plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.exapps.velox.core.audioanalysis"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    // Pure-Kotlin detection logic. No Compose, no Media3. The
    // player stack (`:player:engine`) consumes this via a
    // Hilt-bound adapter; nothing here depends on Android
    // framework classes that aren't available in unit tests.
    implementation(project(":core:domain"))
    // The analysis service writes through `TrackAnalysisDao`
    // (Room-generated). `:core:data` is the only place that
    // owns the Room database; the DAO is exposed by it.
    implementation(project(":core:data"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
