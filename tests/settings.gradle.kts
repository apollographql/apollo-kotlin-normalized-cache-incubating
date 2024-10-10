includeBuild("../")

pluginManagement {
  listOf(repositories, dependencyResolutionManagement.repositories).forEach {
    it.apply {
      maven {
        url = uri("https://s01.oss.sonatype.org/content/repositories/snapshots/")
      }
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
