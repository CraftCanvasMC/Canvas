import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import io.papermc.paperweight.tasks.CreateBundlerJar
import io.papermc.paperweight.tasks.RemapJar
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

plugins {
    java
    `maven-publish`
    id("com.github.johnrengelman.shadow") version "8.1.1" apply false
    id("io.papermc.paperweight.patcher") version "1.5.10"
    id("ignite.parent-build-logic")
}

allprojects {
    apply(plugin = "java")
    apply(plugin = "maven-publish")

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(17))
        }
    }
}

val paperMavenPublicUrl = "https://repo.papermc.io/repository/maven-public/"

subprojects {
    tasks.withType<JavaCompile>().configureEach {
        options.encoding = Charsets.UTF_8.name()
        options.release.set(17)
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
}

paperweight {
    serverProject.set(project(":canvas-server"))

    remapRepo.set(paperMavenPublicUrl)
    decompileRepo.set(paperMavenPublicUrl)

    useStandardUpstream("purpur") {
        url.set(github("PurpurMC", "Purpur"))
        ref.set(providers.gradleProperty("purpurCommit"))

        withStandardPatcher {
            baseName("Purpur")

            apiPatchDir.set(layout.projectDirectory.dir("patches/api"))
            apiOutputDir.set(layout.projectDirectory.dir("Canvas-API"))

            serverPatchDir.set(layout.projectDirectory.dir("patches/server"))
            serverOutputDir.set(layout.projectDirectory.dir("Canvas-Server"))
        }
    }
}

tasks.generateDevelopmentBundle {
    apiCoordinates.set("io.github.dueris:canvas-api")
    mojangApiCoordinates.set("io.papermc.paper:paper-mojangapi")
    libraryRepositories.set(
        listOf(
            "https://repo.maven.apache.org/maven2/",
            paperMavenPublicUrl,
            "https://repo.purpurmc.org/snapshots",
        )
    )
}

tasks.register("printMinecraftVersion") {
    doLast {
        println(providers.gradleProperty("mcVersion").get().trim())
    }
}

tasks.register<DefaultTask>("createCanvasBundler") {
    dependsOn(tasks.withType<CreateBundlerJar>())

    doLast {
        val shadowJar: CreateBundlerJar = tasks.getByName<CreateBundlerJar>("createReobfBundlerJar")
        val targetJarDirectory: Path = projectDir.toPath().toAbsolutePath().resolve("Canvas-Launcher/src/main/resources")

        Files.createDirectories(targetJarDirectory)
        Files.copy(
            shadowJar.outputZip.asFile.get().toPath().toAbsolutePath(),
            targetJarDirectory.resolve("canvas-server.jar"),
            StandardCopyOption.REPLACE_EXISTING
        )
    }
}

tasks.register<DefaultTask>("createCanvasServer") {
    // Specify the project path for createCanvasBundler in the dependsOn statement
    dependsOn(":createCanvasBundler", projects.canvasLauncher.dependencyProject.tasks.withType<ShadowJar>())

    doLast {
        val shadowJar: ShadowJar = projects.canvasLauncher.dependencyProject.tasks.getByName<ShadowJar>("shadowJar")
        val targetJarDirectory: Path = projectDir.toPath().toAbsolutePath().resolve("target")

        Files.createDirectories(targetJarDirectory)
        Files.copy(
            shadowJar.archiveFile.get().asFile.toPath().toAbsolutePath(),
            targetJarDirectory.resolve(shadowJar.archiveBaseName.get() + ".jar"),
            StandardCopyOption.REPLACE_EXISTING
        )
    }
}

tasks.register<Copy>("renameServer") {
    from("build/libs/paperweight-development-bundle-1.20.2-R0.1-SNAPSHOT.zip")
    into("Canvas-Server/build/libs")
    include("paperweight-development-bundle-1.20.2-R0.1-SNAPSHOT.zip")

    eachFile {
        val fileName = name
        name = fileName.replace("paperweight-development-bundle-1.20.2-R0.1-SNAPSHOT.zip", "canvas-server.zip")
    }
}

tasks.register<Copy>("renameApi") {
    from("Canvas-API/build/libs/canvas-api-1.20.2-R0.1-SNAPSHOT-sources.jar")
    into("Canvas-API/build/libs")
    include("canvas-api-1.20.2-R0.1-SNAPSHOT-sources.jar")

    eachFile {
        val fileName = name
        name = fileName.replace("canvas-api-1.20.2-R0.1-SNAPSHOT-sources.jar", "canvas-api.jar")
    }
}

tasks.register<Copy>("unzipLauncherData") {
    from(zipTree(file("work/launcherData.csd")))
    into(file("./Canvas-Launcher/"))
}

tasks.register<Zip>("repackLauncherData") {
    from(file("./Canvas-Launcher/")) {
        exclude("**/build/**")
        exclude("**/bin/**")
        exclude("**/src/main/resources/canvas-server.jar")
    }
    archiveFileName.set("launcherData.csd")
    destinationDirectory.set(file("work"))
}

publishing {
    publications.create<MavenPublication>("devBundle") {
        artifact(tasks.generateDevelopmentBundle) {
            groupId = "io.github.dueris"
            artifactId = "canvas-devBundle"
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

tasks.getByName("rebuildPatches").dependsOn("repackLauncherData")
tasks.getByName("applyPatches").dependsOn("unzipLauncherData")
