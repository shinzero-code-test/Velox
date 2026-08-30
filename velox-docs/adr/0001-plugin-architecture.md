# ADR 0001 — Plugin architecture

**Status:** accepted (v1.9.0)
**Date:** 2026-08-30
**Deciders:** Velox core team

## Context

The Phase 3 plan calls for a plugin architecture that lets
third-party APKs add new media sources (SMBv2, NFS, S3, podcast
feeds, IPTV M3Us) without modifying the host. The first surface
ships in v1.5.0 (the `MediaSourceProvider` SPI), the registry
adapter in v1.5.0, the first-party providers (HTTP, SMB, FTP,
WebDAV) in v1.8.0, and the APK-form discovery foundation in
v1.9.0 (this ADR).

## Decision

A third-party plugin APK is identified by the **same signing
key** as the host, declares a **signature-level permission**
on the host, and exposes its provider via a **Service** with the
`com.exapps.velox.MEDIA_SOURCE_PROVIDER` action. The host
discovers installed plugins at runtime via `PackageManager`
and instantiates them with a `PathClassLoader` rooted at the
plugin APK's source dir.

### Plugin APK manifest

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.example.myplugin">

    <uses-permission
        android:name="com.exapps.velox.permission.PLUGIN_HOST" />

    <application>
        <service
            android:name=".MyPluginProvider"
            android:exported="true">
            <intent-filter>
                <action android:name="com.exapps.velox.MEDIA_SOURCE_PROVIDER" />
            </intent-filter>
            <meta-data
                android:name="com.exapps.velox.MEDIA_SOURCE_PROVIDER"
                android:value="com.example.myplugin.MyPluginProvider" />
        </service>
    </application>
</manifest>
```

### Plugin Kotlin class

```kotlin
class MyPluginProvider @Inject constructor() : MediaSourceProvider {
    override val id = "myplugin"
    override val displayName = LocalizedPluginName("My Plugin")
    override val supportedProtocols = listOf("myproto")
    override suspend fun listDirectory(url: String) = ...
    override suspend fun openStream(url: String, offset: Long?) = ...
}
```

The class has a no-arg constructor and implements
`MediaSourceProvider`. The host instantiates via
`Class.getDeclaredConstructor().newInstance()`.

### Security model

- **Signature-level permission** (`protectionLevel="signature"`):
  only APKs signed with the host's key can hold the permission
  and therefore be discovered.
- **`checkSignatures`**: the host verifies the plugin APK's
  signing certificate against its own before loading. The walk
  accepts `SIGNATURE_MATCH` and `SIGNATURE_FIRST_SAME_SIGNER`
  (the framework's same-developer tier).
- **No plugin sandbox in v1.9.0**: plugins run in the host
  process. A future round can add a per-plugin process via
  `android:process` + a remote binder; that is out of scope here.

### Why this shape

- The signature permission is the simplest gate that prevents
  random third-party APKs from being loaded. Same-signature
  APKs are by definition the same developer's; loading them is
  a deliberate side-load, not an unverified extension.
- A `<service>` with an `<intent-filter>` is the standard
  Android way to advertise background capabilities. The host
  queries it via `queryIntentServices` and the metadata carries
  the class name to instantiate.
- `PathClassLoader` is the framework's native classloader for
  installed APKs. The plugin's dependencies resolve through the
  host's classloader (so the plugin can use any androidx lib the
  host already links to); the plugin's own classes resolve
  through the APK's dex files.

## Consequences

- Plugins are **first-party-grade trusted** by signing key.
  This is appropriate for a media player where the threat
  model is "developer mistakes", not "user downloads a malware
  APK with the right permission name". A future round can add
  a per-plugin permission grant flow.
- The first-party providers (HTTP, SMB, FTP, WebDAV) ship
  in the host APK and are bound through Hilt multibinding.
  They do **not** need to hold the plugin permission.
- A v1.9.0 build of Velox that has never had a third-party
  plugin installed shows the same four first-party providers in
  Settings → About → Plugins, with a small "third-party
  plugins" footer explaining the contract. The footer is the
  developer-facing documentation; end users never see it.
- Adding `android:exported="true"` to the plugin's service is
  required for `queryIntentServices` to return it on Android
  12+. The `permission` attribute on the service is **not** set
  — the host doesn't gate its own intent queries by the
  permission because it already filters by the same-signature
  check during `discover()`.

## References

- Phase 3 plan §6.1 (`MediaSourceProvider` SPI)
- Phase 3 plan §6.2 (APK-form discovery contract)
- `core/domain/.../plugin/PluginDiscovery.kt` (the port)
- `core/data/.../plugin/PackageManagerPluginDiscovery.kt` (the impl)
- `app/src/main/AndroidManifest.xml` (the host's signature
  permission + `<queries>` element)
