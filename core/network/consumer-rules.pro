# jcifs-ng resolves configuration and SMB internals reflectively; keep the core
# types so R8 stripping in the consuming app doesn't break SMB playback.
-keep class jcifs.** { *; }
-dontwarn jcifs.**

# OkHttp/Conscrypt warnings surfaced through jcifs-ng's optional dependencies.
-dontwarn org.slf4j.**
-dontwarn com.sun.jna.**
