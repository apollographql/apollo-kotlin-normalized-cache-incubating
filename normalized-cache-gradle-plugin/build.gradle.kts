import com.gradleup.librarian.gradle.librarianModule

plugins {
  alias(libs.plugins.kotlin.jvm)
  id("java-gradle-plugin")
}

librarianModule(true)

dependencies {
  compileOnly(libs.kgp.min)
  compileOnly(libs.gradle.api.min)
  implementation(libs.kotlin.poet)
  implementation(libs.apollo.ast)
  testImplementation(libs.kotlin.test)
}

gradlePlugin {
  plugins {
    create("com.apollographql.cache") {
      id = "com.apollographql.cache"
      displayName = "com.apollographql.cache"
      description = "Apollo Normalized Cache Gradle plugin"
      implementationClass = "com.apollographql.cache.gradle.ApolloCachePlugin"
    }
  }
}
