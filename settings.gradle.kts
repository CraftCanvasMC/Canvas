import java.util.Locale

pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://repo.papermc.io/repository/maven-public/")
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.7.0"
}

if (!file(".git").exists()) {
    val errorText = """
        
        =====================[ ERROR ]=====================
         The Canvas project directory is not a properly cloned Git repository.
         
         In order to build Canvas from source you must clone
         the Canvas repository using Git, not download a code
         zip from GitHub.
         
         Built Canvas jars are available for download at
         https://canvas.kesug.com/downloads/
         
         See https://github.com/CraftCanvasMC/Canvas/blob/HEAD/CONTRIBUTING.md
         for further information on building and modifying Canvas.
        ===================================================
    """.trimIndent()
    error(errorText)
}

includeBuild("build-logic")
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "canvas"
for (name in listOf("Canvas-API", "Canvas-Server", "Canvas-Launcher")) {
    val projName = name.lowercase(Locale.ENGLISH)
    include(projName)
    findProject(":$projName")!!.projectDir = file(name)
}
