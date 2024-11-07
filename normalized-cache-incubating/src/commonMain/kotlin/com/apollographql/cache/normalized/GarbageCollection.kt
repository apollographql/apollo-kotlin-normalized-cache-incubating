package com.apollographql.cache.normalized

import com.apollographql.apollo.annotations.ApolloInternal
import com.apollographql.cache.normalized.api.CacheHeaders
import com.apollographql.cache.normalized.api.CacheKey
import com.apollographql.cache.normalized.api.Record
import com.apollographql.cache.normalized.internal.OptimisticNormalizedCache

@ApolloInternal
fun ApolloStore.getReachableCacheKeys(): Set<CacheKey> {
  fun ApolloStore.getReachableCacheKeys(roots: List<CacheKey>, reachableCacheKeys: MutableSet<CacheKey>) {
    val records = accessCache { cache -> cache.loadRecords(roots.map { it.key }, CacheHeaders.NONE) }.associateBy { it.key }
    val cacheKeysToCheck = mutableListOf<CacheKey>()
    for ((key, record) in records) {
      reachableCacheKeys.add(CacheKey(key))
      cacheKeysToCheck.addAll(record.referencedFields())
    }
    if (cacheKeysToCheck.isNotEmpty()) {
      getReachableCacheKeys(cacheKeysToCheck, reachableCacheKeys)
    }
  }

  return mutableSetOf<CacheKey>().also { reachableCacheKeys ->
    getReachableCacheKeys(listOf(CacheKey.rootKey()), reachableCacheKeys)
  }
}

@ApolloInternal
fun ApolloStore.allRecords(): Map<String, Record> {
  return accessCache { cache ->
    val dump = cache.dump()
    val classKey = dump.keys.first { it != OptimisticNormalizedCache::class }
    dump[classKey]!!
  }
}

fun ApolloStore.removeUnreachableRecords(): Set<CacheKey> {
  val unreachableCacheKeys = allRecords().keys.map { CacheKey(it) } - getReachableCacheKeys()
  remove(unreachableCacheKeys, cascade = false)
  return unreachableCacheKeys.toSet()
}
