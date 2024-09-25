package com.apollographql.cache.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ExternalDependency

abstract class ApolloCachePlugin : Plugin<Project> {
  override fun apply(target: Project) {
    target.configureDefaultVersionsResolutionStrategy()
  }

  private fun Project.configureDefaultVersionsResolutionStrategy() {
    configurations.configureEach { configuration ->
      configuration.withDependencies { dependencySet ->
        val pluginVersion = VERSION
        dependencySet.filterIsInstance<ExternalDependency>()
            .filter { it.group == "com.apollographql.cache" && it.version.isNullOrEmpty() }
            .forEach { it.version { constraint -> constraint.require(pluginVersion) } }
      }
    }
  }
}
