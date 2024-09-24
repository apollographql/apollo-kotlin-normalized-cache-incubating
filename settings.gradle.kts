pluginManagement {
  listOf(repositories, dependencyResolutionManagement.repositories).forEach {
    it.mavenCentral()
    it.google()
  }
}

include("normalized-cache-incubating", "normalized-cache-sqlite-incubating")
