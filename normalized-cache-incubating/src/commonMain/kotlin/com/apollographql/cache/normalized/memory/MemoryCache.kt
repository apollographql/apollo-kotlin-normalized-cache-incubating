package com.apollographql.cache.normalized.memory

import com.apollographql.cache.normalized.api.ApolloCacheHeaders
import com.apollographql.cache.normalized.api.CacheHeaders
import com.apollographql.cache.normalized.api.CacheKey
import com.apollographql.cache.normalized.api.NormalizedCache
import com.apollographql.cache.normalized.api.NormalizedCacheFactory
import com.apollographql.cache.normalized.api.Record
import com.apollographql.cache.normalized.api.RecordMerger
import com.apollographql.cache.normalized.api.RecordMergerContext
import com.apollographql.cache.normalized.api.withDates
import com.apollographql.cache.normalized.internal.Lock
import com.apollographql.cache.normalized.internal.patternToRegex
import com.apollographql.cache.normalized.memory.internal.LruCache
import kotlin.jvm.JvmOverloads
import kotlin.reflect.KClass

/**
 * Memory (multiplatform) cache implementation based on recently used property (LRU).
 *
 * [maxSizeBytes] - the maximum size in bytes the cache may occupy.
 * [expireAfterMillis] - after what timeout each entry in the cache treated as expired. By default there is no timeout.
 *
 * Expired entries removed from the cache only on cache miss ([loadRecord] operation) and not removed from the cache automatically
 * (there is no any sort of GC that runs in the background).
 */
