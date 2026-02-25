pluginManagement {
    repositories {
        maven {
            url = uri("https://nexus.bytro.com/repository/maven-public/")
            credentials {
                val bytroNexusUser: String? by settings
                val bytroNexusPass: String? by settings
                if (bytroNexusUser == null || bytroNexusPass == null) {
                    throw InvalidUserDataException(
                        """
You need to provide `bytroNexusUser` (your github username) and `bytroNexusPass` (an authtoken with read:org) via gradle.properties
see https://bytrolabs.atlassian.net/wiki/spaces/BT/pages/3263823873#Gradle-Integration
and https://github.com/settings/tokens/new"""
                    )
                }
                username = bytroNexusUser
                password = bytroNexusPass
            }
        }
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}

dependencyResolutionManagement {
    repositories {
        maven {
            url = uri("https://nexus.bytro.com/repository/maven-public/")
            credentials {
                val bytroNexusUser: String by settings
                val bytroNexusPass: String by settings
                username = bytroNexusUser
                password = bytroNexusPass
            }
        }
    }
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
}

rootProject.name = "nimrod"
