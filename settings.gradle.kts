pluginManagement {
  includeBuild("build-logic")
}

apply(from = "gradle/repositories.gradle.kts")

include(
    "normalized-cache",
    "normalized-cache-sqlite",
    "normalized-cache-apollo-compiler-plugin",
    "test-utils",
)
