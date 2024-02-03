rootProject.name = "ignite-build-logic"

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    gradlePluginPortal()
  }

  versionCatalogs {
    register("libs") {
      from(files("../gradle/libs.versions.toml")) // include from parent project
    }
  }
}
