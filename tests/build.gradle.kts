buildscript {
  repositories {
    maven {
      url = uri("https://s01.oss.sonatype.org/content/repositories/snapshots/")
    }
    mavenCentral()
    google()
  }
}

plugins {
  alias(libs.plugins.kotlin.multiplatform).apply(false)
  alias(libs.plugins.apollo).apply(false)
  alias(libs.plugins.apollo.external).apply(false)
}
