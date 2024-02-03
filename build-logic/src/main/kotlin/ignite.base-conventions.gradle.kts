import com.diffplug.gradle.spotless.FormatExtension
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.regex.Pattern
import java.util.stream.Collectors

plugins {
  `java-library`
  id("checkstyle")
  id("com.diffplug.spotless")
}

// Expose version catalog
val libs = extensions.getByType(org.gradle.accessors.dm.LibrariesForLibs::class)

group = rootProject.group
version = rootProject.version

java {
  javaTarget(17)
  withSourcesJar()
}

dependencies {
  compileOnly(libs.jetbrains.annotations)
}

tasks {
  javadoc {
    val minimalOptions: MinimalJavadocOptions = options
    options.encoding("UTF-8")

    if (minimalOptions is StandardJavadocDocletOptions) {
      val options: StandardJavadocDocletOptions = minimalOptions
      options.addStringOption("Xdoclint:none", "-quiet")
    }
  }

  compileJava {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf(
      "-nowarn",
      "-Xlint:-unchecked",
      "-Xlint:-deprecation"
    ))
  }
}
