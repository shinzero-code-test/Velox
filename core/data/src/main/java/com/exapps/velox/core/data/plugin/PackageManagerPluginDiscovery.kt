package com.exapps.velox.core.data.plugin

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.exapps.velox.core.domain.plugin.MediaSourceProvider
import com.exapps.velox.core.domain.plugin.PluginDiscovery
import dagger.hilt.android.qualifiers.ApplicationContext
import dalvik.system.PathClassLoader
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 3 / Wave 3 / Round 3.5e — APK-form plugin discovery.
 *
 * The host's `AndroidManifest.xml` declares a signature-level
 * permission (`com.exapps.velox.permission.PLUGIN_HOST`) and a
 * `<queries>` element for the
 * `com.exapps.velox.MEDIA_SOURCE_PROVIDER` action. A third-party
 * plugin APK that wants to be loaded:
 *
 *  1. Holds the `com.exapps.velox.permission.PLUGIN_HOST`
 *     permission in its own manifest.
 *  2. Declares a `<service>` (or `<receiver>`) with the
 *     `com.exapps.velox.MEDIA_SOURCE_PROVIDER` action and a
 *     `<meta-data android:name="…PROVIDER" android:value="fully.qualified.ClassName" />`
 *     pointing at a class that implements
 *     [com.exapps.velox.core.domain.plugin.MediaSourceProvider].
 *
 * The host walks the package manager, filters by the permission
 * (same-signature gate), reads the class name from the meta-data,
 * and instantiates the class via a [PathClassLoader] rooted at
 * the plugin APK's nativeLibraryDir/codeCacheDir. The classloader
 * bridge is the same one Android uses for any installed APK, so
 * the plugin's transitive deps resolve naturally.
 *
 * Plugins run in the **host process**. There is no plugin
 * sandbox in this MVP — the security model is the same-signature
 * permission. A future round can add a per-plugin process via
 * the `android:process` attribute + a remote binder; that's out
 * of scope for v1.9.0.
 */
@Singleton
class PackageManagerPluginDiscovery @Inject constructor(
    @ApplicationContext private val context: Context,
) : PluginDiscovery {

    override suspend fun discover(): List<MediaSourceProvider> {
        val intent = android.content.Intent(ACTION_MEDIA_SOURCE_PROVIDER)
        // PackageManager.GET_META_DATA so the meta-data Bundle
        // for the service is available to loadClass.
        val flags = PackageManager.GET_META_DATA or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) PackageManager.GET_META_DATA else 0)

        // queryIntentServices, not queryIntentActivities: the
        // plugin contract is a service with the
        // MEDIA_SOURCE_PROVIDER action. (Activities would also
        // match the intent filter, but they're not the right
        // shape for a provider.)
        val matches = runCatching {
            context.packageManager.queryIntentServices(intent, flags)
        }.getOrNull() ?: return emptyList()

        val results = mutableListOf<MediaSourceProvider>()
        for (resolveInfo in matches) {
            val packageName = resolveInfo.serviceInfo.packageName
            // Skip the host itself — the host is also a service
            // candidate (any provider in our own APK is bound
            // through Hilt, not through this loader).
            if (packageName == context.packageName) continue
            // Signature-level permission gate.
            if (!hasPluginHostPermission(packageName)) continue
            val className = readProviderClassName(resolveInfo) ?: continue
            val provider = instantiate(className, packageName) ?: continue
            results += provider
        }
        return results
    }

    /**
     * Returns true when [packageName] holds the
     * `com.exapps.velox.permission.PLUGIN_HOST` signature
     * permission. We do the same check the framework would do
     * before honouring the permission: compare the APK's
     * signature(s) against the host's. A
     * `checkSignatures` result of `SIGNATURE_MATCH` is required.
     */
    private fun hasPluginHostPermission(packageName: String): Boolean {
        val pm = context.packageManager
        val matches = runCatching {
            pm.checkSignatures(pm.packageName, packageName)
        }.getOrNull() ?: return false
        // SIGNATURE_MATCH (2) and SIGNATURE_FIRST_SAME_SIGNER (1)
        // both pass. SIGNATURE_MATCH is the strictest ("same
        // signing key"); SIGNATURE_FIRST_SAME_SIGNER is the
        // framework's "first installer's cert matches". We
        // accept either as "the same developer signed both".
        if (matches != PackageManager.SIGNATURE_MATCH &&
            matches != PackageManager.SIGNATURE_FIRST_SAME_SIGNER
        ) return false
        // The permission gate: the plugin must *request* the
        // permission in its own manifest. checkPermission returns
        // PERMISSION_GRANTED when the manifest declares it.
        return runCatching {
            pm.checkPermission(PLUGIN_HOST_PERMISSION, packageName) ==
                PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
    }

    /**
     * Reads the `<meta-data>` Bundle off the service's
     * [android.content.pm.ServiceInfo]. The plugin's manifest
     * entry should be:
     *
     *   <meta-data
     *       android:name="com.exapps.velox.MEDIA_SOURCE_PROVIDER"
     *       android:value="fully.qualified.ClassName" />
     */
    private fun readProviderClassName(
        resolveInfo: android.content.pm.ResolveInfo,
    ): String? {
        val metaData = resolveInfo.serviceInfo.metaData ?: return null
        val className = metaData.getString(META_KEY) ?: return null
        return className.takeIf { it.isNotBlank() }
    }

    /**
     * Loads the provider class via a [PathClassLoader] rooted at
     * the plugin APK's native library path. The plugin's
     * dependencies (any androidx libs the plugin links to) are
     * resolved through the host's classloader; classes the plugin
     * defines itself come from the APK's own code path.
     */
    private fun instantiate(
        className: String,
        packageName: String,
    ): MediaSourceProvider? = runCatching {
        val apkPath = context.packageManager.getApplicationInfo(
            packageName,
            0,
        ).sourceDir
        // A PathClassLoader whose parent is the host's classloader
        // resolves `androidx.*`, `kotlin.*`, etc. through the
        // host, while the plugin's own classes resolve through
        // the APK's dex files.
        val classLoader = PathClassLoader(apkPath, context.classLoader)
        val cls = Class.forName(className, true, classLoader)
        // The plugin class must have a no-arg constructor and
        // implement MediaSourceProvider. The reflective
        // instantiation gives the plugin a chance to do its own
        // initialisation in the constructor (matching the host's
        // first-party Hilt-bound providers, which all do their
        // work in the constructor).
        val instance = cls.getDeclaredConstructor().newInstance()
        instance as? MediaSourceProvider
    }.getOrNull()

    companion object {
        /** The intent action a plugin APK advertises via its service. */
        const val ACTION_MEDIA_SOURCE_PROVIDER = "com.exapps.velox.MEDIA_SOURCE_PROVIDER"

        /** The meta-data key the plugin uses to point at its provider class. */
        const val META_KEY = "com.exapps.velox.MEDIA_SOURCE_PROVIDER"

        /** Signature permission defined in the host's manifest. */
        const val PLUGIN_HOST_PERMISSION = "com.exapps.velox.permission.PLUGIN_HOST"
    }
}
