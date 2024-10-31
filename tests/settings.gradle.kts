includeBuild("../")

pluginManagement {
  includeBuild("../build-logic")
}

rootProject.name = "apollo-kotlin-normalized-cache-incubating-tests"

apply(from = "../gradle/repositories.gradle.kts")

// Include all tests
rootProject.projectDir
  .listFiles()!!
  .filter { it.isDirectory && File(it, "build.gradle.kts").exists() }
  .forEach {
    include(it.name)
  }
