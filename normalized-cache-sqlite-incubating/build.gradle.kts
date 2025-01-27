import com.gradleup.librarian.gradle.librarianModule

plugins {
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.android)
  alias(libs.plugins.sqldelight)
}

librarianModule(true)

kotlin {
  configureKmp(
      withJs = false,
      withWasm = false,
      withAndroid = true,
  )
}

android {
  namespace = "com.apollographql.apollo.cache.normalized.sql"
  compileSdk = 34

  defaultConfig {
    minSdk = 16
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    multiDexEnabled = true
  }

  testOptions.targetSdk = 30
}


configure<app.cash.sqldelight.gradle.SqlDelightExtension> {
  databases.create("BlobDatabase") {
    packageName.set("com.apollographql.cache.normalized.sql.internal.blob")
    schemaOutputDirectory.set(file("sqldelight/blob/schema"))
    srcDirs.setFrom("src/commonMain/sqldelight/blob/")
  }
  databases.create("Blob2Database") {
    packageName.set("com.apollographql.cache.normalized.sql.internal.blob2")
    schemaOutputDirectory.set(file("sqldelight/blob2/schema"))
    srcDirs.setFrom("src/commonMain/sqldelight/blob2/")
  }
}

kotlin {
  androidTarget {
    publishAllLibraryVariants()
  }

  sourceSets {
    getByName("commonMain") {
      dependencies {
        api(libs.apollo.api)
        api(project(":normalized-cache-incubating"))
        api(libs.sqldelight.runtime)
      }
    }

    getByName("jvmMain") {
      dependencies {
        implementation(libs.sqldelight.jvm)
      }
    }

    getByName("appleMain") {
      dependencies {
        implementation(libs.sqldelight.native)
      }
    }

    getByName("jvmTest") {
      dependencies {
        implementation(libs.truth)
        implementation(libs.slf4j.nop)
      }
    }

    getByName("androidMain") {
      dependencies {
        api(libs.androidx.sqlite)
        implementation(libs.sqldelight.android)
        implementation(libs.androidx.sqlite.framework)
        implementation(libs.androidx.startup.runtime)
      }
    }
    getByName("androidUnitTest") {
      dependencies {
        implementation(libs.kotlin.test.junit)
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

tasks.configureEach {
  if (name.endsWith("UnitTest")) {
    /**
     * Because there is no App Startup in Android unit tests, the Android tests
     * fail at runtime so ignore them
     * We could make the Android unit tests use the Jdbc driver if we really wanted to
     */
    enabled = false
  }
}
