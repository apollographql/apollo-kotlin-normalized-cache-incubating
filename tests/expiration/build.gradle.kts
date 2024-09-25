import com.apollographql.cache.gradle.GenerateApolloCacheSourcesTask
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.apollo)
  alias(libs.plugins.apollo.cache)
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
      languageSettings.optIn("com.apollographql.apollo.annotations.ApolloExperimental")
      languageSettings.optIn("com.apollographql.apollo.annotations.ApolloInternal")
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
  }
}


val generateDeclarativeApolloCacheSourcesTask =
  tasks.register("generateDeclarativeApolloCacheSources", GenerateApolloCacheSourcesTask::class.java) {
    packageName.set("declarative")
    schemaFiles.from(file("src/commonMain/graphql/declarative"))
  }

kotlin.sourceSets.getByName("commonMain").kotlin.srcDir(generateDeclarativeApolloCacheSourcesTask)
