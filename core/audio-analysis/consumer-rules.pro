# Add project specific ProGuard rules here.
# Keep audio-analysis types so reflection-based Hilt bindings survive R8.
-keep class com.exapps.velox.core.audioanalysis.** { *; }
