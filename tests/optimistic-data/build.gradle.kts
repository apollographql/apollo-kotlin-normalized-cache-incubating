plugins {
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.apollo)
}

kotlin {
  configureKmp(
      withJs = true,
      withWasm = false,
      withAndroid = false,
      withApple = AppleTargets.Host,
  )

  sourceSets {
    getByName("commonMain") {
      dependencies {
        implementation(libs.apollo.runtime)
        implementation("com.apollographql.cache:normalized-cache-incubating")
      }
    }

    getByName("commonTest") {
      dependencies {
        implementation(libs.kotlin.test)
        implementation(libs.apollo.testing.support)
        implementation(libs.apollo.mockserver)
        implementation(libs.turbine)
      }
    }
  }
}

apollo {
  service("service") {
    packageName.set("optimistic")
  }
}
