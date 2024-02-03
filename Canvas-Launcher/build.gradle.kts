/*
 * The Launcher source is a modified version of the Ignite mixin launcher for Paper/Spigot
 */
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
  id("ignite.launcher-conventions")
  id("ignite.publish-conventions")
  id("com.github.johnrengelman.shadow") version "7.1.2" // Canvas
}

dependencies {
  api(libs.tinylog.api)

  implementation(libs.tinylog.impl)

  implementation(libs.mixin) {
    exclude(group = "com.google.guava")
    exclude(group = "com.google.code.gson")
    exclude(group = "org.ow2.asm")
  }

  implementation(libs.mixinExtras) {
    exclude(group = "org.apache.commons")
  }

  implementation(libs.accessWidener)
  implementation(libs.asm)
  implementation(libs.asm.analysis)
  implementation(libs.asm.commons)
  implementation(libs.asm.tree)
  implementation(libs.asm.util)

  implementation(libs.gson)
}
// Canvas start

tasks.getByName<ShadowJar>("shadowJar") {
  mergeServiceFiles()

  relocate("com.google.gson", "space.vectrix.ignite.libs.gson")
}

tasks.register("dist") {
  dependsOn("shadowJar")

  doLast {
    val sourceDir = project.layout.buildDirectory.asFile.map { it.resolve("libs") }.get()
    val targetDir = rootProject.layout.buildDirectory.asFile.map { it.resolve("libs") }.get()

    targetDir.mkdirs()

    rootProject.copy {
      from(sourceDir) {
        include("*-all.jar")
      }

      into(targetDir)

      rename { "canvas-launcher.jar" }
    }
  }
}
tasks.getByName("processResources").dependsOn(rootProject.tasks.getByName("createCanvasBundler"))
// Canvas end

// Project metadata is configured in gradle.properties
