import com.gradleup.librarian.gradle.librarianRoot

plugins {
  alias(libs.plugins.kotlin).apply(false)
  alias(libs.plugins.android).apply(false)
  alias(libs.plugins.librarian).apply(false)
  alias(libs.plugins.atomicfu).apply(false)
  alias(libs.plugins.sqldelight).apply(false)
}

librarianRoot()
