plugins {
  id("ignite.base-conventions")
  `maven-publish`
  signing
}

// Expose version catalog
val libs = extensions.getByType(org.gradle.accessors.dm.LibrariesForLibs::class)

publishing {
  publications {
    create<MavenPublication>("mavenJava") {
      from(components["java"])

      pom {
        name.set(project.name)
        description.set(project.description)
        url.set("https://vectrix.space")

        licenses {
          license {
            name.set("MIT License")
            url.set("https://opensource.org/licenses/MIT")
          }
        }

        developers {
          developer {
            id.set("vectrix")
            name.set("Vectrix")
          }
        }

        scm {
          connection.set("scm:git:https://github.com/vectrix-space/ignite.git")
          developerConnection.set("scm:git:https://github.com/vectrix-space/ignite.git")
          url.set("https://github.com/vectrix-space/ignite.git")
        }
      }
    }
  }
}

signing {
  sign(publishing.publications["mavenJava"])

  if(project.hasProperty("signingKey") && project.hasProperty("signingPassword")) {
    useInMemoryPgpKeys(
      project.property("signingKey").toString(),
      project.property("signingPassword").toString()
    )
  }
}

tasks.withType(Sign::class) {
  onlyIf { project.hasProperty("signingKey") && project.hasProperty("signingPassword") }
}
