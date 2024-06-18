pluginManagement {
  listOf(repositories, dependencyResolutionManagement.repositories).forEach {
    it.mavenCentral()
  }
}

includeBuild("build-logic")

include("normalized-cache-api-incubating")
