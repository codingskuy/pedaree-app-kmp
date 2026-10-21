pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "pedaree-app-kmp"

// Logika domain, kontrak API, presentasi (state holder) — teruji di JVM tanpa emulator.
include(":shared")
// UI Compose Multiplatform + Navigation3 + entri Android/iOS.
include(":composeApp")
