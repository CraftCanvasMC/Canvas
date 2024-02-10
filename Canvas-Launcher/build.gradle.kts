/*
 * The Launcher source is a modified version of the Ignite mixin launcher for Paper/Spigot
 */
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    `java-library`
    id("checkstyle")
    `maven-publish`
    signing
  id("com.github.johnrengelman.shadow") version "7.1.2" // Canvas
}

dependencies {
  api("org.tinylog:tinylog-api:2.6.2")

  implementation("org.tinylog:tinylog-impl:2.6.2")

  implementation("net.fabricmc:sponge-mixin:0.12.5+mixin.0.8.5") {
    exclude(group = "com.google.guava")
    exclude(group = "com.google.code.gson")
    exclude(group = "org.ow2.asm")
  }

  implementation("io.github.llamalad7:mixinextras-common:0.3.2") {
    exclude(group = "org.apache.commons")
  }

  implementation("net.fabricmc:access-widener:2.1.0")
  implementation("org.ow2.asm:asm:9.6")
  implementation("org.ow2.asm:asm-analysis:9.6")
  implementation("org.ow2.asm:asm-commons:9.6")
  implementation("org.ow2.asm:asm-tree:9.6")
  implementation("org.ow2.asm:asm-util:9.6")

  implementation("com.google.code.gson:gson:2.10.1")
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

val libs = extensions.getByType(org.gradle.accessors.dm.LibrariesForLibs::class)

group = rootProject.group
version = rootProject.version

java {
    withSourcesJar()
}

dependencies {
    compileOnly(libs.jetbrains.annotations)
}

tasks {
    javadoc {
        val minimalOptions: MinimalJavadocOptions = options
        options.encoding("UTF-8")

        if (minimalOptions is StandardJavadocDocletOptions) {
            val options: StandardJavadocDocletOptions = minimalOptions
            options.addStringOption("Xdoclint:none", "-quiet")
        }
    }

    compileJava {
        options.encoding = "UTF-8"
        options.compilerArgs.addAll(listOf(
            "-nowarn",
            "-Xlint:-unchecked",
            "-Xlint:-deprecation"
        ))
    }
}

signing {
    if(project.hasProperty("signingKey") && project.hasProperty("signingPassword")) {
        useInMemoryPgpKeys(
            project.property("signingKey").toString(),
            project.property("signingPassword").toString()
        )
    }
}

tasks.withType(Sign::class) {
    onlyIf { project.hasProperty("signingKey") && project.hasProperty("signingPassword") }
}

val implementationVersion = project.version.toString()
val regexPattern = """(\d+\.\d+)""".toRegex()
val apiVersion = regexPattern.find(implementationVersion)?.value

tasks.getByName<Jar>("jar") {
    manifest {
        attributes(
            "Premain-Class" to "space.vectrix.ignite.agent.IgniteAgent",
            "Agent-Class" to "space.vectrix.ignite.agent.IgniteAgent",
            "Launcher-Agent-Class" to "space.vectrix.ignite.agent.IgniteAgent",
            "Main-Class" to "space.vectrix.ignite.IgniteBootstrap",
            "Multi-Release" to true,

            "Specification-Title" to "ignite",
            "Specification-Version" to apiVersion,
            "Specification-Vendor" to "vectrix.space",

            "Implementation-Title" to project.name,
            "Implementation-Version" to implementationVersion,
            "Implementation-Vendor" to "vectrix.space"
        )

        attributes(
            "org/objectweb/asm/",
            "Implementation-Version" to "9.6"
        )
    }
}

tasks.getByName("build") {
    dependsOn("dist")
}
// Canvas end

// Project metadata is configured in gradle.properties
