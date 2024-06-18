import com.gradleup.librarian.core.librarianRoot

buildscript {
  repositories {
    mavenCentral()
    google()
  }
  dependencies {
    classpath("build-logic:build-logic")
  }
}

librarianRoot()
