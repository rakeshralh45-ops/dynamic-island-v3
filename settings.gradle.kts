pluginManagement {
    repositories {
        google()
        // Google-hosted Maven Central mirror reduces direct Maven Central rate-limit failures in CI.
        maven { url = uri("https://maven-central-eu.storage-download.googleapis.com/maven2/") }
        gradlePluginPortal()
        mavenCentral()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        // Prefer the mirror before direct Maven Central.
        maven { url = uri("https://maven-central-eu.storage-download.googleapis.com/maven2/") }
        mavenCentral()
    }
}
rootProject.name = "DynamicIsland"
include(":app")
