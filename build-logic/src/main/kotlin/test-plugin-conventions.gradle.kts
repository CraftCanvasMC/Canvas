plugins {
    java
    id("xyz.jpenilla.resource-factory-paper-convention")
}

dependencies {
    compileOnly(project(":canvas-server", configuration = JavaPlugin.RUNTIME_ELEMENTS_CONFIGURATION_NAME))
}

extensions.configure<xyz.jpenilla.resourcefactory.paper.PaperPluginYaml> {
    apiVersion.set(providers.gradleProperty("apiVersion"))
    version = "SNAPSHOT-DEV"
    authors = listOf("CanvasMC")
    foliaSupported = true
}

tasks.processResources {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
