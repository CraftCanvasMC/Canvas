import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
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
    id("io.github.goooler.shadow") version "8.1.7" apply false
    id("io.papermc.paperweight.patcher") version "1.6.3"
}

allprojects {
    apply(plugin = "java")
    apply(plugin = "maven-publish")

    java {
        toolchain {
            languageVersion = JavaLanguageVersion.of(21)
        }
    }
}

val paperMavenPublicUrl = "https://repo.papermc.io/repository/maven-public/"

subprojects {
    tasks.withType<JavaCompile>().configureEach {
        options.encoding = Charsets.UTF_8.name()
        options.release = 21
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
    }
}

repositories {
    mavenCentral()
    jcenter()
    maven(paperMavenPublicUrl) {
        content {
            onlyForConfigurations(configurations.paperclip.name)
        }
    }
}

dependencies {
    remapper("net.fabricmc:tiny-remapper:0.8.10:fat")
    decompiler("net.minecraftforge:forgeflower:2.0.627.2")
    paperclip("io.papermc:paperclip:3.0.3")
    // implementation(libs.build.nexus)
    // implementation(libs.build.shadow)
    // implementation(libs.build.spotless)
    compileOnly(files(libs.javaClass.superclass.protectionDomain.codeSource.location))
}

paperweight {
    serverProject = project(":canvas-server")

    remapRepo = paperMavenPublicUrl
    decompileRepo = paperMavenPublicUrl

    useStandardUpstream("purpur") {
        url = github("PurpurMC", "Purpur")
        ref = providers.gradleProperty("purpurCommit")

        withStandardPatcher {
            baseName("Purpur")

            apiPatchDir = layout.projectDirectory.dir("patches/api")
            apiOutputDir = layout.projectDirectory.dir("Canvas-API")

            serverPatchDir = layout.projectDirectory.dir("patches/server")
            serverOutputDir = layout.projectDirectory.dir("Canvas-Server")
        }

        patchTasks.register("generatedApi") {
            isBareDirectory = true
            upstreamDirPath = "paper-api-generator/generated"
            patchDir = layout.projectDirectory.dir("patches/generated-api")
            outputDir = layout.projectDirectory.dir("paper-api-generator/generated")
        }
    }
}

tasks.generateDevelopmentBundle {
    apiCoordinates = "io.github.dueris:canvas-api"
    mojangApiCoordinates = "io.papermc.paper:paper-mojangapi"
    libraryRepositories = listOf(
        "https://repo.maven.apache.org/maven2/",
        paperMavenPublicUrl,
        "https://repo.purpurmc.org/snapshots",
    )
}

tasks.register("printMinecraftVersion") {
    doLast {
        println(providers.gradleProperty("mcVersion").get().trim())
    }
}

tasks.register<DefaultTask>("createCanvasBundler") {
    dependsOn(":remapPurpurClip")

    doLast {
        val rmapLoc: Path = projectDir.toPath().toAbsolutePath().resolve(".gradle/caches/canvas/building")
        val targetJarDirectory: Path = projectDir.toPath().toAbsolutePath().resolve("Canvas-Launcher/src/main/resources")

        Files.createDirectories(targetJarDirectory)
        Files.copy(
            rmapLoc.resolve("canvas-1.20.6.jar"),
            targetJarDirectory.resolve("canvas-1.20.6-R0.1-SNAPSHOT.zip"),
            StandardCopyOption.REPLACE_EXISTING
        )
    }
}

fun clearCanvasCache() {
    val rmapLoc: Path = projectDir.toPath().toAbsolutePath().resolve(".gradle/caches/canvas/building")
        Files.createDirectories(rmapLoc)
        Files.walk(rmapLoc)
                .sorted(Comparator.reverseOrder())
                .forEach { Files.delete(it) }
}

fun copyToTarget() {
    val shadowJar: ShadowJar = projects.canvasLauncher.dependencyProject.tasks.getByName<ShadowJar>("shadowJar")
        val targetJarDirectory: Path = projectDir.toPath().toAbsolutePath().resolve("target")

        Files.createDirectories(targetJarDirectory)
        Files.copy(
            shadowJar.archiveFile.get().asFile.toPath().toAbsolutePath(),
            targetJarDirectory.resolve(shadowJar.archiveBaseName.get() + ".jar"),
            StandardCopyOption.REPLACE_EXISTING
        )
}

tasks.register<DefaultTask>("remapPurpurClip") {
    dependsOn(":createMojmapPaperclipJar")
    clearCanvasCache()

    doLast{
        val rmapLoc: Path = projectDir.toPath().toAbsolutePath().resolve(".gradle/caches/canvas/building")
        Files.createDirectories(rmapLoc)
        Files.copy(
            file("build/libs/canvas-paperclip-1.20.6-R0.1-SNAPSHOT-mojmap.jar").toPath().toAbsolutePath(),
            rmapLoc.resolve("canvas-1.20.6.zip"),
            StandardCopyOption.REPLACE_EXISTING
        )
        Files.copy(
            file("build/libs/canvas-paperclip-1.20.6-R0.1-SNAPSHOT-mojmap.jar").toPath().toAbsolutePath(),
            rmapLoc.resolve("canvas-1.20.6.jar"),
            StandardCopyOption.REPLACE_EXISTING
        )
    }
}

tasks.register<DefaultTask>("createCanvasServer") {
    dependsOn(":createCanvasBundler", projects.canvasLauncher.dependencyProject.tasks.withType<ShadowJar>())

    doLast {
        copyToTarget()
        clearCanvasCache()
    }
}

tasks.register<Copy>("viewLauncherContents") {
    from(zipTree(file("target/canvas-launcher.jar")))
    into(file("target/view"))
}

publishing {
    publications.create<MavenPublication>("devBundle") {
        artifact(tasks.generateDevelopmentBundle) {
            groupId = "io.github.dueris"
            artifactId = "dev-bundle"
        }
    }
    repositories {
        maven {
            name = "sonatype"
            if (version.toString().endsWith("SNAPSHOT")) {
                url = uri("https://s01.oss.sonatype.org/content/repositories/snapshots/")
            } else {
                url = uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
            }
            credentials {
                username=System.getenv("OSSRH_USERNAME")
                password=System.getenv("OSSRH_PASSWORD")
            }
        }
    }
}
