package com.apollographql.cache.gradle.internal

import com.apollographql.cache.gradle.internal.codegen.Codegen
import com.apollographql.cache.gradle.internal.codegen.SchemaReader
import gratatouille.GInputFiles
import gratatouille.GOutputDirectory
import gratatouille.GTaskAction

@GTaskAction
internal fun generateApolloCacheSources(
    schemaFiles: GInputFiles,
    packageName: String,
    outputDirectory: GOutputDirectory,
) {
  outputDirectory.deleteRecursively()
  outputDirectory.mkdirs()

  val schemaReader = SchemaReader(schemaFiles)
  val maxAges = schemaReader.getMaxAge()
  Codegen(
      packageName = packageName + ".cache",
      outputDirectory = outputDirectory,
      maxAges = maxAges,
  )
      .generate()
}
