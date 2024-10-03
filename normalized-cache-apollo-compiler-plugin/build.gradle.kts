import com.gradleup.librarian.gradle.librarianModule

plugins {
  alias(libs.plugins.kotlin.jvm)
}

librarianModule(true)

dependencies {
  implementation(libs.apollo.compiler)
  implementation(libs.apollo.ast)
  implementation(libs.kotlin.poet)
  testImplementation(libs.kotlin.test)
}
