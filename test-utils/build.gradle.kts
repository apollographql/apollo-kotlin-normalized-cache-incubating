plugins {
  id("org.jetbrains.kotlin.multiplatform")
}

group = "com.apollographql.cache"

kotlin {
  configureKmp(
      withJs = true,
      withWasm = true,
      withAndroid = false,
  )

  sourceSets {
    getByName("commonMain") {
      dependencies {
        api(libs.kotlinx.coroutines.test)
        api(project(":normalized-cache-incubating"))
        implementation(libs.kotlin.test)
      }
    }
    getByName("jsMain") {
      dependencies {
        api(libs.okio.nodefilesystem)
      }
    }
  }
}
