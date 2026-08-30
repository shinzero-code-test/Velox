pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Velox"

// :app — application entry, DI wiring, navigation host
include(":app")

// :core:* — shared foundation, no feature-specific logic
include(":core:common")
include(":core:ui")
include(":core:domain")
include(":core:data")
include(":core:network")
include(":core:audio-analysis")

// :feature:* — one module per user-facing feature area
include(":feature:library")
include(":feature:player")
include(":feature:playlists")
include(":feature:settings")
include(":feature:equalizer")
include(":feature:subtitles")
include(":feature:network")

// :player:* — the playback engine itself, kept independent of any single feature
include(":player:engine")
include(":player:service")
