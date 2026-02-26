dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven {
            url = uri("https://nexus.bytro.com/repository/maven-public/")
            credentials {
                val bytroNexusUser: String? by settings
                val bytroNexusPass: String? by settings
                username = bytroNexusUser
                password = bytroNexusPass
            }
        }
    }
}

rootProject.name = "nimrod"
