plugins {
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.apollo)
}

kotlin {
  configureKmp(
      withJs = false,
      withWasm = false,
      withAndroid = false,
      withApple = AppleTargets.Host,
  )

  sourceSets {
    getByName("commonMain") {
      dependencies {
        implementation(libs.apollo.runtime)
        implementation("com.apollographql.cache:normalized-cache-sqlite-incubating")
      }
    }

    getByName("commonTest") {
      dependencies {
        implementation(libs.apollo.testing.support)
        implementation(libs.apollo.mockserver)
        implementation(libs.kotlin.test)
        implementation(libs.turbine)
        implementation("com.apollographql.cache:test-utils")
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
  service("programmatic") {
    packageName.set("programmatic")
    srcDir("src/commonMain/graphql/programmatic")
  }

  service("declarative") {
    packageName.set("declarative")
    srcDir("src/commonMain/graphql/declarative")

    plugin("com.apollographql.cache:normalized-cache-apollo-compiler-plugin") {
      argument("packageName", packageName.get())
    }
  }

  service("doNotStore") {
    packageName.set("donotstore")
    srcDir("src/commonMain/graphql/doNotStore")

    plugin("com.apollographql.cache:normalized-cache-apollo-compiler-plugin") {
      argument("packageName", packageName.get())
    }
  }
}
