import com.gradleup.librarian.gradle.librarianRoot

plugins {
  id("build.logic") apply false

  alias(libs.plugins.kotlin.multiplatform).apply(false)
  alias(libs.plugins.kotlin.jvm).apply(false)
  alias(libs.plugins.android).apply(false)
  alias(libs.plugins.librarian).apply(false)
  alias(libs.plugins.atomicfu).apply(false)
  alias(libs.plugins.sqldelight).apply(false)
}

librarianRoot()
