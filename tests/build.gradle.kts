buildscript {
  repositories {
    mavenCentral()
    google()
  }
}

plugins {
  alias(libs.plugins.kotlin.multiplatform).apply(false)
  alias(libs.plugins.apollo).apply(false)
  alias(libs.plugins.apollo.cache).apply(false)
}
