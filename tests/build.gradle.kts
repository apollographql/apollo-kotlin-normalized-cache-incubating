buildscript {
  repositories {
    mavenCentral()
    google()
  }
}

plugins {
  alias(libs.plugins.kotlin).apply(false)
  alias(libs.plugins.apollo).apply(false)
}
