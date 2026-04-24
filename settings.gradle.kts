import java.util.*

pluginManagement {
    repositories {
        mavenLocal()
        gradlePluginPortal()
        maven {
            name = "canvasmc"
            url = uri("https://maven.canvasmc.io/releases")
        }
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

if (!file(".git").exists()) {
    val errorText = """
        
        =====================[ ERROR ]=====================
         The Canvas project directory is not a properly cloned Git repository.
         
         In order to build Canvas from source you must clone
         the Canvas repository using Git, not download a code
         zip from GitHub.
         
         Built Canvas jars are available for download at
         https://canvasmc.io/downloads
         
         See https://github.com/CraftCanvasMC/Canvas/blob/HEAD/CONTRIBUTING.md
         for further information on building and modifying Canvas.
        ===================================================
    """.trimIndent()
    error(errorText)
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "canvas"
for (name in listOf("canvas-api", "canvas-server")) {
    val projName = name.lowercase(Locale.ENGLISH)
    include(projName)
    findProject(":$projName")!!.projectDir = file(name)
}

rootDir.listFiles()
    ?.filter { it.isDirectory && (it.name.endsWith("-debug", ignoreCase = true) || it.name.endsWith("-plugin", ignoreCase = true)) }
    ?.forEach { dir ->
        val projName = dir.name.lowercase(Locale.ENGLISH)
        include(projName)
        findProject(":$projName")!!.projectDir = dir
    }

gradle.lifecycle.beforeProject {
    val mcVersion = providers.gradleProperty("mcVersion").get().trim()
    val canvasChannel = providers.gradleProperty("channel").get().trim()
    val canvasBuildNumber = providers.environmentVariable("BUILD_NUMBER").orNull?.trim()?.toInt()
    val versionString = if (canvasBuildNumber == null) {
        "$mcVersion.local-SNAPSHOT"
    } else {
        "$mcVersion.build.$canvasBuildNumber-${canvasChannel.lowercase()}"
    }
    version = versionString
}
