plugins {
  `embedded-kotlin`
}

dependencies {
  implementation(libs.kgp)
  implementation(libs.librarian)
  implementation(libs.atomicfu.plugin)
  implementation(libs.android.plugin)
  implementation(libs.sqldelight.plugin)
  implementation(libs.apollo.gradle.plugin)
}

group = "build-logic"
