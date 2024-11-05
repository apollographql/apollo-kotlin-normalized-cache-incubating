package com.apollographql.cache.normalized

import com.apollographql.apollo.api.CustomScalarAdapters
import com.apollographql.apollo.api.Executable
import com.apollographql.apollo.api.Fragment
import com.apollographql.apollo.api.Operation
import com.apollographql.apollo.api.json.JsonNumber
import com.apollographql.apollo.interceptor.ApolloInterceptor
import com.apollographql.cache.normalized.api.CacheHeaders
import com.apollographql.cache.normalized.api.CacheKey
import com.apollographql.cache.normalized.api.CacheKeyGenerator
import com.apollographql.cache.normalized.api.CacheResolver
import com.apollographql.cache.normalized.api.DefaultEmbeddedFieldsProvider
import com.apollographql.cache.normalized.api.DefaultFieldKeyGenerator
import com.apollographql.cache.normalized.api.DefaultRecordMerger
import com.apollographql.cache.normalized.api.EmbeddedFieldsProvider
import com.apollographql.cache.normalized.api.EmptyMetadataGenerator
import com.apollographql.cache.normalized.api.FieldKeyGenerator
import com.apollographql.cache.normalized.api.FieldPolicyCacheResolver
import com.apollographql.cache.normalized.api.MetadataGenerator
import com.apollographql.cache.normalized.api.NormalizedCache
import com.apollographql.cache.normalized.api.NormalizedCacheFactory
import com.apollographql.cache.normalized.api.Record
import com.apollographql.cache.normalized.api.RecordMerger
import com.apollographql.cache.normalized.api.TypePolicyCacheKeyGenerator
import com.apollographql.cache.normalized.internal.DefaultApolloStore
import com.benasher44.uuid.Uuid
import kotlinx.coroutines.flow.SharedFlow
import kotlin.reflect.KClass

/**
 * ApolloStore exposes a thread-safe api to access a [com.apollographql.cache.normalized.api.NormalizedCache].
 *
 * Note that most operations are synchronous and might block if the underlying cache is doing IO - calling them from the main thread
 * should be avoided.
 */
interface ApolloStore {
  /**
   * Exposes the keys of records that have changed.
   */
  val changedKeys: SharedFlow<Set<String>>

  /**
   * Read GraphQL operation from store.
   * This is a synchronous operation that might block if the underlying cache is doing IO
   *
   * @param operation to be read
   *
   * @throws [com.apollographql.apollo.exception.CacheMissException] on cache miss
   * @throws [com.apollographql.apollo.exception.ApolloException] on other cache read errors
   *
   * @return the operation data with optional headers from the [NormalizedCache]
   */
  fun <D : Operation.Data> readOperation(
      operation: Operation<D>,
      customScalarAdapters: CustomScalarAdapters = CustomScalarAdapters.Empty,
      cacheHeaders: CacheHeaders = CacheHeaders.NONE,
  ): ReadResult<D>

  /**
   * Read a GraphQL fragment from the store.
   * This is a synchronous operation that might block if the underlying cache is doing IO
   *
   * @param fragment to be read
   * @param cacheKey    [CacheKey] to be used to find cache record for the fragment
   *
   * @throws [com.apollographql.apollo.exception.CacheMissException] on cache miss
   * @throws [com.apollographql.apollo.exception.ApolloException] on other cache read errors
   *
   * @return the fragment data with optional headers from the [NormalizedCache]
   */
  fun <D : Fragment.Data> readFragment(
      fragment: Fragment<D>,
      cacheKey: CacheKey,
      customScalarAdapters: CustomScalarAdapters = CustomScalarAdapters.Empty,
      cacheHeaders: CacheHeaders = CacheHeaders.NONE,
  ): ReadResult<D>

  /**
   * Write an operation data to the store.
   * This is a synchronous operation that might block if the underlying cache is doing IO
   *
   * @param operation     [Operation] response data of which should be written to the store
   * @param operationData [Operation.Data] operation response data to be written to the store
   * @return the changed keys
   *
   * @see publish
   */
  fun <D : Operation.Data> writeOperation(
      operation: Operation<D>,
      operationData: D,
      customScalarAdapters: CustomScalarAdapters = CustomScalarAdapters.Empty,
      cacheHeaders: CacheHeaders = CacheHeaders.NONE,
  ): Set<String>

  /**
   * Write a fragment data to the store.
   * This is a synchronous operation that might block if the underlying cache is doing IO
   *
   * @param fragment data to be written to the store
   * @param cacheKey [CacheKey] to be used as root record key
   * @param fragmentData [Fragment.Data] to be written to the store
   * @return the changed keys
   *
   * @see publish
   */
  fun <D : Fragment.Data> writeFragment(
      fragment: Fragment<D>,
      cacheKey: CacheKey,
      fragmentData: D,
      customScalarAdapters: CustomScalarAdapters = CustomScalarAdapters.Empty,
      cacheHeaders: CacheHeaders = CacheHeaders.NONE,
  ): Set<String>

  /**
   * Write operation data to the optimistic store.
   * This is a synchronous operation that might block if the underlying cache is doing IO.
   *
   * @param operation     [Operation] response data of which should be written to the store
   * @param operationData [Operation.Data] operation response data to be written to the store
   * @param mutationId    mutation unique identifier
   * @return the changed keys
   *
   * @see publish
   */
  fun <D : Operation.Data> writeOptimisticUpdates(
      operation: Operation<D>,
      operationData: D,
      mutationId: Uuid,
      customScalarAdapters: CustomScalarAdapters = CustomScalarAdapters.Empty,
  ): Set<String>

