rootProject.name = "voice-dictation-compose"

pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        google()
    }
    plugins {
        val kotlinVersion = "2.4.10"
        val composeVersion = "1.11.1"

        kotlin("multiplatform").version(kotlinVersion)
        kotlin("jvm").version(kotlinVersion)
        id("org.jetbrains.kotlin.plugin.compose").version(kotlinVersion)
        id("org.jetbrains.kotlin.plugin.serialization").version(kotlinVersion)
        id("org.jetbrains.compose").version(composeVersion)
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
    }
}

include(":composeApp")
