import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import io.papermc.paperweight.tasks.RebuildGitPatches
import io.papermc.paperweight.tasks.RebuildBaseGitPatches

plugins {
    java
    id("io.canvasmc.weaver.patcher") version "2.3.2-SNAPSHOT"
}

val paperMavenPublicUrl = "https://repo.papermc.io/repository/maven-public/"

paperweight {
    upstreams.paper {
        ref = providers.gradleProperty("paperCommit")

        patchFile {
            path = "paper-server/build.gradle.kts"
            outputFile = file("canvas-server/build.gradle.kts")
            patchFile = file("canvas-server/build.gradle.kts.patch")
        }
        patchFile {
            path = "paper-api/build.gradle.kts"
            outputFile = file("canvas-api/build.gradle.kts")
            patchFile = file("canvas-api/build.gradle.kts.patch")
        }
        patchDir("paperApi") {
            upstreamPath = "paper-api"
            excludes = listOf("build.gradle.kts")
            patchesDir = file("canvas-api/paper-patches")
            additionalAts?.set(file("build-data/canvas.at"))
            outputDir = file("paper-api")
        }
    }
    additionalUpstreams.register("folia") {
        repo = github("PaperMC", "Folia")
        ref = providers.gradleProperty("foliaCommit")

        sourceGeneration {
            generationConfig.register("folia-api")
            generationConfig.register("folia-server")
        }

        patchGeneration {
            patchesDirOutput = true
            outputDir.set(file("generated-patches")) // only used when patchesDirOutput is disabled

            inputConfig.register("paper-api")
            inputConfig.register("paper-server") {
                additionalPatch.set(file("build-data/patch-gen/0001-PaperServer-Remove-Folia-Profiler.patch"))
            }
            inputConfig.register("minecraft") {
                additionalAts.set(file("build-data/folia.at"))
                additionalPatch.set(file("build-data/patch-gen/0001-Remove-Folia-Profiler.patch"))
            }
        }
    }
}

subprojects {
    apply(plugin = "java-library")
    apply(plugin = "maven-publish")

    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion = JavaLanguageVersion.of(21)
        }
    }

    repositories {
        mavenCentral()
        maven(paperMavenPublicUrl)
    }

    tasks.withType<AbstractArchiveTask>().configureEach {
        isPreserveFileTimestamps = false
        isReproducibleFileOrder = true
    }
    tasks.withType<JavaCompile>().configureEach {
        options.encoding = Charsets.UTF_8.name()
        options.release = 21
        options.isFork = true
        options.compilerArgs.addAll(listOf("-Xlint:-deprecation", "-Xlint:-removal"))
    }
    tasks.withType<Javadoc>().configureEach {
        options.encoding = Charsets.UTF_8.name()
    }
    tasks.withType<ProcessResources>().configureEach {
        filteringCharset = Charsets.UTF_8.name()
    }
    tasks.withType<Test>().configureEach {
        testLogging {
            showStackTraces = true
            exceptionFormat = TestExceptionFormat.FULL
            events(TestLogEvent.STANDARD_OUT)
        }
    }
    extensions.configure<PublishingExtension> {
        repositories {
            maven("https://maven.canvasmc.io/snapshots") {
                name = "canvasmc"
                credentials {
                    username=System.getenv("PUBLISH_USER")
                    password=System.getenv("PUBLISH_TOKEN")
                }
            }
        }
    }
}

// patching scripts
tasks.register("fixupMinecraftFilePatches") {
    dependsOn(":canvas-server:fixupMinecraftSourcePatches")
}

allprojects {
    tasks.withType<RebuildGitPatches>().configureEach {
        filterPatches = false
    }
    tasks.withType<RebuildBaseGitPatches>().configureEach {
        filterPatches = false
    }
}
