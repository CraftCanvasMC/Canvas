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
        maven("https://central.sonatype.com/repository/maven-snapshots/")
    }

    val subproject = this;
    // we don't have any form of publishing for canvas-server, because thats the dev bundle
    if (subproject.name == "canvas-api") {
        publishing {
            repositories {
                maven {
                    name = "central"
                    url = uri("https://central.sonatype.com/repository/maven-snapshots/")
                    credentials {
                        username=System.getenv("PUBLISH_USER")
                        password=System.getenv("PUBLISH_TOKEN")
                    }
                }
            }

            publications {
                create<MavenPublication>("mavenJava") {
                    from(components["java"])

                    afterEvaluate {
                        pom {
                            name.set("canvas-api")
                            description.set(subproject.description)
                            url.set("https://github.com/CraftCanvasMC/Canvas")
                            licenses {
                                license {
                                    name.set("GNU Affero General Public License v3.0")
                                    url.set("https://github.com/CraftCanvasMC/Canvas/blob/master/LICENSE")
                                    distribution.set("repo")
                                }
                            }
                            developers {
                                developer {
                                    id.set("canvas-team")
                                    name.set("Canvas Team")
                                    organization.set("CanvasMC")
                                    organizationUrl.set("https://canvasmc.io")
                                    roles.add("developer")
                                }
                            }
                            scm {
                                url.set("https://github.com/CraftCanvasMC/Canvas")
                            }
                        }
                    }
                }
            }
        }
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

// build publication
tasks.register<Jar>("createMojmapClipboardJar") {
    dependsOn(":canvas-server:createMojmapPaperclipJar")
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

// patching scripts
tasks.register("fixupMinecraftFilePatches") {
    dependsOn(":canvas-server:fixupMinecraftSourcePatches")
}
