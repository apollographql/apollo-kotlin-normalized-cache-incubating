package com.apollographql.cache.gradle

import com.apollographql.cache.gradle.internal.codegen.Codegen
import com.apollographql.cache.gradle.internal.codegen.SchemaReader
import com.apollographql.cache.gradle.internal.codegen.toInputFiles
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

abstract class GenerateApolloCacheSourcesTask : DefaultTask() {
  @get:InputFiles
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val schemaFiles: ConfigurableFileCollection

  @get:Input
  abstract val packageName: Property<String>

  @get:OutputDirectory
  abstract val outputDirectory: DirectoryProperty

  @TaskAction
  fun taskAction() {
    val outputDir = outputDirectory.asFile.get()
    outputDir.apply {
      deleteRecursively()
      mkdirs()
    }

    val schemaReader = SchemaReader(schemaFiles.toInputFiles())
    val maxAges = schemaReader.getMaxAge()
    Codegen(
        packageName = packageName.get() + ".cache",
        outputDirectory = outputDir,
        maxAges = maxAges,
    )
        .generate()
  }
}
