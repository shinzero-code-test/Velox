plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.room)
}

android {
    namespace = "com.exapps.velox.core.data"
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
        // M14: BuildConfig.DEBUG gates the destructive-migration escape hatch.
        buildConfig = true
    }
}

room {
    // Exported schemas live here for migration testing; commit this folder.
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:domain"))
    implementation(project(":core:network"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    // Phase 3 / Milestone 4 — Plugin architecture. The first-party
    // `HttpUrlProvider` wraps OkHttp so the registry has a real
    // provider exercising the SPI in the MVP. Round 1.5 swaps
    // this for a non-trivial plugin (IPTV M3U parser, etc.).
    implementation(libs.okhttp)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    // H4 (data-layer review): MigrationTestHelper for asserting that the 1→2
    // and 2→3 migrations preserve the user-visible shape (favourites, play
    // stats, tag-editor overrides, bookmarks) on every schema bump.
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.room.testing)
}
