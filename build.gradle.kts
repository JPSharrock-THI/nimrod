plugins {
    java
    id("org.springframework.boot") version "3.4.3"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.graalvm.buildtools.native") version "0.10.4"
}

group = "com.nimrod"
version = "0.1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    // Spring Boot (no web)
    implementation("org.springframework.boot:spring-boot-starter")

    // CLI
    implementation("info.picocli:picocli-spring-boot-starter:4.7.6")

    // CSV
    implementation("org.apache.commons:commons-csv:1.12.0")

    // FlatBuffers
    implementation("com.google.flatbuffers:flatbuffers-java:24.12.23")

    // FlatBuffers DB schemas (generated Java classes for decoding game state)
    // Published from the sup-server-db-fbs-schema project to Bytro Nexus
    implementation("com.bytro.sup:sup-server-db-fbs-schema:0.2.17")

    // Jackson (JSON output)
    implementation("com.fasterxml.jackson.core:jackson-databind")

    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// The GraalVM collectReachabilityMetadata and Spring AOT both produce files at
// META-INF/native-image/com.nimrod/nimrod/ — keep the first copy encountered.
tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

springBoot {
    mainClass = "com.nimrod.NimrodApplication"
}

graalvmNative {
    binaries {
        named("main") {
            imageName = "nimrod"
            mainClass = "com.nimrod.NimrodApplication"

            buildArgs.addAll(
                "--no-fallback",
                "-H:+ReportExceptionStackTraces"
            )
        }
    }

    toolchainDetection = false
}
