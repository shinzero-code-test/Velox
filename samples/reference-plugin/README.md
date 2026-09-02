# Velox Reference Plugin (sample)

This is a minimal APK-form plugin that demonstrates the `com.exapps.velox.MEDIA_SOURCE_PROVIDER`
contract (ADR 0001). It lives outside the host repo so the host does not depend on it at
compile-time; install the built APK alongside Velox on the same device (same signing key)
and Velox's `PackageManagerPluginDiscovery` will load it at runtime via `PathClassLoader`.

## What it does
- Implements `MediaSourceProvider` with id `velox-ref-sample`
- Advertises `myproto://` URLs (replace with your own scheme)
- `listDirectory` returns a synthetic 2-entry listing
- `openStream` returns an empty `ByteArrayInputStream` (replace with real IO)

## Build
This sample is **not** included in Velox's `settings.gradle.kts` — build it standalone:

```bash
cd samples/reference-plugin
gradle assembleDebug
adb install app/build/outputs/apk/debug/reference-plugin-debug.apk
```

The host and plugin **must** be signed with the same keystore for the
`com.exapps.velox.permission.PLUGIN_HOST` signature permission to grant.

## Manifest contract (see also velox-docs/adr/0001-plugin-architecture.md)
```xml
<manifest package="com.example.veloxrefplugin">
    <uses-permission android:name="com.exapps.velox.permission.PLUGIN_HOST"/>
    <application>
        <service android:name=".ExamplePluginProvider" android:exported="true">
            <intent-filter>
                <action android:name="com.exapps.velox.MEDIA_SOURCE_PROVIDER"/>
            </intent-filter>
            <meta-data
                android:name="com.exapps.velox.MEDIA_SOURCE_PROVIDER"
                android:value="com.example.veloxrefplugin.ExamplePluginProvider"/>
        </service>
    </application>
</manifest>
```
The class must have a public no-arg constructor and implement `MediaSourceProvider`.

## Process isolation (future)
v1.9.x loads plugins in the host process (`PathClassLoader`). True isolation via
`android:process=":plugin_myproto"` + Binder is tracked as a future round; this
sample declares no `android:process` and runs in the host process by design.
