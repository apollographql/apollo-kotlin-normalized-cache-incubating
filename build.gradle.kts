import com.gradleup.librarian.core.librarianRoot

buildscript {
  repositories {
    mavenCentral()
  }
  dependencies {
    classpath("build-logic:build-logic")
  }
}

librarianRoot()
