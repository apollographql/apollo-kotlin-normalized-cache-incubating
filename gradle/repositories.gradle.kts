listOf(pluginManagement.repositories, dependencyResolutionManagement.repositories).forEach {
  it.apply {
//    maven {
//      url = uri("https://s01.oss.sonatype.org/content/repositories/snapshots/")
//    }
    mavenCentral()
    google()
    gradlePluginPortal()
  }
}
