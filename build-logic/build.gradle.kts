plugins {
  `embedded-kotlin`
}

dependencies {
  implementation(libs.kgp)
  implementation(libs.librarian)
  implementation(libs.atomicfu.plugin)
}

group = "build-logic"
