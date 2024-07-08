import com.gradleup.librarian.core.librarianModule
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.targets.js.dsl.ExperimentalWasmDsl

plugins {
  id("org.jetbrains.kotlin.multiplatform")
  id("kotlinx-atomicfu")
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
  js(IR) {
    nodejs()
  }
  @OptIn(ExperimentalWasmDsl::class)
  wasmJs {
    nodejs()
  }

  @OptIn(ExperimentalKotlinGradlePluginApi::class)
  applyDefaultHierarchyTemplate {
    group("common") {
      group("noWasm") {
        group("concurrent") {
          group("apple")
          withJvm()
        }
        withJvm()
      }
    }
  }

  sourceSets {
    getByName("commonMain") {
      dependencies {
        api(libs.apollo.runtime)
        api(libs.apollo.mpp.utils)
        implementation(libs.okio)
        api(libs.uuid)
        implementation(libs.atomicfu.library)
      }
    }

    getByName("commonTest") {
      dependencies {
        implementation(libs.kotlin.test)
        implementation(libs.apollo.testing.support)
      }
    }

    configureEach {
      languageSettings.optIn("com.apollographql.apollo.annotations.ApolloExperimental")
      languageSettings.optIn("com.apollographql.apollo.annotations.ApolloInternal")
    }
  }
}
