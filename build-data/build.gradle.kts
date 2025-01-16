plugins {
  `kotlin-dsl`
}

dependencies {
}

java {
  sourceCompatibility = JavaVersion.VERSION_22
  targetCompatibility = JavaVersion.VERSION_22
}

kotlin {
  target {
    compilations.configureEach {
      kotlinOptions {
        jvmTarget = "22"
      }
    }
  }
}
