package com.apollographql.cache.normalized.internal

import com.apollographql.apollo.api.Adapter
import com.apollographql.apollo.api.CompiledField
import com.apollographql.apollo.api.CompiledFragment
import com.apollographql.apollo.api.CompiledSelection
import com.apollographql.apollo.api.CustomScalarAdapters
import com.apollographql.apollo.api.Executable
import com.apollographql.apollo.api.json.MapJsonReader
import com.apollographql.apollo.exception.CacheMissException
import com.apollographql.cache.normalized.api.ApolloCacheHeaders
import com.apollographql.cache.normalized.api.CacheHeaders
import com.apollographql.cache.normalized.api.CacheKey
import com.apollographql.cache.normalized.api.CacheResolver
import com.apollographql.cache.normalized.api.FieldKeyGenerator
import com.apollographql.cache.normalized.api.ReadOnlyNormalizedCache
import com.apollographql.cache.normalized.api.Record
import com.apollographql.cache.normalized.api.ResolverContext
import kotlin.jvm.JvmSuppressWildcards

/**
 * A resolver that solves the "N+1" problem by batching all SQL queries at a given depth.
 * It respects skip/include directives.
 */
internal class CacheBatchReader(
    private val cache: ReadOnlyNormalizedCache,
    private val rootKey: String,
    private val variables: Executable.Variables,
    private val cacheResolver: CacheResolver,
    private val cacheHeaders: CacheHeaders,
    private val rootSelections: List<CompiledSelection>,
    private val rootField: CompiledField,
    private val fieldKeyGenerator: FieldKeyGenerator,
) {
  /**
   * @param key: the key of the record we need to fetch
   * @param path: the path where this pending reference needs to be inserted
   */
  class PendingReference(
      val key: String,
      val path: List<Any>,
      val fieldPath: List<CompiledField>,
      val selections: List<CompiledSelection>,
      val parentType: String,
  )

  /**
   * The objects read from the cache with only the fields that are selected and maybe some values changed
   * The key is the path to the object
   */
  private val data = mutableMapOf<List<Any>, Map<String, Any?>>()

  /**
   * True if at least one of the resolved fields is stale
   */
  private var isStale = false

  private val pendingReferences = mutableListOf<PendingReference>()

  private class CollectState(val variables: Executable.Variables) {
    val fields = mutableListOf<CompiledField>()
  }

  /**
   *
   */
  private fun collect(selections: List<CompiledSelection>, parentType: String, typename: String?, state: CollectState) {
    selections.forEach { compiledSelection ->
      when (compiledSelection) {
        is CompiledField -> {
          state.fields.add(compiledSelection)
        }

        is CompiledFragment -> {
          if ((typename in compiledSelection.possibleTypes || compiledSelection.typeCondition == parentType) && !compiledSelection.shouldSkip(state.variables.valueMap)) {
            collect(compiledSelection.selections, parentType, typename, state)
          }
        }
      }
    }
  }

  private fun collectAndMergeSameDirectives(
      selections: List<CompiledSelection>,
      parentType: String,
      variables: Executable.Variables,
      typename: String?,
  ): List<CompiledField> {
    val state = CollectState(variables)
    collect(selections, parentType, typename, state)
    return state.fields.groupBy { (it.responseName) to it.condition }.values.map {
      it.first().newBuilder().selections(it.flatMap { it.selections }).build()
    }
  }

  fun collectData(): CacheBatchReaderData {
    pendingReferences.add(
        PendingReference(
            key = rootKey,
            selections = rootSelections,
            parentType = rootField.type.rawType().name,
            path = emptyList(),
            fieldPath = listOf(rootField),
        )
    )

    while (pendingReferences.isNotEmpty()) {
      val records = cache.loadRecords(pendingReferences.map { it.key }, cacheHeaders).associateBy { it.key }

      val copy = pendingReferences.toList()
      pendingReferences.clear()
      copy.forEach { pendingReference ->
        var record = records[pendingReference.key]
        if (record == null) {
          if (pendingReference.key == CacheKey.rootKey().key) {
            // This happens the very first time we read the cache
            record = Record(pendingReference.key, emptyMap())
          } else {
            throw CacheMissException(pendingReference.key)
          }
        }

        val collectedFields =
          collectAndMergeSameDirectives(pendingReference.selections, pendingReference.parentType, variables, record["__typename"] as? String)

        val map = collectedFields.mapNotNull {
          if (it.shouldSkip(variables.valueMap)) {
            return@mapNotNull null
          }

          val value = cacheResolver.resolveField(
              ResolverContext(
                  field = it,
                  variables = variables,
                  parent = record,
                  parentKey = record.key,
                  parentType = pendingReference.parentType,
                  cacheHeaders = cacheHeaders,
                  fieldKeyGenerator = fieldKeyGenerator,
                  path = pendingReference.fieldPath + it,
              )
          ).unwrap()
          value.registerCacheKeys(pendingReference.path + it.responseName, pendingReference.fieldPath + it, it.selections, it.type.rawType().name)

          it.responseName to value
        }.toMap()

        data[pendingReference.path] = map
      }
    }

    return CacheBatchReaderData(data, CacheHeaders.Builder().apply { if (isStale) addHeader(ApolloCacheHeaders.STALE, "true") }.build())
  }

  private fun Any?.unwrap(): Any? {
    return when (this) {
      is CacheResolver.ResolvedValue -> {
        if (cacheHeaders.headerValue(ApolloCacheHeaders.STALE) == "true") {
          isStale = true
        }
        this.value
      }

      else -> {
        this
      }
    }
  }

  /**
   * The path leading to this value
   */
  private fun Any?.registerCacheKeys(
      path: List<Any>,
      fieldPath: List<CompiledField>,
      selections: List<CompiledSelection>,
      parentType: String,
  ) {
    when (this) {
      is CacheKey -> {
        pendingReferences.add(
            PendingReference(
                key = key,
                selections = selections,
                parentType = parentType,
                path = path,
                fieldPath = fieldPath,
            )
        )
      }

      is List<*> -> {
        forEachIndexed { index, value ->
          value.registerCacheKeys(path + index, fieldPath, selections, parentType)
        }
      }

      is Map<*, *> -> {
        @Suppress("UNCHECKED_CAST")
        this as Map<String, @JvmSuppressWildcards Any?>
        val collectedFields = collectAndMergeSameDirectives(selections, parentType, variables, get("__typename") as? String)
        collectedFields.mapNotNull {
          if (it.shouldSkip(variables.valueMap)) {
            return@mapNotNull null
          }

          val value = cacheResolver.resolveField(
              ResolverContext(
                  field = it,
                  variables = variables,
                  parent = this,
                  parentKey = "",
                  parentType = parentType,
                  cacheHeaders = cacheHeaders,
                  fieldKeyGenerator = fieldKeyGenerator,
                  path = fieldPath + it,
              )
          ).unwrap()
          value.registerCacheKeys(path + it.responseName, fieldPath + it, it.selections, it.type.rawType().name)
        }
      }
    }
  }

  internal class CacheBatchReaderData(
      private val data: Map<List<Any>, Map<String, Any?>>,
      val cacheHeaders: CacheHeaders,
  ) {
    fun <D : Executable.Data> toData(
        adapter: Adapter<D>,
        customScalarAdapters: CustomScalarAdapters,
        variables: Executable.Variables,
    ): D {
      val reader = MapJsonReader(toMap())
      return adapter.fromJson(
          reader,
          customScalarAdapters.newBuilder().falseVariables(variables.valueMap.filter { it.value == false }.keys).build()
      )
    }

    @Suppress("UNCHECKED_CAST")
    private fun toMap(): Map<String, Any?> {
      return data[emptyList()].replaceCacheKeys(emptyList()) as Map<String, Any?>
    }

    private fun Any?.replaceCacheKeys(path: List<Any>): Any? {
      return when (this) {
        is CacheKey -> {
          data[path].replaceCacheKeys(path)
        }

        is List<*> -> {
          mapIndexed { index, src ->
            src.replaceCacheKeys(path + index)
          }
        }

        is Map<*, *> -> {
          // This will traverse Map custom scalars but this is ok as it shouldn't contain any CacheKey
          mapValues {
            it.value.replaceCacheKeys(path + (it.key as String))
          }
        }

        else -> {
          // Scalar value
          this
        }
      }
    }
  }
}
