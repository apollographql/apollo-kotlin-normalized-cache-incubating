plugins {
  id("build.logic") apply false

  alias(libs.plugins.kotlin.multiplatform).apply(false)
  alias(libs.plugins.apollo).apply(false)
  alias(libs.plugins.apollo.external).apply(false)
}
