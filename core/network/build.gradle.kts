plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.exapps.velox.core.network"
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
    implementation(project(":core:common"))
    // Phase 3 / Wave 3 / Round 3.5b — the network clients are
    // adapted as `MediaSourceProvider`s (the plugin SPI). The
    // port lives in :core:domain; this module depends on it.
    implementation(project(":core:domain"))

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)

    // Phase 2 network protocols. `implementation` (not `api`): consumers reference
    // only core:network's own types — leaking the stacks to every feature module
    // was flagged by the data-layer review.
    implementation(libs.jcifs.ng)
    implementation(libs.commons.net)
    implementation(libs.okhttp)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
