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
    findByName("commonMain")?.apply {
      dependencies {
        implementation(libs.apollo.runtime)
        implementation("com.apollographql.cache:normalized-cache-incubating")
      }
    }

    findByName("commonTest")?.apply {
      dependencies {
        implementation(libs.kotlin.test)
        implementation(libs.apollo.testing.support)
        implementation(libs.apollo.mockserver)
      }
    }
  }
}

apollo {
  service("base") {
    packageName.set("defer")
  }
}
