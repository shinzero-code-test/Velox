plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.exapps.velox.player.engine"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:domain"))
    // Phase 3 / L6 (deferred-backlog): the engine no longer imports
    // `:core:data` directly. EQ and decoder preferences flow through the
    // domain ports in `:core:domain` (see DecoderPreferenceStore and
    // EqualizerPreferencesStore), with adapters bound by Hilt in
    // `:core:data`/DataModule.kt.
    implementation(project(":core:network"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.androidx.media3.exoplayer)
    // Phase 2 "Network streams": HLS / DASH / RTSP support via Media3 modules —
    // DefaultMediaSourceFactory picks them up from the classpath automatically.
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.exoplayer.dash)
    implementation(libs.androidx.media3.exoplayer.rtsp)
    implementation(libs.androidx.media3.common)
    implementation(libs.androidx.media3.datasource)
    // VeloxVideoSurface — Compose wrapper over PlayerView (video + subtitle rendering)
    implementation(libs.androidx.media3.ui)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    androidTestImplementation(libs.androidx.junit)
}
