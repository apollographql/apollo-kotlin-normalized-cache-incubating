pluginManagement {
  listOf(repositories, dependencyResolutionManagement.repositories).forEach {
    it.apply {
      maven {
        url = uri("https://s01.oss.sonatype.org/content/repositories/snapshots/")
      }
      mavenCentral()
      google()
    }
  }
}

include(
    "normalized-cache-incubating",
    "normalized-cache-sqlite-incubating",
    "normalized-cache-apollo-compiler-plugin",
)