class MemoryCache(
    private val nextCache: NormalizedCache? = null,
    private val maxSizeBytes: Int = Int.MAX_VALUE,
    private val expireAfterMillis: Long = -1,
) : NormalizedCache {
  // A lock is only needed if there is a nextCache
  private val lock = nextCache?.let { Lock() }

  private fun <T> lockWrite(block: () -> T): T {
    return lock?.write { block() } ?: block()
  }

  private fun <T> lockRead(block: () -> T): T {
    return lock?.read { block() } ?: block()
  }

  private val lruCache = LruCache<String, Record>(maxSize = maxSizeBytes, expireAfterMillis = expireAfterMillis) { key, record ->
    key.length + record.sizeInBytes
  }

  val size: Int
    get() = lockRead { lruCache.weight() }

  override fun loadRecord(key: String, cacheHeaders: CacheHeaders): Record? = lockRead {
    val record = internalLoadRecord(key, cacheHeaders)
    record ?: nextCache?.loadRecord(key, cacheHeaders)?.also { nextCachedRecord ->
      lruCache[key] = nextCachedRecord
    }
  }

  override fun loadRecords(keys: Collection<String>, cacheHeaders: CacheHeaders): Collection<Record> = lockRead {
    val recordsByKey: Map<String, Record?> = keys.associateWith { key -> internalLoadRecord(key, cacheHeaders) }
    val missingKeys = recordsByKey.filterValues { it == null }.keys
    val nextCachedRecords = nextCache?.loadRecords(missingKeys, cacheHeaders).orEmpty()
    for (record in nextCachedRecords) {
      lruCache[record.key] = record
    }
    recordsByKey.values.filterNotNull() + nextCachedRecords
  }

  private fun internalLoadRecord(key: String, cacheHeaders: CacheHeaders): Record? {
    return lruCache[key]?.also {
      if (cacheHeaders.hasHeader(ApolloCacheHeaders.EVICT_AFTER_READ)) {
        lruCache.remove(key)
      }
    }
  }

  override fun clearAll() {
    lockWrite {
      lruCache.clear()
      nextCache?.clearAll()
    }
  }

  override fun remove(cacheKey: CacheKey, cascade: Boolean): Boolean {
    return remove(setOf(cacheKey), cascade) > 0
  }

  override fun remove(cacheKeys: Collection<CacheKey>, cascade: Boolean): Int {
    return lockWrite {
      val total = internalRemove(cacheKeys, cascade)
      nextCache?.remove(cacheKeys, cascade)
      total
    }
  }

  private fun internalRemove(cacheKeys: Collection<CacheKey>, cascade: Boolean): Int {
    var total = 0
    val referencedCacheKeys = mutableSetOf<CacheKey>()
    for (cacheKey in cacheKeys) {
      val removedRecord = lruCache.remove(cacheKey.key)
      if (cascade && removedRecord != null) {
        referencedCacheKeys += removedRecord.referencedFields().map { CacheKey(it.key) }
      }
      if (removedRecord != null) {
        total++
      }
    }
    if (referencedCacheKeys.isNotEmpty()) {
      total += internalRemove(referencedCacheKeys, cascade)
    }
    return total
  }

  override fun remove(pattern: String): Int {
    val regex = patternToRegex(pattern)
    return lockWrite {
      var total = 0
      val keys = HashSet(lruCache.asMap().keys) // local copy to avoid concurrent modification
      keys.forEach {
        if (regex.matches(it)) {
          lruCache.remove(it)
          total++
        }
      }

      val chainRemoved = nextCache?.remove(pattern) ?: 0
      total + chainRemoved
    }
  }

  override fun merge(record: Record, cacheHeaders: CacheHeaders, recordMerger: RecordMerger): Set<String> {
    if (cacheHeaders.hasHeader(ApolloCacheHeaders.DO_NOT_STORE)) {
      return emptySet()
    }
    return lockWrite {
      val changedKeys = internalMerge(record, cacheHeaders, recordMerger)
      changedKeys + nextCache?.merge(record, cacheHeaders, recordMerger).orEmpty()
    }
  }

  override fun merge(records: Collection<Record>, cacheHeaders: CacheHeaders, recordMerger: RecordMerger): Set<String> {
    if (cacheHeaders.hasHeader(ApolloCacheHeaders.DO_NOT_STORE)) {
      return emptySet()
    }
    return lockWrite {
      val changedKeys = records.flatMap { record -> internalMerge(record, cacheHeaders, recordMerger) }.toSet()
      changedKeys + nextCache?.merge(records, cacheHeaders, recordMerger).orEmpty()
    }
  }

  private fun internalMerge(record: Record, cacheHeaders: CacheHeaders, recordMerger: RecordMerger): Set<String> {
    val receivedDate = cacheHeaders.headerValue(ApolloCacheHeaders.RECEIVED_DATE)
    val expirationDate = cacheHeaders.headerValue(ApolloCacheHeaders.EXPIRATION_DATE)
    val oldRecord = loadRecord(record.key, cacheHeaders)
    val changedKeys = if (oldRecord == null) {
      lruCache[record.key] = record.withDates(receivedDate = receivedDate, expirationDate = expirationDate)
      record.fieldKeys()
    } else {
      val (mergedRecord, changedKeys) = recordMerger.merge(RecordMergerContext(existing = oldRecord, incoming = record, cacheHeaders = cacheHeaders))
      lruCache[record.key] = mergedRecord.withDates(receivedDate = receivedDate, expirationDate = expirationDate)
      changedKeys
    }
    return changedKeys
  }

  override fun dump(): Map<KClass<*>, Map<String, Record>> {
    return lockRead {
      mapOf(this::class to lruCache.asMap().mapValues { (_, record) -> record }) +
          nextCache?.dump().orEmpty()
    }
  }

  internal fun clearCurrentCache() {
    lruCache.clear()
  }
}

class MemoryCacheFactory @JvmOverloads constructor(
    private val maxSizeBytes: Int = Int.MAX_VALUE,
    private val expireAfterMillis: Long = -1,
) : NormalizedCacheFactory() {

  private var nextCacheFactory: NormalizedCacheFactory? = null

  fun chain(factory: NormalizedCacheFactory): MemoryCacheFactory {
    nextCacheFactory = factory
    return this
  }

  override fun create(): MemoryCache {
    return MemoryCache(
        nextCache = nextCacheFactory?.create(),
        maxSizeBytes = maxSizeBytes,
        expireAfterMillis = expireAfterMillis,
    )
  }
}
