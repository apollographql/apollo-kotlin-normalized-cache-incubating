pluginManagement {
  listOf(repositories, dependencyResolutionManagement.repositories).forEach {
    it.mavenCentral()
    it.gradlePluginPortal()
    it.google()
  }
}

dependencyResolutionManagement {
  versionCatalogs {
    create("libs") {
      from(files("../gradle/libs.versions.toml"))
    }
  }
}
