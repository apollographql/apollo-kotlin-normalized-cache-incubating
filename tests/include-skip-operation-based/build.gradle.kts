import com.apollographql.apollo.annotations.ApolloExperimental

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
  service("service") {
    packageName.set("com.example")
    @OptIn(ApolloExperimental::class)
    generateDataBuilders.set(true)
  }
}
