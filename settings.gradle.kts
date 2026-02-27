pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

// Auto-downloads missing JDK toolchains (e.g. GraalVM 21 for nativeCompile).
// Remove if you manage JDKs manually via SDKMAN/brew.
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}

dependencyResolutionManagement {
    repositories {
        mavenLocal()
        mavenCentral()
        maven {
            url = uri("https://nexus.bytro.com/repository/maven-public/")
            credentials {
                val bytroNexusUser: String? by settings
                val bytroNexusPass: String? by settings
                username = bytroNexusUser ?: ""
                password = bytroNexusPass ?: ""
            }
        }
    }
}

rootProject.name = "nimrod"
