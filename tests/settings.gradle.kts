pluginManagement {
  listOf(repositories, dependencyResolutionManagement.repositories).forEach {
    it.apply {
      mavenCentral()
    }
  }
}

rootProject.name = "apollo-kotlin-normalized-cache-incubating-tests"

// Include all tests
rootProject.projectDir
  .listFiles()!!
  .filter { it.isDirectory && File(it, "build.gradle.kts").exists() }
  .forEach {
    include(it.name)
  }

includeBuild("../")
