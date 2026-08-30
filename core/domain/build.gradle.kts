// :core:domain is intentionally a pure Kotlin/JVM module, not an Android library.
// Domain models, repository interfaces, and use cases must not depend on the
// Android framework — that's what keeps them trivially unit-testable and keeps
// the dependency arrows pointing the right way (data/feature depend on domain,
// never the reverse).
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
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

    // @Inject annotations (JSR-330) for constructor injection wiring via Hilt,
    // without dragging Hilt itself into the domain layer.
    implementation(libs.javax.inject)

    // Phase 3 / Milestone 2: theme manifests are serialised JSON; the
    // domain layer owns the schema so :core:data and :core:ui can both
    // parse the same shape without one depending on the other.
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
}
