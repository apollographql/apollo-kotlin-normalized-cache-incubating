package com.apollographql.cache.normalized.api

import com.apollographql.apollo.api.CustomScalarAdapters
import com.apollographql.apollo.api.Error
import com.apollographql.apollo.api.Executable
import com.apollographql.apollo.api.Operation
import com.apollographql.apollo.api.json.MapJsonWriter
import com.apollographql.apollo.api.variables
import com.apollographql.cache.normalized.internal.CacheBatchReader
import com.apollographql.cache.normalized.internal.CacheBatchReader.CacheBatchReaderData
import com.apollographql.cache.normalized.internal.Normalizer
import kotlin.jvm.JvmOverloads

fun <D : Operation.Data> Operation<D>.normalize(
    data: D,
    errors: List<Error>? = null,
    customScalarAdapters: CustomScalarAdapters = CustomScalarAdapters.Empty,
    cacheKeyGenerator: CacheKeyGenerator = TypePolicyCacheKeyGenerator,
    metadataGenerator: MetadataGenerator = EmptyMetadataGenerator,
    fieldKeyGenerator: FieldKeyGenerator = DefaultFieldKeyGenerator,
    embeddedFieldsProvider: EmbeddedFieldsProvider = DefaultEmbeddedFieldsProvider,
) =
  normalize(
      data = data,
      rootKey = CacheKey.rootKey().key,
      errors = errors,
      customScalarAdapters = customScalarAdapters,
      cacheKeyGenerator = cacheKeyGenerator,
      metadataGenerator = metadataGenerator,
      fieldKeyGenerator = fieldKeyGenerator,
      embeddedFieldsProvider = embeddedFieldsProvider
  )

fun <D : Executable.Data> Executable<D>.normalize(
    data: D,
    rootKey: String,
    errors: List<Error>? = null,
    customScalarAdapters: CustomScalarAdapters = CustomScalarAdapters.Empty,
    cacheKeyGenerator: CacheKeyGenerator = TypePolicyCacheKeyGenerator,
    metadataGenerator: MetadataGenerator = EmptyMetadataGenerator,
    fieldKeyGenerator: FieldKeyGenerator = DefaultFieldKeyGenerator,
    embeddedFieldsProvider: EmbeddedFieldsProvider = DefaultEmbeddedFieldsProvider,
): Map<String, Record> {
  val writer = MapJsonWriter()
  adapter().toJson(writer, customScalarAdapters, data)
  val variables = variables(customScalarAdapters, withDefaultValues = true)

  @Suppress("UNCHECKED_CAST")
  val dataWithErrors = (writer.root() as Map<String, Any?>).withErrors(errors)
  return Normalizer(variables, rootKey, cacheKeyGenerator, metadataGenerator, fieldKeyGenerator, embeddedFieldsProvider)
      .normalize(dataWithErrors, rootField().selections, rootField().type.rawType())
}

/**
 * Returns this data with the given [errors] inlined.
 */
private fun Map<String, Any?>.withErrors(errors: List<Error>?): Map<String, Any?> {
  if (errors == null || errors.isEmpty()) return this
  var dataWithErrors = this
  for (error in errors) {
    val path = error.path
    if (path == null) continue
    dataWithErrors = dataWithErrors.withValueAt(path, error)
  }
  return dataWithErrors
}

@Suppress("UNCHECKED_CAST")
private fun Map<String, Any?>.withValueAt(path: List<Any>, value: Any?): Map<String, Any?> {
  var node: Any? = this.toMutableMap()
  var root = node
  for ((i, key) in path.withIndex()) {
    if (key is String) {
      node as MutableMap<String, Any?>
      if (i == path.lastIndex) {
        node[key] = value
      } else {
        when (val value = node[key]) {
          is Map<*, *> -> {
            node[key] = value.toMutableMap()
          }

          is List<*> -> {
            node[key] = value.toMutableList()
          }

          else -> break
        }
      }
      node = node[key]!!
    } else {
      key as Int
      node as MutableList<Any?>
      if (i == path.lastIndex) {
        node[key] = value
      } else {
        when (val value = node[key]) {
          is Map<*, *> -> {
            node[key] = value.toMutableMap()
          }

          is List<*> -> {
            node[key] = value.toMutableList()
          }

          else -> break
        }
      }
    }
  }
  return root as Map<String, Any?>
}

@JvmOverloads
fun <D : Executable.Data> Executable<D>.readDataFromCache(
    customScalarAdapters: CustomScalarAdapters,
    cache: ReadOnlyNormalizedCache,
    cacheResolver: CacheResolver,
    cacheHeaders: CacheHeaders,
    fieldKeyGenerator: FieldKeyGenerator = DefaultFieldKeyGenerator,
): D {
  val variables = variables(customScalarAdapters, true)
  return readInternal(
      cacheKey = CacheKey.rootKey(),
      cache = cache,
      cacheResolver = cacheResolver,
      cacheHeaders = cacheHeaders,
      variables = variables,
      fieldKeyGenerator = fieldKeyGenerator,
      returnPartialResponses = false,
  ).toData(adapter(), customScalarAdapters, variables)
}

@JvmOverloads
fun <D : Executable.Data> Executable<D>.readDataFromCache(
    cacheKey: CacheKey,
    customScalarAdapters: CustomScalarAdapters,
    cache: ReadOnlyNormalizedCache,
    cacheResolver: CacheResolver,
    cacheHeaders: CacheHeaders,
    fieldKeyGenerator: FieldKeyGenerator = DefaultFieldKeyGenerator,
): D {
  val variables = variables(customScalarAdapters, true)
  return readInternal(
      cacheKey = cacheKey,
      cache = cache,
      cacheResolver = cacheResolver,
      cacheHeaders = cacheHeaders,
      variables = variables,
      fieldKeyGenerator = fieldKeyGenerator,
      returnPartialResponses = false,
  ).toData(adapter(), customScalarAdapters, variables)
}

internal fun <D : Executable.Data> Executable<D>.readDataFromCacheInternal(
    cacheKey: CacheKey,
    cache: ReadOnlyNormalizedCache,
    cacheResolver: CacheResolver,
    cacheHeaders: CacheHeaders,
    variables: Executable.Variables,
    fieldKeyGenerator: FieldKeyGenerator,
    returnPartialResponses: Boolean,
): CacheBatchReaderData = readInternal(
    cacheKey = cacheKey,
    cache = cache,
    cacheResolver = cacheResolver,
    cacheHeaders = cacheHeaders,
    variables = variables,
    fieldKeyGenerator = fieldKeyGenerator,
    returnPartialResponses = returnPartialResponses,
)


private fun <D : Executable.Data> Executable<D>.readInternal(
    cacheKey: CacheKey,
    cache: ReadOnlyNormalizedCache,
    cacheResolver: CacheResolver,
    cacheHeaders: CacheHeaders,
    variables: Executable.Variables,
    fieldKeyGenerator: FieldKeyGenerator,
    returnPartialResponses: Boolean,
): CacheBatchReaderData {
  return CacheBatchReader(
      cache = cache,
      cacheHeaders = cacheHeaders,
      cacheResolver = cacheResolver,
      variables = variables,
      rootKey = cacheKey.key,
      rootSelections = rootField().selections,
      rootField = rootField(),
      fieldKeyGenerator = fieldKeyGenerator,
      returnPartialResponses = returnPartialResponses,
  ).collectData()
}

fun Collection<Record>?.dependentKeys(): Set<String> {
  return this?.flatMap {
    it.fieldKeys()
  }?.toSet() ?: emptySet()
}
