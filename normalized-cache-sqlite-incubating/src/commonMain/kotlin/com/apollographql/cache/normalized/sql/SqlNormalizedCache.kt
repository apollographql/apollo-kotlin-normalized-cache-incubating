package com.apollographql.cache.normalized.sql

import com.apollographql.apollo.exception.apolloExceptionHandler
import com.apollographql.cache.normalized.api.ApolloCacheHeaders
import com.apollographql.cache.normalized.api.ApolloCacheHeaders.EVICT_AFTER_READ
import com.apollographql.cache.normalized.api.CacheHeaders
import com.apollographql.cache.normalized.api.CacheKey
import com.apollographql.cache.normalized.api.NormalizedCache
import com.apollographql.cache.normalized.api.Record
import com.apollographql.cache.normalized.api.RecordMerger
import com.apollographql.cache.normalized.api.withDates
import com.apollographql.cache.normalized.sql.internal.RecordDatabase
import kotlin.reflect.KClass

class SqlNormalizedCache internal constructor(
    private val recordDatabase: RecordDatabase,
) : NormalizedCache {

  private fun <T> maybeTransaction(condition: Boolean, block: () -> T): T {
    return if (condition) {
      recordDatabase.transaction {
        block()
      }
    } else {
      block()
    }
  }

  override fun loadRecord(key: String, cacheHeaders: CacheHeaders): Record? {
    if (cacheHeaders.hasHeader(ApolloCacheHeaders.MEMORY_CACHE_ONLY)) {
      return null
    }
    val evictAfterRead = cacheHeaders.hasHeader(EVICT_AFTER_READ)
    return maybeTransaction(evictAfterRead) {
      try {
        recordDatabase.select(key)
      } catch (e: Exception) {
        // Unable to read the record from the database, it is possibly corrupted - treat this as a cache miss
        apolloExceptionHandler(Exception("Unable to read a record from the database", e))
        null
      }?.also {
        if (evictAfterRead) {
          recordDatabase.delete(key)
        }
      }
    }
  }

  override fun loadRecords(keys: Collection<String>, cacheHeaders: CacheHeaders): Collection<Record> {
    if (cacheHeaders.hasHeader(ApolloCacheHeaders.MEMORY_CACHE_ONLY)) {
      return emptyList()
    }
    val evictAfterRead = cacheHeaders.hasHeader(EVICT_AFTER_READ)
    return maybeTransaction(evictAfterRead) {
      try {
        internalGetRecords(keys)
      } catch (e: Exception) {
        // Unable to read the records from the database, it is possibly corrupted - treat this as a cache miss
        apolloExceptionHandler(Exception("Unable to read records from the database", e))
        emptyList()
      }.also {
        if (evictAfterRead) {
          it.forEach { record ->
            recordDatabase.delete(record.key)
          }
        }
      }
    }
  }

  override fun clearAll() {
    recordDatabase.deleteAll()
  }

  override fun remove(cacheKey: CacheKey, cascade: Boolean): Boolean {
    return recordDatabase.transaction {
      internalDeleteRecords(setOf(cacheKey.key), cascade) > 0
    }
  }

  override fun remove(cacheKeys: Collection<CacheKey>, cascade: Boolean): Int {
    return recordDatabase.transaction {
      internalDeleteRecords(cacheKeys.map { it.key }, cascade)
    }
  }

  override fun remove(pattern: String): Int {
    return recordDatabase.transaction {
      recordDatabase.deleteMatching(pattern)
      recordDatabase.changes().toInt()
    }
  }

  override fun merge(record: Record, cacheHeaders: CacheHeaders, recordMerger: RecordMerger): Set<String> {
    if (cacheHeaders.hasHeader(ApolloCacheHeaders.DO_NOT_STORE) || cacheHeaders.hasHeader(ApolloCacheHeaders.MEMORY_CACHE_ONLY)) {
      return emptySet()
    }
    return try {
      internalUpdateRecord(record = record, cacheHeaders = cacheHeaders, recordMerger = recordMerger)
    } catch (e: Exception) {
      // Unable to merge the record in the database, it is possibly corrupted - treat this as a cache miss
      apolloExceptionHandler(Exception("Unable to merge a record from the database", e))
      emptySet()
    }
  }

  override fun merge(records: Collection<Record>, cacheHeaders: CacheHeaders, recordMerger: RecordMerger): Set<String> {
    if (cacheHeaders.hasHeader(ApolloCacheHeaders.DO_NOT_STORE) || cacheHeaders.hasHeader(ApolloCacheHeaders.MEMORY_CACHE_ONLY)) {
      return emptySet()
    }
    return try {
      internalUpdateRecords(records = records, cacheHeaders = cacheHeaders, recordMerger = recordMerger)
    } catch (e: Exception) {
      // Unable to merge the records in the database, it is possibly corrupted - treat this as a cache miss
      apolloExceptionHandler(Exception("Unable to merge records from the database", e))
      emptySet()
    }
  }

  override fun dump(): Map<KClass<*>, Map<String, Record>> {
    return mapOf(this::class to recordDatabase.selectAll().associateBy { it.key })
  }

  private fun getReferencedKeysRecursively(keys: Collection<String>, visited: MutableSet<String> = mutableSetOf()): Set<String> {
    if (keys.isEmpty()) return emptySet()
    val referencedKeys = recordDatabase.select(keys - visited).flatMap { it.referencedFields() }.map { it.key }.toSet()
    visited += keys
    return referencedKeys + getReferencedKeysRecursively(referencedKeys, visited)
  }

  /**
   * Assume an enclosing transaction
   */
  private fun internalDeleteRecords(keys: Collection<String>, cascade: Boolean): Int {
    val referencedKeys = if (cascade) {
      getReferencedKeysRecursively(keys)
    } else {
      emptySet()
    }
    return (keys + referencedKeys).chunked(999).sumOf { chunkedKeys ->
      recordDatabase.delete(chunkedKeys)
      recordDatabase.changes().toInt()
    }
  }

  /**
   * Update records, loading the previous ones
   *
   * This is an optimization over [internalUpdateRecord]
   */
  private fun internalUpdateRecords(records: Collection<Record>, cacheHeaders: CacheHeaders, recordMerger: RecordMerger): Set<String> {
    var updatedRecordKeys: Set<String> = emptySet()
    val receivedDate = cacheHeaders.headerValue(ApolloCacheHeaders.RECEIVED_DATE)
    val expirationDate = cacheHeaders.headerValue(ApolloCacheHeaders.EXPIRATION_DATE)
    recordDatabase.transaction {
      val oldRecords = internalGetRecords(
          keys = records.map { it.key },
      ).associateBy { it.key }

      updatedRecordKeys = records.flatMap { record ->
        val oldRecord = oldRecords[record.key]
        if (oldRecord == null) {
          recordDatabase.insert(record.withDates(receivedDate = receivedDate, expirationDate = expirationDate))
          record.fieldKeys()
        } else {
          val (mergedRecord, changedKeys) = recordMerger.merge(existing = oldRecord, incoming = record)
          if (mergedRecord.isNotEmpty()) {
            recordDatabase.update(mergedRecord.withDates(receivedDate = receivedDate, expirationDate = expirationDate))
          }
          changedKeys
        }
      }.toSet()
    }
    return updatedRecordKeys
  }

  /**
   * Update a single [Record], loading the previous one
   */
  private fun internalUpdateRecord(record: Record, cacheHeaders: CacheHeaders, recordMerger: RecordMerger): Set<String> {
    val receivedDate = cacheHeaders.headerValue(ApolloCacheHeaders.RECEIVED_DATE)
    val expirationDate = cacheHeaders.headerValue(ApolloCacheHeaders.EXPIRATION_DATE)
    return recordDatabase.transaction {
      val oldRecord = recordDatabase.select(record.key)
      if (oldRecord == null) {
        recordDatabase.insert(record.withDates(receivedDate = receivedDate, expirationDate = expirationDate))
        record.fieldKeys()
      } else {
        val (mergedRecord, changedKeys) = recordMerger.merge(existing = oldRecord, incoming = record)
        if (mergedRecord.isNotEmpty()) {
          recordDatabase.update(mergedRecord.withDates(receivedDate = receivedDate, expirationDate = expirationDate))
        }
        changedKeys
      }
    }
  }

  /**
   * Loads a list of records, making sure to not query more than 999 at a time
   * to help with the SQLite limitations
   */
  private fun internalGetRecords(keys: Collection<String>): List<Record> {
    return keys.chunked(999).flatMap { chunkedKeys ->
      recordDatabase.select(chunkedKeys)
    }
  }
}
