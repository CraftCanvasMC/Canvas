import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import kotlin.system.measureTimeMillis

plugins {
    java
    `maven-publish`
    id("io.papermc.paperweight.patcher") version "2.0.0-SNAPSHOT"
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

    tasks.withType(JavaCompile::class.java).configureEach {
        options.isFork = true
        options.forkOptions.memoryMaximumSize = "4G"
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

tasks.register<Jar>("createMojmapClipboardJar") {
    dependsOn(":canvas-server:createMojmapPaperclipJar", "clipboard:shadowJar")

    outputs.upToDateWhen { false }

    val paperclipJarTask = project(":canvas-server").tasks.getByName("createMojmapPaperclipJar")
    val clipboardJarTask = project(":clipboard").tasks.getByName("shadowJar")

    val paperclipJar = paperclipJarTask.outputs.files.singleFile
    val clipboardJar = clipboardJarTask.outputs.files.singleFile
    val outputDir = paperclipJar.parentFile
    val tempDir = File(outputDir, "tempJarWork")
    val clipboardExtractDir = File(outputDir, "tempClipboardExtract")
    val newJarName = "canvas-clipboard-${properties.get("version")}-mojmap.jar"

    doFirst {
        val time = measureTimeMillis {
            println("Recompiling paperclip jar with clipboard sources...")
            outputDir.listFiles()
                ?.filter { it.name.startsWith("canvas-build.") && it.name.endsWith(".jar") }
                ?.forEach { it.delete() }

            tempDir.deleteRecursively()
            tempDir.mkdirs()

            clipboardExtractDir.deleteRecursively()
            clipboardExtractDir.mkdirs()

            copy {
                from(zipTree(paperclipJar))
                into(tempDir)
            }

            copy {
                from(zipTree(clipboardJar))
                into(clipboardExtractDir)
            }

            val oldPackagePath = "io/papermc/paperclip/"
            val newPackagePath = "io/canvasmc/clipboard/"

            println("Walking and replacing sources...")
            clipboardExtractDir.walkTopDown()
                .filter { it.isFile && it.extension == "class" && it.relativeTo(clipboardExtractDir).path.startsWith(newPackagePath) }
                .forEach { clipboardClass ->
                    val relativePath = clipboardClass.relativeTo(clipboardExtractDir).path
                    val targetFile = File(tempDir, relativePath)

                    targetFile.parentFile.mkdirs()
                    clipboardClass.copyTo(targetFile, overwrite = true)
                }

            tempDir.walkTopDown()
                .filter { it.isFile && it.relativeTo(tempDir).path.startsWith(oldPackagePath) }
                .forEach { it.delete() }

            tempDir.walkBottomUp()
                .filter { it.isDirectory && it.listFiles().isNullOrEmpty() }
                .forEach { it.delete() }

            val metaInfDir = File(tempDir, "META-INF")
            metaInfDir.mkdirs()
            File(metaInfDir, "main-class").writeText("net.minecraft.server.Main")
        }
        println("Finished recompile in ${time}ms")
    }

    archiveFileName.set(newJarName)
    destinationDirectory.set(outputDir)
    from(tempDir)

    from("path/to/vanilla.versions") {
        into("/")
    }

    manifest {
        attributes(
            // paperclip args
            "Main-Class" to "io.canvasmc.clipboard.Main",
            "Enable-Native-Access" to "ALL-UNNAMED",
            // setup agent
            "Agent-Class" to "io.canvasmc.clipboard.Instrumentation",
            "Premain-Class" to "io.canvasmc.clipboard.Instrumentation",
            "Launcher-Agent-Class" to "io.canvasmc.clipboard.Instrumentation",
            "Can-Redefine-Classes" to true,
            "Can-Retransform-Classes" to true
        )
    }

    doLast {
        tempDir.deleteRecursively()
        clipboardExtractDir.deleteRecursively()
    }
}

tasks.register("buildPublisherJar") {
    dependsOn(":createMojmapClipboardJar")

    doLast {
        val buildNumber = System.getenv("BUILD_NUMBER") ?: "local"

        val paperclipJarTask = tasks.getByName("createMojmapClipboardJar")
        val outputJar = paperclipJarTask.outputs.files.singleFile
        val outputDir = outputJar.parentFile

        if (outputJar.exists()) {
            val newJarName = "canvas-build.$buildNumber.jar"
            val newJarFile = File(outputDir, newJarName)

            outputDir.listFiles()
                ?.filter { it.name.startsWith("canvas-build.") && it.name.endsWith(".jar") }
                ?.forEach { it.delete() }
            outputJar.renameTo(newJarFile)
            println("Renamed ${outputJar.name} to $newJarName in ${outputDir.absolutePath}")
        }
    }
}

tasks.register("fixupMinecraftFilePatches") {
    dependsOn(":canvas-server:fixupMinecraftSourcePatches")
}
