plugins {
  alias(libs.plugins.kotlin.multiplatform)
  // TODO: Use the external plugin for now - switch to the regular one when Schema is not relocated
  // See https://github.com/apollographql/apollo-kotlin/pull/6176
  alias(libs.plugins.apollo.external)
}

kotlin {
  configureKmp(
      withJs = true,
      withWasm = true,
      withAndroid = false,
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
      }
    }
  }
}

apollo {
  service("service") {
    packageName.set("cache.include")
  }
}
