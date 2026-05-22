plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(gradleApi())
    implementation("xyz.jpenilla.resource-factory-paper-convention:xyz.jpenilla.resource-factory-paper-convention.gradle.plugin:1.3.1")
}
