package com.exapps.velox.core.network.net

/**
 * Network URL utilities shared by the SMB / FTP / WebDAV clients.
 *
 * data-layer (review): callers used to pass URLs that didn't end with
 * a trailing slash for a directory path; jcifs-ng's SmbFile.listFiles()
 * returns empty in that case. Normalising here keeps the call sites
 * forgiving.
 */
internal fun ensureTrailingSlash(url: String): String =
    if (url.endsWith("/")) url else "$url/"
