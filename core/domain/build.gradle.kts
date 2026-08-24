// :core:domain is intentionally a pure Kotlin/JVM module, not an Android library.
// Domain models, repository interfaces, and use cases must not depend on the
// Android framework — that's what keeps them trivially unit-testable and keeps
// the dependency arrows pointing the right way (data/feature depend on domain,
// never the reverse).
plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

dependencies {
    // coroutines-core only — the -android artifact pulls in the Main dispatcher's
    // Android dependency, which has no place in a framework-agnostic domain module.
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
}
