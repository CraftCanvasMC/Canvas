import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import java.nio.file.*
import java.util.*
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import org.gradle.api.DefaultTask

plugins {
    java
    `maven-publish`
    id("io.papermc.paperweight.patcher") version "2.0.0-beta.14"
}

allprojects {
    apply(plugin = "java")
    apply(plugin = "maven-publish")

    java {
        toolchain {
            languageVersion = JavaLanguageVersion.of(22)
        }
    }

    tasks.compileJava {
        options.compilerArgs.add("-Xlint:-deprecation")
        options.isWarnings = false
    }
}

val paperMavenPublicUrl = "https://repo.papermc.io/repository/maven-public/"
val mcVersion = "1.21.4"

subprojects {
    apply(plugin = "java-library")
    apply(plugin = "maven-publish")

    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion = JavaLanguageVersion.of(22)
        }
    }

    tasks.withType<JavaCompile> {
        options.encoding = Charsets.UTF_8.name()
        options.release = 22
        options.isFork = true
    }
    tasks.withType<Javadoc> {
        options.encoding = Charsets.UTF_8.name()
    }
    tasks.withType<ProcessResources> {
        filteringCharset = Charsets.UTF_8.name()
    }
    tasks.withType<Test> {
        testLogging {
            showStackTraces = true
            exceptionFormat = TestExceptionFormat.FULL
            events(TestLogEvent.STANDARD_OUT)
        }
    }

    repositories {
        mavenCentral()
        maven(paperMavenPublicUrl)
        maven("https://jitpack.io")
        maven("https://s01.oss.sonatype.org/content/repositories/snapshots/")
        maven("https://maven.shedaniel.me/")
        maven("https://maven.terraformersmc.com/releases/")
    }

}

repositories {
    mavenCentral()
    jcenter()
    maven(paperMavenPublicUrl)
}

paperweight {
    upstreams.register("purpur") {
        repo = github("PurpurMC", "Purpur")
        ref = providers.gradleProperty("purpurCommit")

        patchFile {
            path = "purpur-server/build.gradle.kts"
            outputFile = file("canvas-server/build.gradle.kts")
            patchFile = file("canvas-server/build.gradle.kts.patch")
        }
        patchFile {
            path = "purpur-api/build.gradle.kts"
            outputFile = file("canvas-api/build.gradle.kts")
            patchFile = file("canvas-api/build.gradle.kts.patch")
        }
        patchRepo("paperApi") {
            upstreamPath = "paper-api"
            patchesDir = file("canvas-api/paper-patches")
            outputDir = file("paper-api")
        }
        patchDir("purpurApi") {
            upstreamPath = "purpur-api"
            excludes = listOf("build.gradle.kts", "build.gradle.kts.patch", "paper-patches")
            patchesDir = file("canvas-api/purpur-patches")
            outputDir = file("purpur-api")
        }
    }
}

tasks.register("buildPublisherJar") {
    dependsOn(":canvas-server:createMojmapPaperclipJar")

    doLast {
        val buildNumber = System.getenv("BUILD_NUMBER") ?: "local"

        val paperclipJarTask = project(":canvas-server").tasks.getByName("createMojmapPaperclipJar")
        val outputJar = paperclipJarTask.outputs.files.singleFile
        val outputDir = outputJar.parentFile

        if (outputJar.exists()) {
            val newJarName = "canvas-build$$buildNumber.jar"
            val newJarFile = File(outputDir, newJarName)

            outputDir.listFiles()
                ?.filter { it.name.startsWith("canvas-build$") && it.name.endsWith(".jar") }
                ?.forEach { it.delete() }
            outputJar.renameTo(newJarFile)
            println("Renamed ${outputJar.name} to $newJarName in ${outputDir.absolutePath}")
        }
    }
}