  /**
   * Writes a fragment to the optimistic store.
   *
   * This is a synchronous operation that might block if the underlying cache is doing IO.
   *
   * @param fragment the fragment to write
   * @param cacheKey the root where to write the fragment data to
   * @param fragmentData the fragment data to write
   * @param mutationId a unique identifier for this optimistic update
   * @return the changed keys
   *
   * @see publish
   */
  fun <D : Fragment.Data> writeOptimisticUpdates(
      fragment: Fragment<D>,
      cacheKey: CacheKey,
      fragmentData: D,
      mutationId: Uuid,
      customScalarAdapters: CustomScalarAdapters = CustomScalarAdapters.Empty,
  ): Set<String>

  /**
   * Rollback operation data optimistic updates.
   * This is a synchronous operation that might block if the underlying cache is doing IO.
   *
   * @param mutationId mutation unique identifier
   * @return the changed keys
   */
  fun rollbackOptimisticUpdates(
      mutationId: Uuid,
  ): Set<String>

  /**
   * Clear all records from this [ApolloStore].
   * This is a synchronous operation that might block if the underlying cache is doing IO
   *
   * @return `true` if all records were successfully removed, `false` otherwise
   */
  fun clearAll(): Boolean

  /**
   * Remove cache record by the key
   * This is a synchronous operation that might block if the underlying cache is doing IO
   *
   * @param cacheKey of record to be removed
   * @param cascade defines if remove operation is propagated to the referenced entities
   * @return `true` if the record was successfully removed, `false` otherwise
   */
  fun remove(cacheKey: CacheKey, cascade: Boolean = true): Boolean

  /**
   * Remove a list of cache records
   * This is an optimized version of [remove] for caches that can batch operations
   * This is a synchronous operation that might block if the underlying cache is doing IO
   *
   * @param cacheKeys keys of records to be removed
   * @return the number of records that have been removed
   */
  fun remove(cacheKeys: List<CacheKey>, cascade: Boolean = true): Int

  /**
   * Normalize [data] to a map of [Record] keyed by [Record.key].
   */
  fun <D : Operation.Data> normalize(
      operation: Operation<D>,
      data: D,
      customScalarAdapters: CustomScalarAdapters,
  ): Map<String, Record>

  /**
   * @param keys A set of keys of [Record] which have changed.
   */
  suspend fun publish(keys: Set<String>)

  /**
   * Direct access to the cache.
   * This is a synchronous operation that might block if the underlying cache is doing IO.
   *
   * @param block a function that can access the cache.
   */
  fun <R> accessCache(block: (NormalizedCache) -> R): R

  /**
   * Dump the content of the store for debugging purposes.
   * This is a synchronous operation that might block if the underlying cache is doing IO.
   */
  fun dump(): Map<KClass<*>, Map<String, Record>>

  /**
   * Release resources associated with this store.
   */
  fun dispose()

  class ReadResult<D : Executable.Data>(
      val data: D,
      val cacheHeaders: CacheHeaders,
  )
}

fun ApolloStore(
    normalizedCacheFactory: NormalizedCacheFactory,
    cacheKeyGenerator: CacheKeyGenerator = TypePolicyCacheKeyGenerator,
    metadataGenerator: MetadataGenerator = EmptyMetadataGenerator,
    cacheResolver: CacheResolver = FieldPolicyCacheResolver,
    recordMerger: RecordMerger = DefaultRecordMerger,
    fieldKeyGenerator: FieldKeyGenerator = DefaultFieldKeyGenerator,
    embeddedFieldsProvider: EmbeddedFieldsProvider = DefaultEmbeddedFieldsProvider,
): ApolloStore = DefaultApolloStore(
    normalizedCacheFactory = normalizedCacheFactory,
    cacheKeyGenerator = cacheKeyGenerator,
    metadataGenerator = metadataGenerator,
    cacheResolver = cacheResolver,
    recordMerger = recordMerger,
    fieldKeyGenerator = fieldKeyGenerator,
    embeddedFieldsProvider = embeddedFieldsProvider,
)

/**
 * Interface that marks all interceptors added when configuring a `store()` on ApolloClient.Builder.
 */
internal interface ApolloStoreInterceptor : ApolloInterceptor

internal fun ApolloStore.cacheDumpProvider(): () -> Map<String, Map<String, Pair<Int, Map<String, Any?>>>> {
  return {
    dump().map { (cacheClass, cacheRecords) ->
      cacheClass.normalizedCacheName() to cacheRecords.mapValues { (_, record) ->
        record.size to record.fields.mapValues { (_, value) ->
          value.toExternal()
        }
      }
    }.toMap()
  }
}

private fun Any?.toExternal(): Any? {
  return when (this) {
    null -> null
    is String -> this
    is Boolean -> this
    is Int -> this
    is Long -> this
    is Double -> this
    is JsonNumber -> this
    is CacheKey -> this.serialize()
    is List<*> -> {
      map { it.toExternal() }
    }

    is Map<*, *> -> {
      mapValues { it.value.toExternal() }
    }

    else -> error("Unsupported record value type: '$this'")
  }
}

internal expect fun KClass<*>.normalizedCacheName(): String
