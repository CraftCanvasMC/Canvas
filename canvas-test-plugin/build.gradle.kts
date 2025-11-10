plugins {
    `java-library`
    idea
}

dependencies {
    implementation(projects.canvasServer)
    implementation(projects.canvasApi)
}

tasks.register<Copy>("buildAndCopyPlugin") {
    dependsOn(tasks.named("build"))

    from(layout.buildDirectory.dir("libs"))
    include("canvas-test-plugin-*.jar")
    into(file("../local/plugins/"))
}
