pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        maven("https://repo1.maven.org/maven2")
        maven("https://maven-central.storage-download.googleapis.com/maven2")
        gradlePluginPortal()
        maven("https://repo.itextsupport.com/android")
        maven("https://chaquo.com/maven")
    }
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "io.objectbox") {
                useModule("io.objectbox:objectbox-gradle-plugin:${requested.version}")
            }
        }
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        maven("https://repo1.maven.org/maven2")
        maven("https://maven-central.storage-download.googleapis.com/maven2")
        maven("https://jitpack.io")
        mavenLocal()
        maven("https://chaquo.com/maven")
    }
}

rootProject.name = "rikkahub"
include(":app")
include(":highlight")
include(":ai")
include(":search")
include(":tts")
include(":common")
include(":document")
