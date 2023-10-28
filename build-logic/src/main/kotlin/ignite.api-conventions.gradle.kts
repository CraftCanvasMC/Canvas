import de.marcphilipp.gradle.nexus.NexusPublishExtension
import org.gradle.plugins.signing.Sign

plugins {
  id("ignite.base-conventions")
  id("net.kyori.indra.publishing")
  id("de.marcphilipp.nexus-publish")
  id("signing")
}

/* // Canvas start - change to canvas sonatype/remove ignite sonatype integration
signing {
  val signingKey: String? by project
  val signingPassword: String? by project
  useInMemoryPgpKeys(signingKey, signingPassword)
  sign(configurations.archives.get())
}

indra {
  extensions.configure<NexusPublishExtension> {
    repositories.sonatype {
      nexusUrl.set(uri("https://s01.oss.sonatype.org/service/local/"))
      snapshotRepositoryUrl.set(uri("https://s01.oss.sonatype.org/content/repositories/snapshots/"))
    }
  }

  configurePublications {
    pom {
      developers {
        developer {
          id.set("VectrixDevelops")
          name.set("Vectrix")
        }
      }
    }
  }
}

tasks {
  withType<PublishToMavenRepository>().configureEach {
    onlyIf {
      val version: String = project.version.toString()
      System.getenv("CI") == null || version.endsWith("-SNAPSHOT")
    }
  }

  withType<Sign>().configureEach {
    onlyIf {
      project.hasProperty("signingEnabled")
    }
  }
}
*/ // Canvas end
