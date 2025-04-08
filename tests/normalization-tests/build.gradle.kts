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
        implementation("com.apollographql.cache:test-utils")
        implementation(libs.apollo.mockserver)
        implementation(libs.kotlin.test)
      }
    }

    getByName("jvmTest") {
      dependencies {
        implementation(libs.slf4j.nop)
      }
    }
  }
}

apollo {
  service("1") {
    srcDir("src/commonMain/graphql/1")
    packageName.set("com.example.one")
  }
  service("2") {
    srcDir("src/commonMain/graphql/2")
    packageName.set("com.example.two")
  }
  service("3") {
    srcDir("src/commonMain/graphql/3")
    packageName.set("com.example.three")
  }
}
