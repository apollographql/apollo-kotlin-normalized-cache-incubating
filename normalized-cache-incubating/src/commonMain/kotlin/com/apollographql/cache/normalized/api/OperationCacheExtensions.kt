package com.apollographql.cache.normalized.api

import com.apollographql.apollo.api.Adapter
import com.apollographql.apollo.api.CustomScalarAdapters
import com.apollographql.apollo.api.Executable
import com.apollographql.apollo.api.Operation
import com.apollographql.apollo.api.json.MapJsonReader
import com.apollographql.apollo.api.json.MapJsonWriter
import com.apollographql.apollo.api.variables
import com.apollographql.cache.normalized.internal.CacheBatchReader
import com.apollographql.cache.normalized.internal.CacheBatchReader.CacheBatchReaderData
import com.apollographql.cache.normalized.internal.Normalizer
import kotlin.jvm.JvmOverloads

fun <D : Operation.Data> Operation<D>.normalize(
    data: D,
    customScalarAdapters: CustomScalarAdapters,
    cacheKeyGenerator: CacheKeyGenerator,
    metadataGenerator: MetadataGenerator = EmptyMetadataGenerator,
    fieldKeyGenerator: FieldKeyGenerator = DefaultFieldKeyGenerator,
    embeddedFieldsProvider: EmbeddedFieldsProvider = DefaultEmbeddedFieldsProvider,
) =
  normalize(data, customScalarAdapters, cacheKeyGenerator, metadataGenerator, fieldKeyGenerator, embeddedFieldsProvider, CacheKey.rootKey().key)

fun <D : Executable.Data> Executable<D>.normalize(
    data: D,
    customScalarAdapters: CustomScalarAdapters,
    cacheKeyGenerator: CacheKeyGenerator,
    metadataGenerator: MetadataGenerator = EmptyMetadataGenerator,
    fieldKeyGenerator: FieldKeyGenerator = DefaultFieldKeyGenerator,
    embeddedFieldsProvider: EmbeddedFieldsProvider = DefaultEmbeddedFieldsProvider,
    rootKey: String,
): Map<String, Record> {
  val writer = MapJsonWriter()
  adapter().toJson(writer, customScalarAdapters, data)
  val variables = variables(customScalarAdapters, withDefaultValues = true)
  @Suppress("UNCHECKED_CAST")
  return Normalizer(variables, rootKey, cacheKeyGenerator, metadataGenerator, fieldKeyGenerator, embeddedFieldsProvider)
      .normalize(writer.root() as Map<String, Any?>, rootField().selections, rootField().type.rawType())
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
  ).toData(adapter(), customScalarAdapters, variables)
}

internal fun <D : Executable.Data> Executable<D>.readDataFromCacheInternal(
    cacheKey: CacheKey,
    cache: ReadOnlyNormalizedCache,
    cacheResolver: CacheResolver,
    cacheHeaders: CacheHeaders,
    variables: Executable.Variables,
    fieldKeyGenerator: FieldKeyGenerator,
): CacheBatchReaderData = readInternal(
    cacheKey = cacheKey,
    cache = cache,
    cacheResolver = cacheResolver,
    cacheHeaders = cacheHeaders,
    variables = variables,
    fieldKeyGenerator = fieldKeyGenerator,
)


private fun <D : Executable.Data> Executable<D>.readInternal(
    cacheKey: CacheKey,
    cache: ReadOnlyNormalizedCache,
    cacheResolver: CacheResolver,
    cacheHeaders: CacheHeaders,
    variables: Executable.Variables,
    fieldKeyGenerator: FieldKeyGenerator,
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
  ).collectData()
}

fun Collection<Record>?.dependentKeys(): Set<String> {
  return this?.flatMap {
    it.fieldKeys()
  }?.toSet() ?: emptySet()
}

internal fun <D : Executable.Data> CacheBatchReaderData.toData(
    adapter: Adapter<D>,
    customScalarAdapters: CustomScalarAdapters,
    variables: Executable.Variables,
): D {
  val reader = MapJsonReader(
      root = toMap(),
  )

  return adapter.fromJson(reader, customScalarAdapters.newBuilder().falseVariables(variables.valueMap.filter { it.value == false }.keys)
      .build()
  )
}
