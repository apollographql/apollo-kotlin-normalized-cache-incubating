import com.gradleup.librarian.gradle.Librarian

plugins {
  id("org.jetbrains.kotlin.multiplatform")
  id("org.jetbrains.kotlin.plugin.atomicfu")
}

Librarian.module(project)

kotlin {
  configureKmp(
      withJs = true,
      withWasm = true,
      withAndroid = false,
  )

  sourceSets {
    getByName("commonMain") {
      dependencies {
        api(libs.apollo.runtime)
        api(libs.apollo.mpp.utils)
        implementation(libs.okio)
        api(libs.uuid)
        implementation(libs.atomicfu.library)
      }
    }

    getByName("commonTest") {
      dependencies {
        implementation(libs.kotlin.test)
        implementation(libs.apollo.testing.support)
      }
    }
  }
}
