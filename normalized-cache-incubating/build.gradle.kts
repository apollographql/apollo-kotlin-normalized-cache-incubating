import com.gradleup.librarian.gradle.librarianModule

plugins {
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.atomicfu)
}

librarianModule(true)

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
