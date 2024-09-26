package com.apollographql.cache.gradle

import com.apollographql.cache.gradle.internal.isKotlinMultiplatform
import com.apollographql.cache.gradle.internal.kotlinProjectExtensionOrThrow
import org.gradle.api.Action
import org.gradle.api.Project
import javax.inject.Inject

abstract class ApolloCacheExtension @Inject constructor(val project: Project) {
  fun service(serviceName: String, action: Action<ApolloCacheService>) {
    val service = project.objects.newInstance(ApolloCacheService::class.java, serviceName)
    action.execute(service)

    val task =
      project.tasks.register("generate${serviceName.replaceFirstChar(Char::uppercase)}ApolloCacheSources", GenerateApolloCacheSourcesTask::class.java) {
        it.packageName.set(service.packageName)
        it.schemaFiles.from(service.graphqlSourceDirectorySet)
        it.outputDirectory.set(project.layout.buildDirectory.dir("generated/source/apollo-cache/${serviceName}"))
      }

    val mainSourceSetName = if (project.isKotlinMultiplatform) "commonMain" else "main"
    project.kotlinProjectExtensionOrThrow.sourceSets.getByName(mainSourceSetName).kotlin.srcDir(task)
  }
}
