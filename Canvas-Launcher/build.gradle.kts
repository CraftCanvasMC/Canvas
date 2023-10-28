import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import io.papermc.paperweight.util.convertToPath
import java.nio.file.Paths

plugins {
  id("canvas.launcher-api")
}

tasks.named<ShadowJar>("shadowJar") {
    dependsOn(rootProject.tasks.getByName("createCanvasBundler"))
    println("Compiling server jar into resources and shading...")
}

tasks.getByName("compileJava").dependsOn(rootProject.tasks.getByName("createCanvasBundler"))
tasks.getByName("processResources").dependsOn(rootProject.tasks.getByName("createCanvasBundler"))

applyJarMetadata("space.vectrix.ignite")
repositories {
  mavenCentral()
}

fun ShadowJar.configureRelocations() {
  relocate("com.google.common", "space.vectrix.ignite.libs.google.common")
  relocate("net.kyori", "space.vectrix.ignite.libs.kyori")
  relocate("org.spongepowered.configurate.gson", "space.vectrix.ignite.libs.configurate.gson")
  relocate("org.spongepowered.configurate.hocon", "space.vectrix.ignite.libs.configurate.hocon")
  relocate("org.spongepowered.configurate.yaml", "space.vectrix.ignite.libs.configurate.yaml")
  relocate("org.yaml.snakeyaml", "space.vectrix.ignite.libs.snakeyaml")
  relocate("com.typesafe.config", "space.vectrix.ignite.libs.typesafe")
}

fun ShadowJar.configureExcludes() {
  // Guava - Only need a few things.
  exclude("com/google/common/escape/*")
  exclude("com/google/common/eventbus/*")
  exclude("com/google/common/html/*")
  exclude("com/google/common/net/*")
  exclude("com/google/common/xml/*")
  exclude("com/google/thirdparty/**")

  dependencies {
    // Checkerframework
    exclude(dependency("org.checkerframework:checker-qual"))

    // Google
    exclude(dependency("com.google.errorprone:error_prone_annotations"))
    exclude(dependency("com.google.j2objc:j2objc-annotations"))
  }
}
