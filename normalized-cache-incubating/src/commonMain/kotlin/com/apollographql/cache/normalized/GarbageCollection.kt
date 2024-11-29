package com.apollographql.cache.normalized

import com.apollographql.apollo.annotations.ApolloInternal
import com.apollographql.apollo.mpp.currentTimeMillis
import com.apollographql.cache.normalized.api.CacheHeaders
import com.apollographql.cache.normalized.api.CacheKey
import com.apollographql.cache.normalized.api.DefaultRecordMerger
import com.apollographql.cache.normalized.api.MaxAgeContext
import com.apollographql.cache.normalized.api.MaxAgeProvider
import com.apollographql.cache.normalized.api.NormalizedCache
import com.apollographql.cache.normalized.api.Record
import com.apollographql.cache.normalized.api.RecordValue
import com.apollographql.cache.normalized.api.expirationDate
import com.apollographql.cache.normalized.api.receivedDate
import kotlin.time.Duration

@ApolloInternal
fun NormalizedCache.getReachableCacheKeys(): Set<CacheKey> {
  fun NormalizedCache.getReachableCacheKeys(roots: List<CacheKey>, reachableCacheKeys: MutableSet<CacheKey>) {
    val records = loadRecords(roots.map { it.key }, CacheHeaders.NONE).associateBy { it.key }
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
fun NormalizedCache.allRecords(): Map<String, Record> {
  return dump().values.fold(emptyMap()) { acc, map -> acc + map }
}

/**
 * Remove all unreachable records in the cache.
 * A record is unreachable if there exist no chain of references from the root record to it.
 *
 * @return the cache keys that were removed.
 */
fun NormalizedCache.removeUnreachableRecords(): Set<CacheKey> {
  val unreachableCacheKeys = allRecords().keys.map { CacheKey(it) } - getReachableCacheKeys()
  remove(unreachableCacheKeys, cascade = false)
  return unreachableCacheKeys.toSet()
}

/**
 * Remove all unreachable records in the store.
 * @see removeUnreachableRecords
 */
fun ApolloStore.removeUnreachableRecords(): Set<CacheKey> {
  return accessCache { cache ->
    cache.removeUnreachableRecords()
  }
}

/**
 * Remove all stale fields in the cache.
 * A field is stale if its received date is older than its max age (configurable via [maxAgeProvider]) or if its expiration date has
 * passed. A maximum staleness can be passed.
 *
 * Received dates are stored by calling `storeReceiveDate(true)` on your `ApolloClient`.
 *
 * Expiration dates are stored by calling `storeExpirationDate(true)` on your `ApolloClient`.
 *
 * When all fields of a record are removed, the record itself is removed too.
 *
 * This can result in unreachable records, and dangling references.
 *
 * @return the field keys that were removed.
 */
fun NormalizedCache.removeStaleFields(
    maxAgeProvider: MaxAgeProvider,
    maxStale: Duration = Duration.ZERO,
): Set<String> {
  val allRecords: Map<String, Record> = allRecords()
  val recordsToUpdate = mutableMapOf<String, Record>()
  val removedKeys = mutableSetOf<String>()
  for (record in allRecords.values) {
    var recordCopy = record
    for (field in record.fields) {
      // Consider the client controlled max age
      val receivedDate = record.receivedDate(field.key)
      if (receivedDate != null) {
        val currentDate = currentTimeMillis() / 1000
        val age = currentDate - receivedDate
        val maxAge = maxAgeProvider.getMaxAge(
            MaxAgeContext(
                listOf(
                    MaxAgeContext.Field(name = "", type = record["__typename"] as? String ?: "", isTypeComposite = true),
                    MaxAgeContext.Field(name = field.key, type = field.value.guessType(allRecords), isTypeComposite = field.value is CacheKey),
                )
            )
        ).inWholeSeconds
        val staleDuration = age - maxAge
        if (staleDuration >= maxStale.inWholeSeconds) {
          recordCopy -= field.key
          recordsToUpdate[record.key] = recordCopy
          removedKeys.add(record.key + "." + field.key)
          continue
        }
      }

      // Consider the server controlled max age
      val expirationDate = record.expirationDate(field.key)
      if (expirationDate != null) {
        val currentDate = currentTimeMillis() / 1000
        val staleDuration = currentDate - expirationDate
        if (staleDuration >= maxStale.inWholeSeconds) {
          recordCopy -= field.key
          recordsToUpdate[record.key] = recordCopy
          removedKeys.add(record.key + "." + field.key)
        }
      }
    }
  }
  if (recordsToUpdate.isNotEmpty()) {
    remove(recordsToUpdate.keys.map { CacheKey(it) }, cascade = false)
    val nonEmptyRecords = recordsToUpdate.values.filterNot { it.isEmptyRecord() }
    if (nonEmptyRecords.isNotEmpty()) {
      merge(nonEmptyRecords, CacheHeaders.NONE, DefaultRecordMerger)
    }
  }
  return removedKeys
}

/**
 * Remove all stale fields in the store.
 * @see removeStaleFields
 */
fun ApolloStore.removeStaleFields(
    maxAgeProvider: MaxAgeProvider,
    maxStale: Duration = Duration.ZERO,
): Set<String> {
  return accessCache { cache ->
    cache.removeStaleFields(maxAgeProvider, maxStale)
  }
}

/**
 * Remove all dangling references in the cache.
 * A field is a dangling reference if its value (or, for lists, any of its values) is a reference to a record that does not exist.
 *
 * When all fields of a record are removed, the record itself is removed too.
 *
 * This can result in unreachable records.
 *
 * @return the field keys that were removed.
 */
fun NormalizedCache.removeDanglingReferences(): Set<String> {
  val allRecords: MutableMap<String, Record> = allRecords().toMutableMap()
  val recordsToUpdate = mutableMapOf<String, Record>()
  val allRemovedKeys = mutableSetOf<String>()
  do {
    val removedKeys = mutableSetOf<String>()
    for (record in allRecords.values.toList()) {
      var recordCopy = record
      for (field in record.fields) {
        if (field.value.isDanglingReference(allRecords)) {
          recordCopy -= field.key
          recordsToUpdate[record.key] = recordCopy
          removedKeys.add(record.key + "." + field.key)
          if (recordCopy.isEmptyRecord()) {
            allRecords.remove(record.key)
          } else {
            allRecords[record.key] = recordCopy
          }
        }
      }
    }
    allRemovedKeys.addAll(removedKeys)
  } while (removedKeys.isNotEmpty())
  if (recordsToUpdate.isNotEmpty()) {
    remove(recordsToUpdate.keys.map { CacheKey(it) }, cascade = false)
    val nonEmptyRecords = recordsToUpdate.values.filterNot { it.isEmptyRecord() }
    if (nonEmptyRecords.isNotEmpty()) {
      merge(nonEmptyRecords, CacheHeaders.NONE, DefaultRecordMerger)
    }
  }
  return allRemovedKeys
}

/**
 * Remove all dangling references in the store.
 * @see removeDanglingReferences
 */
fun ApolloStore.removeDanglingReferences(): Set<String> {
  return accessCache { cache ->
    cache.removeDanglingReferences()
  }
}

private fun RecordValue.isDanglingReference(allRecords: Map<String, Record>): Boolean {
  return when (this) {
    is CacheKey -> allRecords[this.key] == null
    is List<*> -> any { it.isDanglingReference(allRecords) }
    is Map<*, *> -> values.any { it.isDanglingReference(allRecords) }
    else -> false
  }
}

private fun Record.isEmptyRecord() = fields.isEmpty() || fields.size == 1 && fields.keys.first() == "__typename"

private fun RecordValue.guessType(allRecords: Map<String, Record>): String {
  return when (this) {
    is List<*> -> {
      val first = firstOrNull() ?: return ""
      first.guessType(allRecords)
    }

    is CacheKey -> {
      allRecords[key]?.get("__typename") as? String ?: ""
    }

    else -> {
      ""
    }
  }
}

private operator fun Record.minus(key: String): Record {
  return Record(
      key = this.key,
      fields = this.fields - key,
      metadata = this.metadata - key,
  )
}

/**
 * Perform garbage collection on the cache.
 *
 * This is a convenience method that calls [removeStaleFields], [removeDanglingReferences], and [removeUnreachableRecords].
 *
 * @param maxAgeProvider the max age provider to use for [removeStaleFields]
 * @param maxStale the maximum staleness to use for [removeStaleFields]
 */
fun NormalizedCache.garbageCollect(
    maxAgeProvider: MaxAgeProvider,
    maxStale: Duration = Duration.ZERO,
) {
  removeStaleFields(maxAgeProvider, maxStale)
  removeDanglingReferences()
  removeUnreachableRecords()
}

/**
 * Perform garbage collection on the store.
 * @see garbageCollect
 */
fun ApolloStore.garbageCollect(
    maxAgeProvider: MaxAgeProvider,
    maxStale: Duration = Duration.ZERO,
) {
  accessCache { cache ->
    cache.garbageCollect(maxAgeProvider, maxStale)
  }
}
