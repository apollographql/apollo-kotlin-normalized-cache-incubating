package com.apollographql.cache.gradle.internal.codegen

import com.apollographql.apollo.annotations.ApolloExperimental
import com.apollographql.apollo.ast.GQLBooleanValue
import com.apollographql.apollo.ast.GQLDirective
import com.apollographql.apollo.ast.GQLDocument
import com.apollographql.apollo.ast.GQLIntValue
import com.apollographql.apollo.ast.GQLInterfaceTypeDefinition
import com.apollographql.apollo.ast.GQLObjectTypeDefinition
import com.apollographql.apollo.ast.GQLSchemaDefinition
import com.apollographql.apollo.ast.GQLStringValue
import com.apollographql.apollo.ast.GQLTypeDefinition
import com.apollographql.apollo.ast.Schema
import com.apollographql.apollo.ast.SourceLocation
import com.apollographql.apollo.ast.pretty
import com.apollographql.apollo.ast.toGQLDocument
import com.apollographql.apollo.ast.validateAsSchema
import gratatouille.FileWithPath
import org.gradle.api.logging.Logging
import java.io.File

private const val CACHE_CONTROL = "cacheControl"
private const val CACHE_CONTROL_FIELD = "cacheControlField"

internal class SchemaReader(
    private val schemaFiles: Collection<FileWithPath>,
) {
  /*
   * Taken from ApolloCompiler.buildCodegenSchema(), with error handling removed as errors will already be handled by it.
   */
  private fun getSchema(): Schema {
    val schemaDocuments = schemaFiles.map {
      it.normalizedPath to it.file.toGQLDocument(allowJson = true)
    }
    // Locate the mainSchemaDocument. It's the one that contains the operation roots
    val mainSchemaDocuments = mutableListOf<GQLDocument>()
    val otherSchemaDocuments = mutableListOf<GQLDocument>()
    schemaDocuments.forEach {
      val document = it.second
      if (
          document.definitions.filterIsInstance<GQLSchemaDefinition>().isNotEmpty()
          || document.definitions.filterIsInstance<GQLTypeDefinition>().any { it.name == "Query" }
      ) {
        mainSchemaDocuments.add(document)
      } else {
        otherSchemaDocuments.add(document)
      }
    }
    val mainSchemaDocument = mainSchemaDocuments.single()

    // Sort the other schema document as type extensions are order sensitive
    val otherSchemaDocumentSorted = otherSchemaDocuments.sortedBy { it.sourceLocation?.filePath?.substringAfterLast(File.pathSeparator) }
    val schemaDefinitions = (listOf(mainSchemaDocument) + otherSchemaDocumentSorted).flatMap { it.definitions }
    val schemaDocument = GQLDocument(
        definitions = schemaDefinitions,
        sourceLocation = null
    )

    @OptIn(ApolloExperimental::class)
    val result = schemaDocument.validateAsSchema()
    return result.value!!
  }

  fun getMaxAge(): Map<String, Int> {
    val schema = getSchema()
    val typeDefinitions = schema.typeDefinitions
    val issues = mutableListOf<Issue>()
    fun GQLDirective.maxAgeAndInherit(): Pair<Int?, Boolean> {
      val maxAge = (arguments.firstOrNull { it.name == "maxAge" }?.value as? GQLIntValue)?.value?.toIntOrNull()
      if (maxAge != null && maxAge < 0) {
        issues += Issue("`maxAge` must not be negative", sourceLocation)
        return null to false
      }
      val inheritMaxAge = (arguments.firstOrNull { it.name == "inheritMaxAge" }?.value as? GQLBooleanValue)?.value == true
      if (maxAge == null && !inheritMaxAge || maxAge != null && inheritMaxAge) {
        issues += Issue("`@$name` must either provide a `maxAge` or an `inheritMaxAge` set to true", sourceLocation)
        return null to false
      }
      return maxAge to inheritMaxAge
    }

    val maxAges = mutableMapOf<String, Int>()
    for (typeDefinition in typeDefinitions.values) {
      val typeCacheControlDirective = typeDefinition.directives.firstOrNull { schema.originalDirectiveName(it.name) == CACHE_CONTROL }
      if (typeCacheControlDirective != null) {
        val (maxAge, inheritMaxAge) = typeCacheControlDirective.maxAgeAndInherit()
        if (maxAge != null) {
          maxAges[typeDefinition.name] = maxAge
        } else if (inheritMaxAge) {
          maxAges[typeDefinition.name] = -1
        }
      }

      val typeCacheControlFieldDirectives =
        typeDefinition.directives.filter { schema.originalDirectiveName(it.name) == CACHE_CONTROL_FIELD }
      for (fieldDirective in typeCacheControlFieldDirectives) {
        val fieldName = (fieldDirective.arguments.first { it.name == "name" }.value as GQLStringValue).value
        if (typeDefinition.fields.none { it.name == fieldName }) {
          issues += Issue("Field `$fieldName` does not exist on type `${typeDefinition.name}`", fieldDirective.sourceLocation)
          continue
        }
        val (maxAge, inheritMaxAge) = fieldDirective.maxAgeAndInherit()
        if (maxAge != null) {
          maxAges["${typeDefinition.name}.$fieldName"] = maxAge
        } else if (inheritMaxAge) {
          maxAges["${typeDefinition.name}.$fieldName"] = -1
        }
      }

      for (field in typeDefinition.fields) {
        val fieldCacheControlDirective = field.directives.firstOrNull { schema.originalDirectiveName(it.name) == CACHE_CONTROL }
        if (fieldCacheControlDirective != null) {
          val (maxAge, inheritMaxAge) = fieldCacheControlDirective.maxAgeAndInherit()
          if (maxAge != null) {
            maxAges["${typeDefinition.name}.${field.name}"] = maxAge
          } else if (inheritMaxAge) {
            maxAges["${typeDefinition.name}.${field.name}"] = -1
          }
        }
      }
    }
    if (issues.isNotEmpty()) {
      for (issue in issues) {
        issue.log()
      }
      throw IllegalStateException("Found issues in the schema")
    }
    return maxAges
  }
}

private class Issue(
    val message: String,
    val sourceLocation: SourceLocation?,
) {
  fun log() {
    Logging.getLogger("apollo").lifecycle("w: ${sourceLocation.pretty()}: Apollo: ${message}")
  }
}

private val GQLTypeDefinition.fields
  get() = when (this) {
    is GQLObjectTypeDefinition -> fields
    is GQLInterfaceTypeDefinition -> fields
    else -> emptyList()
  }
