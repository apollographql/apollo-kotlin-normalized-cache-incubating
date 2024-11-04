import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

enum class AppleTargets {
  All,
  Host,
}

fun KotlinMultiplatformExtension.configureKmp(
    withJs: Boolean,
    withWasm: Boolean,
    withAndroid: Boolean,
    withApple: AppleTargets = AppleTargets.All,
) {
  jvm()
  when (withApple) {
    AppleTargets.All -> {
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
    }

    AppleTargets.Host -> {
      if (System.getProperty("os.arch") == "aarch64") {
        macosArm64()
      } else {
        macosX64()
      }
    }
  }
  if (withJs) {
    js(IR) {
      nodejs {
        testTask {
          useMocha {
            // Override default 2s timeout
            timeout = "120s"
          }
        }
      }
    }
  }
  if (withWasm) {
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
      nodejs()
    }
  }
  if (withAndroid) {
    androidTarget {
      publishAllLibraryVariants()
    }
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
          if (withAndroid) {
            withAndroidTarget()
          }
        }
      }
      if (withJs || withWasm) {
        group("jsCommon") {
          if (withJs) {
            group("js") {
              withJs()
            }
          }
          if (withWasm) {
            group("wasmJs") {
              withWasmJs()
            }
          }
        }
      }
    }
  }

  sourceSets.configureEach {
    languageSettings.optIn("com.apollographql.apollo.annotations.ApolloExperimental")
    languageSettings.optIn("com.apollographql.apollo.annotations.ApolloInternal")
  }
}
