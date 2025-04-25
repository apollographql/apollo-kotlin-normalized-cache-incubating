package com.apollographql.cache.normalized

import com.apollographql.apollo.api.ApolloResponse
import com.apollographql.apollo.api.CustomScalarAdapters
import com.apollographql.apollo.api.Error
import com.apollographql.apollo.api.Executable
import com.apollographql.apollo.api.Fragment
import com.apollographql.apollo.api.Operation
import com.apollographql.cache.normalized.CacheManager.ReadResult
import com.apollographql.cache.normalized.api.CacheHeaders
import com.apollographql.cache.normalized.api.CacheKey
import com.apollographql.cache.normalized.api.DataWithErrors
import com.apollographql.cache.normalized.api.NormalizedCache
import com.apollographql.cache.normalized.api.Record
import com.benasher44.uuid.Uuid
import kotlinx.coroutines.flow.SharedFlow
import kotlin.reflect.KClass

/**
 * A wrapper around [CacheManager] that provides a simplified API for reading and writing data.
 */
class ApolloStore(
    val cacheManager: CacheManager,
    val customScalarAdapters: CustomScalarAdapters,
) {
  /**
   * @see CacheManager.changedKeys
   */
  val changedKeys: SharedFlow<Set<String>>
    get() = cacheManager.changedKeys

  /**
   * @see CacheManager.readOperation
   */
  fun <D : Operation.Data> readOperation(
      operation: Operation<D>,
      cacheHeaders: CacheHeaders = CacheHeaders.NONE,
  ): ApolloResponse<D> = cacheManager.readOperation(
      operation = operation,
      cacheHeaders = cacheHeaders,
      customScalarAdapters = customScalarAdapters,
  )

  /**
   * @see CacheManager.readFragment
   */
  fun <D : Fragment.Data> readFragment(
      fragment: Fragment<D>,
      cacheKey: CacheKey,
      cacheHeaders: CacheHeaders = CacheHeaders.NONE,
  ): ReadResult<D> = cacheManager.readFragment(
      fragment = fragment,
      cacheKey = cacheKey,
      customScalarAdapters = customScalarAdapters,
      cacheHeaders = cacheHeaders,
  )

  /**
   * @see CacheManager.writeOperation
   */
  fun <D : Operation.Data> writeOperation(
      operation: Operation<D>,
      data: D,
      errors: List<Error>? = null,
      cacheHeaders: CacheHeaders = CacheHeaders.NONE,
  ): Set<String> = cacheManager.writeOperation(
      operation = operation,
      data = data,
      errors = errors,
      cacheHeaders = cacheHeaders,
      customScalarAdapters = customScalarAdapters,
  )

  /**
   * @see CacheManager.writeFragment
   */
  fun <D : Operation.Data> writeOperation(
      operation: Operation<D>,
      dataWithErrors: DataWithErrors,
      cacheHeaders: CacheHeaders = CacheHeaders.NONE,
  ): Set<String> = cacheManager.writeOperation(
      operation = operation,
      dataWithErrors = dataWithErrors,
      cacheHeaders = cacheHeaders,
      customScalarAdapters = customScalarAdapters,
  )

  /**
   * @see CacheManager.writeFragment
   */
  fun <D : Fragment.Data> writeFragment(
      fragment: Fragment<D>,
      cacheKey: CacheKey,
      data: D,
      cacheHeaders: CacheHeaders = CacheHeaders.NONE,
  ): Set<String> = cacheManager.writeFragment(
      fragment = fragment,
      cacheKey = cacheKey,
      data = data,
      cacheHeaders = cacheHeaders,
      customScalarAdapters = customScalarAdapters,
  )

  /**
   * @see CacheManager.writeOptimisticUpdates
   */
  fun <D : Operation.Data> writeOptimisticUpdates(
      operation: Operation<D>,
      data: D,
      mutationId: Uuid,
  ): Set<String> = cacheManager.writeOptimisticUpdates(
      operation = operation,
      data = data,
      mutationId = mutationId,
      customScalarAdapters = customScalarAdapters,
  )

  /**
   * @see CacheManager.writeOptimisticUpdates
   */
  fun <D : Fragment.Data> writeOptimisticUpdates(
      fragment: Fragment<D>,
      cacheKey: CacheKey,
      data: D,
      mutationId: Uuid,
  ): Set<String> = cacheManager.writeOptimisticUpdates(
      fragment = fragment,
      cacheKey = cacheKey,
      data = data,
      mutationId = mutationId,
      customScalarAdapters = customScalarAdapters,
  )

  /**
   * @see CacheManager.rollbackOptimisticUpdates
   */
  fun rollbackOptimisticUpdates(mutationId: Uuid): Set<String> = cacheManager.rollbackOptimisticUpdates(mutationId)

  /**
   * @see CacheManager.clearAll
   */
  fun clearAll(): Boolean = cacheManager.clearAll()

  /**
   * @see CacheManager.remove
   */
  fun remove(cacheKey: CacheKey, cascade: Boolean = true): Boolean = cacheManager.remove(cacheKey, cascade)

  /**
   * @see CacheManager.remove
   */
  fun remove(cacheKeys: List<CacheKey>, cascade: Boolean = true): Int = cacheManager.remove(cacheKeys, cascade)

  /**
   * @see CacheManager.trim
   */
  fun trim(maxSizeBytes: Long, trimFactor: Float = 0.1f): Long = cacheManager.trim(maxSizeBytes, trimFactor)

  /**
   * @see CacheManager.normalize
   */
  fun <D : Executable.Data> normalize(
      executable: Executable<D>,
      dataWithErrors: DataWithErrors,
      rootKey: CacheKey = CacheKey.QUERY_ROOT,
  ): Map<CacheKey, Record> = cacheManager.normalize(
      executable = executable,
      dataWithErrors = dataWithErrors,
      rootKey = rootKey,
      customScalarAdapters = customScalarAdapters,
  )

  /**
   * @see CacheManager.publish
   */
  suspend fun publish(keys: Set<String>) = cacheManager.publish(keys)

  /**
   * @see CacheManager.publish
   */
  fun <R> accessCache(block: (NormalizedCache) -> R): R = cacheManager.accessCache(block)

  /**
   * @see CacheManager.dump
   */
  fun dump(): Map<KClass<*>, Map<CacheKey, Record>> = cacheManager.dump()

  /**
   * @see CacheManager.dispose
   */
  fun dispose() = cacheManager.dispose()
}
