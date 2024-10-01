pluginManagement {
  listOf(repositories, dependencyResolutionManagement.repositories).forEach {
    it.mavenLocal()
    it.mavenCentral()
    it.google()
  }
}

include(
    "normalized-cache-incubating",
    "normalized-cache-sqlite-incubating",
    "normalized-cache-apollo-compiler-plugin",
)
