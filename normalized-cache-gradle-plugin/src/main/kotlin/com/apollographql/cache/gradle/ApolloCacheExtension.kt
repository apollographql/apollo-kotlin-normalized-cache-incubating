package com.apollographql.cache.gradle

import com.apollographql.cache.gradle.internal.isKotlinMultiplatform
import com.apollographql.cache.gradle.internal.kotlinProjectExtensionOrThrow
import com.apollographql.cache.gradle.internal.registerGenerateApolloCacheSourcesTask
import org.gradle.api.Action
import org.gradle.api.Project
import javax.inject.Inject

abstract class ApolloCacheExtension @Inject constructor(val project: Project) {
  fun service(serviceName: String, action: Action<ApolloCacheService>) {
    val service = project.objects.newInstance(ApolloCacheService::class.java, serviceName)
    action.execute(service)

    val task = project.registerGenerateApolloCacheSourcesTask(
        taskName = "generate${serviceName.replaceFirstChar(Char::uppercase)}ApolloCacheSources",
        schemaFiles = service.graphqlSourceDirectorySet,
        packageName = service.packageName
    )
    val mainSourceSetName = if (project.isKotlinMultiplatform) "commonMain" else "main"
    project.kotlinProjectExtensionOrThrow.sourceSets.getByName(mainSourceSetName).kotlin.srcDir(task)
  }
}
