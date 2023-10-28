import gradle.kotlin.dsl.accessors._b0433329c9643660552d03c353284877.api
import gradle.kotlin.dsl.accessors._b0433329c9643660552d03c353284877.compileOnlyApi
import gradle.kotlin.dsl.accessors._b0433329c9643660552d03c353284877.implementation
import org.gradle.kotlin.dsl.maven
import org.gradle.kotlin.dsl.repositories

plugins {
    `java-library`
    `maven-publish`
    id("ignite.launcher-conventions")
    id("ignite.api-conventions")
}

repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

dependencies {
    compileOnlyApi("org.checkerframework:checker-qual:3.30.0")

    // Logging

    implementation("net.minecrell:terminalconsoleappender:1.3.0")
    implementation("org.apache.logging.log4j:log4j-core:2.19.0")
    implementation("org.jline:jline-terminal:3.22.0")
    implementation("org.jline:jline-reader:3.22.0")
    implementation("org.jline:jline-terminal-jansi:3.22.0")
    api("org.apache.logging.log4j:log4j-api:2.19.0")

    // Configuration

    implementation("org.spongepowered:configurate-hocon:4.2.0-SNAPSHOT")
    implementation("org.spongepowered:configurate-yaml:4.2.0-SNAPSHOT")
    implementation("org.spongepowered:configurate-gson:4.2.0-SNAPSHOT")
    api("org.spongepowered:configurate-core:4.2.0-SNAPSHOT")

    api("com.google.inject:guice:5.1.0") {
        exclude(group = "com.google.code.findbugs", module = "jsr305")
        exclude(group = "com.google.guava", module = "guava")
    }

    // Common

    implementation("com.google.guava:guava:31.1-jre") { // 21.0 -> 22.0
        exclude(group = "com.google.code.findbugs", module = "jsr305")
    }

    implementation("com.google.errorprone:error_prone_annotations:2.18.0")

    // Event

    implementation("net.kyori:event-api:4.0.0-SNAPSHOT") {
        exclude(group = "com.google.code.findbugs", module = "jsr305")
        exclude(group = "org.checkerframework", module = "checker-qual")
    }

    implementation("net.kyori:event-method-asm:4.0.0-SNAPSHOT") {
        exclude(group = "com.google.code.findbugs", module = "jsr305")
        exclude(group = "org.checkerframework", module = "checker-qual")
    }

    // Transformation

    implementation("net.fabricmc:access-widener:2.1.0")
    implementation("net.fabricmc:sponge-mixin:0.12.4+mixin.0.8.5") {
        exclude(group = "org.ow2.asm")
    }
    api("org.ow2.asm:asm:9.2")
    api("org.ow2.asm:asm-analysis:9.2")
    api("org.ow2.asm:asm-commons:9.2")
    api("org.ow2.asm:asm-tree:9.2")
    api("org.ow2.asm:asm-util:9.2")

    // Launcher

    implementation("cpw.mods:modlauncher:8.1.3") {
        exclude(group = "com.google.code.findbugs", module = "jsr305")
    }

    implementation("cpw.mods:modlauncher:8.1.3:api") {
        exclude(group = "com.google.code.findbugs", module = "jsr305")
    }

    implementation("cpw.mods:grossjava9hacks:1.3.3")

    // Minecraft

    api("com.google.code.gson:gson:2.10.1")
}
