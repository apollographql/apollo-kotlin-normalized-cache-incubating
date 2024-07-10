import com.apollographql.apollo.annotations.ApolloExperimental
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
  id("org.jetbrains.kotlin.multiplatform")
  id("com.apollographql.apollo")
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
  service("embed") {
    packageName.set("embed")
    srcDir("src/commonMain/graphql/embed")
  }

  service("pagination.offsetBasedWithArray") {
    packageName.set("pagination.offsetBasedWithArray")
    srcDir("src/commonMain/graphql/pagination/offsetBasedWithArray")
    @OptIn(ApolloExperimental::class)
    generateDataBuilders.set(true)
  }
  service("pagination.offsetBasedWithPage") {
    packageName.set("pagination.offsetBasedWithPage")
    srcDir("src/commonMain/graphql/pagination/offsetBasedWithPage")
    @OptIn(ApolloExperimental::class)
    generateDataBuilders.set(true)
  }
  service("pagination.offsetBasedWithPageAndInput") {
    packageName.set("pagination.offsetBasedWithPageAndInput")
    srcDir("src/commonMain/graphql/pagination/offsetBasedWithPageAndInput")
    @OptIn(ApolloExperimental::class)
    generateDataBuilders.set(true)
  }
  service("pagination.cursorBased") {
    packageName.set("pagination.cursorBased")
    srcDir("src/commonMain/graphql/pagination/cursorBased")
    @OptIn(ApolloExperimental::class)
    generateDataBuilders.set(true)
  }
  service("pagination.connection") {
    packageName.set("pagination.connection")
    srcDir("src/commonMain/graphql/pagination/connection")
    @OptIn(ApolloExperimental::class)
    generateDataBuilders.set(true)
  }
  service("pagination.connectionWithNodes") {
    packageName.set("pagination.connectionWithNodes")
    srcDir("src/commonMain/graphql/pagination/connectionWithNodes")
    @OptIn(ApolloExperimental::class)
    generateDataBuilders.set(true)
  }
  service("pagination.connectionProgrammatic") {
    packageName.set("pagination.connectionProgrammatic")
    srcDir("src/commonMain/graphql/pagination/connectionProgrammatic")
    @OptIn(ApolloExperimental::class)
    generateDataBuilders.set(true)
  }
}
