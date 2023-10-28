plugins {
  id("net.kyori.indra")
  id("net.kyori.indra.license-header")
}

repositories {
  mavenLocal()
  mavenCentral()
  maven {
    url = uri("https://oss.sonatype.org/content/groups/public/")
  }
}

java {
  toolchain {
    languageVersion.set(JavaLanguageVersion.of(17))
  }
}

indra {
  javaVersions {
    target(17)
  }

  github("vectrix-space", "ignite") {
    ci(true)
  }

  mitLicense()
}
