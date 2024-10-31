pluginManagement {
  includeBuild("build-logic")
}

apply(from = "gradle/repositories.gradle.kts")

include(
    "normalized-cache-incubating",
    "normalized-cache-sqlite-incubating",
    "normalized-cache-apollo-compiler-plugin",
)
