plugins {
  alias(libs.plugins.kotlin.multiplatform)
  // TODO: Use the external plugin for now - switch to the regular one when Schema is not relocated
  // See https://github.com/apollographql/apollo-kotlin/pull/6176
  alias(libs.plugins.apollo.external)
}

kotlin {
  configureKmp(
      withJs = false,
      withWasm = false,
      withAndroid = false,
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
}
