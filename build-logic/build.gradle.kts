plugins {
  `embedded-kotlin`
}

dependencies {
  implementation(libs.kgp)
  implementation(libs.librarian)
  implementation(libs.atomicfu.plugin)
  implementation(libs.android.plugin)
  implementation(libs.sqldelight.plugin)
}

group = "build-logic"
