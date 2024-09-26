package com.apollographql.cache.gradle

import org.gradle.api.Project
import org.gradle.api.provider.Property
import javax.inject.Inject

abstract class ApolloCacheService @Inject constructor(
    private val name: String,
    project: Project,
) {
  abstract val packageName: Property<String>

  internal val graphqlSourceDirectorySet = project.objects.sourceDirectorySet("graphql", "graphql")

  fun srcDir(directory: Any) {
    graphqlSourceDirectorySet.srcDir(directory)
  }
}
