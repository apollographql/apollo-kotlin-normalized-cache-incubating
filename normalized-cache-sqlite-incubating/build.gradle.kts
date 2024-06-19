import com.gradleup.librarian.core.librarianModule
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
  id("org.jetbrains.kotlin.multiplatform")
  id("com.android.library")
  id("app.cash.sqldelight")
}

librarianModule()

kotlin {
  jvm()
  macosX64()
  macosArm64()
  iosArm64()
  iosX64()
  iosSimulatorArm64()
  watchosArm32()
  watchosArm64()
  watchosSimulatorArm64()
  tvosArm64()
  tvosX64()
  tvosSimulatorArm64()
  androidTarget {
    publishAllLibraryVariants()
  }

  @OptIn(ExperimentalKotlinGradlePluginApi::class)
  applyDefaultHierarchyTemplate {
    group("common") {
      group("concurrent") {
        group("native") {
          group("apple")
        }
        group("jvmCommon") {
          withJvm()
          withAndroidTarget()
        }
      }
    }
  }
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
  databases.create("JsonDatabase") {
    packageName = "com.apollographql.cache.normalized.sql.internal.json"
    schemaOutputDirectory = file("sqldelight/json/schema")
    srcDirs("src/commonMain/sqldelight/json/")
  }
  databases.create("BlobDatabase") {
    packageName = "com.apollographql.cache.normalized.sql.internal.blob"
    schemaOutputDirectory = file("sqldelight/blob/schema")
    srcDirs("src/commonMain/sqldelight/blob/")
  }
  databases.create("Blob2Database") {
    packageName = "com.apollographql.cache.normalized.sql.internal.blob2"
    schemaOutputDirectory = file("sqldelight/blob2/schema")
    srcDirs("src/commonMain/sqldelight/blob2/")
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

    configureEach {
      languageSettings.optIn("com.apollographql.apollo3.annotations.ApolloExperimental")
      languageSettings.optIn("com.apollographql.apollo3.annotations.ApolloInternal")
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
