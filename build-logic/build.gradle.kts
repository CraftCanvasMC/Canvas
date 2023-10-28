plugins {
  `kotlin-dsl`
}

repositories {
  gradlePluginPortal()
}

dependencies {
  implementation("net.kyori", "indra-common", "2.2.0")
  implementation("gradle.plugin.com.github.johnrengelman", "shadow", "7.1.2")
  implementation("de.marcphilipp.gradle", "nexus-publish-plugin", "0.4.0")
}
