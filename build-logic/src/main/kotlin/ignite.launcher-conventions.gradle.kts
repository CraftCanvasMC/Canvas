import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.github.jengelman.gradle.plugins.shadow.transformers.Log4j2PluginsCacheFileTransformer
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

plugins {
  id("ignite.base-conventions")
  id("com.github.johnrengelman.shadow")
}

repositories {
  maven {
    url = uri("https://repo.spongepowered.org/maven/")
  }
  maven {
    url = uri("https://files.minecraftforge.net/maven/")
  }
  maven {
    url = uri("https://maven.fabricmc.net/")
  }
}

val launcherJava9 by sourceSets.register("java9") {
  val main: SourceSet = sourceSets.main.get()

  this.java.setSrcDirs(setOf("src/java9"))
  compileClasspath += main.compileClasspath
  compileClasspath += main.runtimeClasspath

  tasks.named(compileJavaTaskName, JavaCompile::class) {
    options.release.set(9)
    if (JavaVersion.current() < JavaVersion.VERSION_11) {
      javaCompiler.set(javaToolchains.compilerFor { languageVersion.set(JavaLanguageVersion.of(11)) })
    }
  }

  dependencies.add(implementationConfigurationName, objects.fileCollection().from(main.output.classesDirs))
}

tasks {
  jar {
    manifest {
      attributes(
        "Premain-Class" to "space.vectrix.ignite.applaunch.agent.Agent",
        "Agent-Class" to "space.vectrix.ignite.applaunch.agent.Agent",
        "Launcher-Agent-Class" to "space.vectrix.ignite.applaunch.agent.Agent",
        "Main-Class" to "space.vectrix.ignite.applaunch.IgniteBootstrap",
        "Multi-Release" to true
      )

      attributes("space/vectrix/ignite/applaunch/",
        "Specification-Title" to "ignite",
        "Specification-Vendor" to "vectrix.space",
        "Specification-Version" to 1.0,
        "Implementation-Title" to project.name,
        "Implementation-Version" to rootProject.version.toString(),
        "Implementation-Vendor" to "vectrix.space"
      )

      from({
        zipTree { configurations.runtimeClasspath.get().files.find { entry -> entry.name.contains("modlauncher") } }.matching { include("**/MANIFEST.MF") }.singleFile
      })
    }

    into("META-INF/versions/9/") {
      from(launcherJava9.output)
    }
  }

  register<DefaultTask>("copyToTarget") {
    dependsOn(tasks.withType<ShadowJar>())

    doLast {
      val shadowJar: ShadowJar = getByName<ShadowJar>("shadowJar")
      val targetJarDirectory: Path = projectDir.toPath().toAbsolutePath().resolve("../target")

      Files.createDirectories(targetJarDirectory)
      Files.copy(
        shadowJar.archiveFile.get().asFile.toPath().toAbsolutePath(),
        targetJarDirectory.resolve(shadowJar.archiveBaseName.get() + ".jar"),
        StandardCopyOption.REPLACE_EXISTING
      )
    }
  }

  named<ShadowJar>("shadowJar") {
    mergeServiceFiles()

    transform(Log4j2PluginsCacheFileTransformer())

    from(jar)

    exclude("META-INF/versions/*/module-info.class")
    exclude("module-info.class")
  }
}
