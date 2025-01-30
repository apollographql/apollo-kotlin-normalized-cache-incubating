@file:OptIn(ApolloExperimental::class)

package com.apollographql.cache.apollocompilerplugin

import com.apollographql.apollo.annotations.ApolloExperimental
import com.apollographql.apollo.ast.ForeignSchema
import com.apollographql.apollo.ast.Schema
import com.apollographql.apollo.compiler.ApolloCompilerPlugin
import com.apollographql.apollo.compiler.ApolloCompilerPluginEnvironment
import com.apollographql.apollo.compiler.ApolloCompilerPluginLogger
import com.apollographql.apollo.compiler.ApolloCompilerPluginProvider
import com.apollographql.apollo.compiler.SchemaListener
import com.apollographql.cache.apollocompilerplugin.internal.Codegen
import com.apollographql.cache.apollocompilerplugin.internal.cacheControlGQLDefinitions
import com.apollographql.cache.apollocompilerplugin.internal.getMaxAges
import java.io.File

class ApolloCacheCompilerPluginProvider : ApolloCompilerPluginProvider {
  override fun create(environment: ApolloCompilerPluginEnvironment): ApolloCompilerPlugin {
    return ApolloCacheCompilerPlugin(
        logger = environment.logger,
        packageName = environment.arguments["packageName"] as? String
            ?: throw IllegalArgumentException("packageName argument is required and must be a String"),
        generateSchema = when (val generateSchema = environment.arguments["generateSchema"]) {
          null -> false
          is Boolean -> generateSchema
          else -> throw IllegalArgumentException("generateSchema argument must be a Boolean")
        }
    )
  }
}

class ApolloCacheCompilerPlugin(
    private val logger: ApolloCompilerPluginLogger,
    private val packageName: String,
    private val generateSchema: Boolean,
) : ApolloCompilerPlugin {
  override fun foreignSchemas(): List<ForeignSchema> {
    return listOf(
        ForeignSchema("cache", "v0.1", cacheControlGQLDefinitions)
    )
  }

  @ApolloExperimental
  override fun schemaListener(): SchemaListener {
    return object : SchemaListener {
      override fun onSchema(schema: Schema, outputDirectory: File) {
        val maxAges = schema.getMaxAges(logger)
        Codegen(
            packageName = "$packageName.cache",
            outputDirectory = outputDirectory,
            maxAges = maxAges,
            schema = schema.takeIf { generateSchema },
        ).generate()
      }
    }
  }
}
