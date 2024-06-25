import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
  id("org.jetbrains.kotlin.multiplatform")
  id("com.apollographql.apollo3")
}

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

  @OptIn(ExperimentalKotlinGradlePluginApi::class)
  applyDefaultHierarchyTemplate {
    group("common") {
      group("concurrent") {
        group("native") {
          group("apple")
        }
        group("jvmCommon") {
          withJvm()
        }
      }
    }
  }

  sourceSets {
    getByName("commonMain") {
      dependencies {
        implementation(libs.apollo.runtime)
      }
    }

    getByName("commonTest") {
      dependencies {
        implementation(libs.apollo.testing.support)
        implementation(libs.apollo.mockserver)
        implementation(libs.kotlin.test)
        implementation("com.apollographql.cache:normalized-cache-sqlite-incubating")
      }
    }

    getByName("jvmTest") {
      dependencies {
        implementation(libs.slf4j.nop)
      }
    }

    configureEach {
      languageSettings.optIn("com.apollographql.apollo3.annotations.ApolloExperimental")
      languageSettings.optIn("com.apollographql.apollo3.annotations.ApolloInternal")
    }
  }
}

apollo {
  service("service") {
    packageName.set("sqlite")
  }

  // Shouldn't be needed after https://github.com/apollographql/apollo-kotlin/blob/e6dfb1a0ba963080b088660ed80691b91b66e54d/libraries/apollo-gradle-plugin-external/src/main/kotlin/com/apollographql/apollo3/gradle/internal/DefaultApolloExtension.kt#L279
  // is released in the Apollo Gradle plugin.
  linkSqlite.set(true)
}
