pluginManagement {
  listOf(repositories, dependencyResolutionManagement.repositories).forEach {
    it.mavenCentral()
    it.google()
  }
}

includeBuild("build-logic")

include("normalized-cache-incubating", "normalized-cache-sqlite-incubating")
