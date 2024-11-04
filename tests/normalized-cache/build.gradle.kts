plugins {
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.apollo)
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
  service("main") {
    packageName.set("main")
    srcDir(file("src/commonMain/graphql/main"))
  }

  service("httpcache") {
    packageName.set("httpcache")
    srcDir(file("src/commonMain/graphql/httpcache"))
  }

  service("normalizer") {
    packageName.set("normalizer")
    srcDir(file("src/commonMain/graphql/normalizer"))
    generateFragmentImplementations.set(true)
    mapScalarToKotlinString("Date")
    mapScalarToKotlinString("Instant")
    sealedClassesForEnumsMatching.set(listOf("Episode"))
    generateOptionalOperationVariables.set(false)
  }

  service("circular") {
    packageName.set("circular")
    srcDir(file("src/commonMain/graphql/circular"))
    generateOptionalOperationVariables.set(false)
  }

  service("declarativecache") {
    packageName.set("declarativecache")
    srcDir(file("src/commonMain/graphql/declarativecache"))
    generateOptionalOperationVariables.set(false)
  }

  service("fragmentnormalizer") {
    packageName.set("fragmentnormalizer")
    srcDir(file("src/commonMain/graphql/fragmentnormalizer"))
    generateOptionalOperationVariables.set(false)
    generateFragmentImplementations.set(true)
  }

}
