package com.apollographql.cache.normalized

import com.apollographql.apollo.api.ApolloResponse
import com.apollographql.apollo.api.CustomScalarAdapters
import com.apollographql.apollo.api.Error
import com.apollographql.apollo.api.Executable
import com.apollographql.apollo.api.Fragment
import com.apollographql.apollo.api.Operation
import com.apollographql.cache.normalized.ApolloStore.ReadResult
import com.apollographql.cache.normalized.api.CacheHeaders
import com.apollographql.cache.normalized.api.CacheKey
import com.apollographql.cache.normalized.api.DataWithErrors
import com.apollographql.cache.normalized.api.NormalizedCache
import com.apollographql.cache.normalized.api.Record
import com.benasher44.uuid.Uuid
import kotlinx.coroutines.flow.SharedFlow
import kotlin.reflect.KClass

/**
 * A wrapper around [ApolloStore] that provides a simplified API for reading and writing data.
 */
class SimpleApolloStore(
    private val apolloStore: ApolloStore,
    private val customScalarAdapters: CustomScalarAdapters,
) {
  /**
   * @see ApolloStore.changedKeys
   */
  val changedKeys: SharedFlow<Set<String>>
    get() = apolloStore.changedKeys

  /**
   * @see ApolloStore.readOperation
   */
  fun <D : Operation.Data> readOperation(
      operation: Operation<D>,
      cacheHeaders: CacheHeaders = CacheHeaders.NONE,
  ): ApolloResponse<D> = apolloStore.readOperation(
      operation = operation,
      cacheHeaders = cacheHeaders,
      customScalarAdapters = customScalarAdapters,
  )

  /**
   * @see ApolloStore.readFragment
   */
  fun <D : Fragment.Data> readFragment(
      fragment: Fragment<D>,
      cacheKey: CacheKey,
      cacheHeaders: CacheHeaders = CacheHeaders.NONE,
  ): ReadResult<D> = apolloStore.readFragment(
      fragment = fragment,
      cacheKey = cacheKey,
      customScalarAdapters = customScalarAdapters,
      cacheHeaders = cacheHeaders,
  )

  /**
   * @see ApolloStore.writeOperation
   */
  fun <D : Operation.Data> writeOperation(
      operation: Operation<D>,
      data: D,
      errors: List<Error>? = null,
      cacheHeaders: CacheHeaders = CacheHeaders.NONE,
  ): Set<String> = apolloStore.writeOperation(
      operation = operation,
      data = data,
      errors = errors,
      cacheHeaders = cacheHeaders,
      customScalarAdapters = customScalarAdapters,
  )

  /**
   * @see ApolloStore.writeFragment
   */
  fun <D : Operation.Data> writeOperation(
      operation: Operation<D>,
      dataWithErrors: DataWithErrors,
      cacheHeaders: CacheHeaders = CacheHeaders.NONE,
  ): Set<String> = apolloStore.writeOperation(
      operation = operation,
      dataWithErrors = dataWithErrors,
      cacheHeaders = cacheHeaders,
      customScalarAdapters = customScalarAdapters,
  )

  /**
   * @see ApolloStore.writeFragment
   */
  fun <D : Fragment.Data> writeFragment(
      fragment: Fragment<D>,
      cacheKey: CacheKey,
      data: D,
      cacheHeaders: CacheHeaders = CacheHeaders.NONE,
  ): Set<String> = apolloStore.writeFragment(
      fragment = fragment,
      cacheKey = cacheKey,
      data = data,
      cacheHeaders = cacheHeaders,
      customScalarAdapters = customScalarAdapters,
  )

  /**
   * @see ApolloStore.writeOptimisticUpdates
   */
  fun <D : Operation.Data> writeOptimisticUpdates(
      operation: Operation<D>,
      data: D,
      mutationId: Uuid,
  ): Set<String> = apolloStore.writeOptimisticUpdates(
      operation = operation,
      data = data,
      mutationId = mutationId,
      customScalarAdapters = customScalarAdapters,
  )

  /**
   * @see ApolloStore.writeOptimisticUpdates
   */
  fun <D : Fragment.Data> writeOptimisticUpdates(
      fragment: Fragment<D>,
      cacheKey: CacheKey,
      data: D,
      mutationId: Uuid,
  ): Set<String> = apolloStore.writeOptimisticUpdates(
      fragment = fragment,
      cacheKey = cacheKey,
      data = data,
      mutationId = mutationId,
      customScalarAdapters = customScalarAdapters,
  )

  /**
   * @see ApolloStore.rollbackOptimisticUpdates
   */
  fun rollbackOptimisticUpdates(mutationId: Uuid): Set<String> = apolloStore.rollbackOptimisticUpdates(mutationId)

  /**
   * @see ApolloStore.clearAll
   */
  fun clearAll(): Boolean = apolloStore.clearAll()

  /**
   * @see ApolloStore.remove
   */
  fun remove(cacheKey: CacheKey, cascade: Boolean = true): Boolean = apolloStore.remove(cacheKey, cascade)

  /**
   * @see ApolloStore.remove
   */
  fun remove(cacheKeys: List<CacheKey>, cascade: Boolean = true): Int = apolloStore.remove(cacheKeys, cascade)

  /**
   * @see ApolloStore.removeByTypes
   */
  fun removeByTypes(types: List<String>): Int = apolloStore.removeByTypes(types)

  /**
   * @see ApolloStore.trim
   */
  fun trim(maxSizeBytes: Long, trimFactor: Float = 0.1f): Long = apolloStore.trim(maxSizeBytes, trimFactor)

  /**
   * @see ApolloStore.normalize
   */
  fun <D : Executable.Data> normalize(
      executable: Executable<D>,
      dataWithErrors: DataWithErrors,
      rootKey: CacheKey = CacheKey.QUERY_ROOT,
  ): Map<CacheKey, Record> = apolloStore.normalize(
      executable = executable,
      dataWithErrors = dataWithErrors,
      rootKey = rootKey,
      customScalarAdapters = customScalarAdapters,
  )

  /**
   * @see ApolloStore.publish
   */
  suspend fun publish(keys: Set<String>) = apolloStore.publish(keys)

  /**
   * @see ApolloStore.publish
   */
  fun <R> accessCache(block: (NormalizedCache) -> R): R = apolloStore.accessCache(block)

  /**
   * @see ApolloStore.dump
   */
  fun dump(): Map<KClass<*>, Map<CacheKey, Record>> = apolloStore.dump()

  /**
   * @see ApolloStore.dispose
   */
  fun dispose() = apolloStore.dispose()
}
