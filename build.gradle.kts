// SPDX-License-Identifier: Apache-2.0
// =========================================================================
// AegisGate Rampart - JetBrains IDE Plugin Build Configuration
// =========================================================================

import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.24"
    id("org.jetbrains.intellij") version "1.17.3"
}

group = "io.aegisgate"
version = "0.1.0"

repositories {
    mavenCentral()
}

// IntelliJ Platform Plugin configuration
intellij {
    version.set("2023.2")          // Minimum compatible IDE version
    type.set("IC")                  // IntelliJ IDEA Community Edition
    plugins.set(listOf())           // No plugin dependencies
}

tasks {
    // Kotlin compilation settings
    withType<KotlinCompile> {
        kotlinOptions {
            jvmTarget = "17"
            freeCompilerArgs = listOf("-Xjvm-default=all")
        }
    }

    // Java compilation settings
    withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"
    }

    // Build the plugin .zip
    buildSearchableOptions = false   // No settings to index

    patchPluginXml {
        sinceBuild.set("232")        // IDE build 2023.2+
        untilBuild.set("242.*")      // Compatible through 2024.2.x
    }

    signPlugin {
        certificateChain.set(System.getenv("CERTIFICATE_CHAIN"))
        privateKey.set(System.getenv("PRIVATE_KEY"))
        password.set(System.getenv("PRIVATE_KEY_PASSWORD"))
    }

    publishPlugin {
        token.set(System.getenv("PUBLISH_TOKEN"))
    }
}

dependencies {
    testImplementation(kotlin("test"))
    // No runtime dependencies — zero external comms, zero external libs
    // The plugin talks only to localhost via java.net.http.HttpClient
}

tasks.test {
    useJUnitPlatform()
}