package com.apollographql.cache.normalized.api

/**
 * A collection of cache headers that Apollo's implementations of [NormalizedCache] respect.
 */
object ApolloCacheHeaders {
  /**
   * Records from this request should not be stored in the [NormalizedCache].
   */
  const val DO_NOT_STORE = "do-not-store"

  /**
   * Records should be stored and read from the [MemoryCache] only.
   */
  const val MEMORY_CACHE_ONLY = "memory-cache-only"

  /**
   * Records from this request should be evicted after being read.
   */
  const val EVICT_AFTER_READ = "evict-after-read"

  /**
   * The value of this header will be stored in the [Record] fields date
   */
  const val DATE = "apollo-date"

  /**
   * How long to accept stale fields
   */
  const val MAX_STALE = "apollo-max-stale"
}
