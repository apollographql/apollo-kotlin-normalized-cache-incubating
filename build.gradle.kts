import com.gradleup.librarian.gradle.Librarian

plugins {
  id("build.logic").apply(false)
}

Librarian.root(project)
