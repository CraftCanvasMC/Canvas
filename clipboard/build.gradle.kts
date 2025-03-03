plugins {
    java
    application
    `maven-publish`
    id("io.github.goooler.shadow") version "8.1.7"
}

val mainClass = "io.canvasmc.clipboard.Main"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(22))
    }

    withSourcesJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(22)
    options.encoding = "UTF-8"
}

tasks.jar {
    val java22Jar = tasks.named("shadowJar")
    dependsOn(java22Jar)
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(zipTree(java22Jar.map { it.outputs.files.singleFile }))

    manifest {
        attributes(
            // paperclip args
            "Main-Class" to mainClass,
            "Enable-Native-Access" to "ALL-UNNAMED",
            // setup agent
            "Premain-Class" to "io.canvasmc.clipboard.Instrumentation",
            "Agent-Class" to "io.canvasmc.clipboard.Instrumentation",
            "Launcher-Agent-Class" to "io.canvasmc.clipboard.Instrumentation",
            "Can-Redefine-Classes" to true,
            "Can-Retransform-Classes" to true
        )
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.sigpipe:jbsdiff:1.0")
}

project.setProperty("mainClassName", mainClass)

tasks.shadowJar {
    val prefix = "paperclip.libs"
    listOf("org.apache", "org.tukaani", "io.sigpipe").forEach { pack ->
        relocate(pack, "$prefix.$pack")
    }
}
