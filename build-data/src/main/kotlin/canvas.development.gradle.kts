import java.io.File

// TODO - this is slow af.
tasks.register("buildFeaturePatches") {
    val name = project.findProperty("commitName")
        ?: throw GradleException("The 'commitName' property must be specified.")
    val justServer = project.findProperty("justServer")
        ?: false

    doLast {
        println("Building feature commit of \"$name\"")
        gitCommit("./purpur-server/", "$name")
        gitCommit("./purpur-api/", "$name")
        gitCommit("./paper-server/", "$name")
        gitCommit("./paper-api/", "$name")
        gitCommit("./canvas-server/src/minecraft/java", "$name")

        println("Running API rebuilding...")
        runGradleTask("rebuildPurpurApiFeaturePatches")
        runGradleTask("rebuildPaperApiFeaturePatches")

        println("Running Server rebuilding...")
        runGradleTask("rebuildPurpurServerFeaturePatches")
        runGradleTask("rebuildPaperServerFeaturePatches")
        runGradleTask("rebuildMinecraftFeaturePatches")
    }
}

fun gitCommit(dir: String, commitMessage: String) {
    val directory = File(dir)

    if (!directory.exists() || !directory.isDirectory) {
        throw IllegalArgumentException("The directory '$dir' does not exist or is not a valid directory.")
    }

    val processBuilder = ProcessBuilder()
    processBuilder.directory(directory)

    var process = processBuilder.command("git", "add", ".").start()
    process.waitFor()

    process = processBuilder.command("git", "commit", "-m", commitMessage).start()
    process.waitFor()

    println("Completed commit in directory $dir")
}

fun runGradleTask(taskName: String) {
    val processBuilder = ProcessBuilder("./gradlew", taskName)
    val dir = "./"
    processBuilder.directory(File(dir))

    println("Running Gradle task: $taskName")

    val process = processBuilder.start()
    val exitCode = process.waitFor()

    if (exitCode != 0) {
        println("Gradle task '$taskName' failed with exit code $exitCode")
    } else {
        println("Gradle task '$taskName' completed successfully")
    }
}
