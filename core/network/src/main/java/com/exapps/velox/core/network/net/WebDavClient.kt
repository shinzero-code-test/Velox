package com.exapps.velox.core.network.net

import com.exapps.velox.core.network.model.NetworkEntry
import com.exapps.velox.core.network.model.NetworkServer
import java.io.InputStream
import java.util.concurrent.TimeUnit
import javax.xml.parsers.DocumentBuilderFactory
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Minimal WebDAV client over OkHttp (Phase 2 "Network browsing"): PROPFIND Depth:1
 * listing with namespace-agnostic XML parsing, and ranged GET for streaming.
 */
class WebDavClient @javax.inject.Inject constructor() : NetworkClient {

    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /** dav(s)://host[:port]/path → https?://host[:port]/path honouring the secure flag. */
    private fun toHttpUrl(url: String, secure: Boolean): String {
        val isSecure = url.startsWith("davs") || secure
        return if (isSecure) url.replaceFirst("davs://", "https://").replaceFirst("dav://", "https://")
        else url.replaceFirst("dav://", "http://")
    }

    private fun authHeader(server: NetworkServer): String? =
        server.username.takeIf { it.isNotBlank() }?.let { Credentials.basic(it, server.password) }

    private fun request(server: NetworkServer, url: String): Request.Builder =
        Request.Builder().url(toHttpUrl(url, server.secure)).apply {
            authHeader(server)?.let { header("Authorization", it) }
        }

    override fun list(server: NetworkServer, url: String): List<NetworkEntry> {
        val body = PROPFIND_BODY.toRequestBody("application/xml".toMediaType())
        val call = request(server, url.trimEnd('/') + "/")
            .header("Depth", "1")
            .method("PROPFIND", body)
            .build()

        http.newCall(call).execute().use { response ->
            if (response.code != 207 && !response.isSuccessful) {
                throw IllegalStateException("WebDAV PROPFIND failed: ${response.code}")
            }
            val xml = response.body?.string().orEmpty()
            return parsePropfind(xml, url)
                .sortedWith(compareByDescending<NetworkEntry> { it.isDirectory }.thenBy { it.name.lowercase() })
        }
    }

    /** Namespace-agnostic DOM walk — servers disagree on the DAV prefix. */
    private fun parsePropfind(xml: String, directoryUrl: String): List<NetworkEntry> {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        }
        val doc = factory.newDocumentBuilder().parse(java.io.ByteArrayInputStream(xml.toByteArray()))

        val entries = mutableListOf<NetworkEntry>()
        val responses = nodeListToList(doc.getElementsByTagName("*"))
            .filter { it.nodeName.substringAfter(':') == "response" }

        for (response in responses) {
            val children = nodeListToList(response.childNodes)

            fun prop(localName: String): org.w3c.dom.Node? {
                val propstat = children.firstOrNull { it.nodeName.substringAfter(':') == "propstat" } ?: return null
                val propNode = nodeListToList(propstat.childNodes)
                    .firstOrNull { it.nodeName.substringAfter(':') == "prop" } ?: return null
                return nodeListToList(propNode.childNodes)
                    .firstOrNull { it.nodeName.substringAfter(':') == localName }
            }

            val href = children.firstOrNull { it.nodeName.substringAfter(':') == "href" }?.textContent ?: continue
            val decodedHref = java.net.URLDecoder.decode(href, "UTF-8")

            val resourceType = prop("resourcetype")
            val isDirectory = resourceType?.let { rt ->
                nodeListToList(rt.childNodes).any { it.nodeName.substringAfter(':') == "collection" }
            } ?: false

            val size = prop("getcontentlength")?.textContent?.toLongOrNull() ?: -1L
            val name = decodedHref.trimEnd('/').substringAfterLast('/')

            entries += NetworkEntry(
                name = name,
                url = absoluteDavUrl(directoryUrl, href),
                isDirectory = isDirectory,
                sizeBytes = size,
            )
        }

        // Depth:1 includes the requested collection itself — drop it.
        return entries.filterNot { it.url.trimEnd('/') == directoryUrl.trimEnd('/') }
    }

    private fun absoluteDavUrl(directoryUrl: String, href: String): String {
        if (href.startsWith("dav:") || href.startsWith("davs:") ||
            href.startsWith("http://") || href.startsWith("https://")
        ) {
            return href.trimEnd('/')
        }
        // Path-only href ("/music/song.mp3") — graft onto our scheme + authority.
        val secure = directoryUrl.startsWith("davs")
        val httpUrl = toHttpUrl(directoryUrl, secure).removeSuffix("/")
        val davScheme = if (secure) "davs" else "dav"
        val authorityAndBase = httpUrl.substringAfter("://")
        val path = if (href.startsWith("/")) href else "/$href"
        return "$davScheme://$authorityAndBase$path"
    }

    override fun openStream(server: NetworkServer, url: String, positionMs: Long): InputStream {
        // Byte-offset heuristic consistent with SMB/FTP; DAV supports true ranges,
        // but ms→bytes mapping without duration metadata is approximate either way.
        val approxBytesPerMs = 16L * 1024L / 8L
        val rangeStart = positionMs * approxBytesPerMs

        val builder = request(server, url)
        if (rangeStart > 0) builder.header("Range", "bytes=$rangeStart-")

        val response = http.newCall(builder.build()).execute()
        if (!response.isSuccessful) {
            val code = response.code
            response.close()
            throw IllegalStateException("WebDAV GET failed: $code")
        }
        val body = checkNotNull(response.body) { "WebDAV GET returned an empty body" }
        return object : InputStream() {
            override fun read(): Int = body.byteStream().read()
            override fun read(b: ByteArray, off: Int, len: Int): Int = body.byteStream().read(b, off, len)
            override fun close() {
                runCatching { body.close() }
                response.close()
            }
        }
    }

    override fun test(server: NetworkServer): Boolean = runCatching {
        list(server, NetworkUrls.root(server))
        true
    }.getOrElse { failure ->
        // Some servers reject Depth:1 on the configured base — try a Depth:0 probe.
        failure is IllegalStateException && probeDepthZero(server)
    }

    private fun probeDepthZero(server: NetworkServer): Boolean {
        val body = PROPFIND_BODY.toRequestBody("application/xml".toMediaType())
        val call = request(server, NetworkUrls.root(server))
            .header("Depth", "0")
            .method("PROPFIND", body)
            .build()
        http.newCall(call).execute().use { return it.code == 207 || it.isSuccessful }
    }

    private companion object {
        val PROPFIND_BODY =
            """<?xml version="1.0"?><d:propfind xmlns:d="DAV:"><d:prop><d:resourcetype/><d:getcontentlength/></d:prop></d:propfind>"""
    }
}

/** NodeList → List helper (org.w3c.dom lists aren't Kotlin iterables). */
private fun nodeListToList(list: org.w3c.dom.NodeList): List<org.w3c.dom.Node> =
    (0 until list.length).map { list.item(it) }
