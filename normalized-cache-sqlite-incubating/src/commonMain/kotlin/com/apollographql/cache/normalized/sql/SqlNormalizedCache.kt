package com.apollographql.cache.normalized.sql

import com.apollographql.apollo.exception.apolloExceptionHandler
import com.apollographql.cache.normalized.api.ApolloCacheHeaders
import com.apollographql.cache.normalized.api.CacheHeaders
import com.apollographql.cache.normalized.api.CacheKey
import com.apollographql.cache.normalized.api.DefaultRecordMerger
import com.apollographql.cache.normalized.api.NormalizedCache
import com.apollographql.cache.normalized.api.Record
import com.apollographql.cache.normalized.api.RecordMerger
import com.apollographql.cache.normalized.api.RecordMergerContext
import com.apollographql.cache.normalized.api.withDates
import com.apollographql.cache.normalized.sql.internal.RecordDatabase
import kotlin.reflect.KClass

class SqlNormalizedCache internal constructor(
    private val recordDatabase: RecordDatabase,
) : NormalizedCache {

  override fun loadRecord(key: String, cacheHeaders: CacheHeaders): Record? {
    return loadRecords(keys = listOf(key), cacheHeaders = cacheHeaders).firstOrNull()
  }

  override fun loadRecords(keys: Collection<String>, cacheHeaders: CacheHeaders): Collection<Record> {
    if (cacheHeaders.hasHeader(ApolloCacheHeaders.MEMORY_CACHE_ONLY)) {
      return emptyList()
    }
    return try {
      selectRecords(keys)
    } catch (e: Exception) {
      // Unable to read the records from the database, it is possibly corrupted - treat this as a cache miss
      apolloExceptionHandler(Exception("Unable to read records from the database", e))
      emptyList()
    }
  }

  override fun clearAll() {
    recordDatabase.deleteAllRecords()
  }

  override fun remove(cacheKey: CacheKey, cascade: Boolean): Boolean {
    return remove(cacheKeys = listOf(cacheKey), cascade = cascade) > 0
  }

  override fun remove(cacheKeys: Collection<CacheKey>, cascade: Boolean): Int {
    return recordDatabase.transaction {
      internalDeleteRecords(cacheKeys.map { it.key }, cascade)
    }
  }

  override fun remove(pattern: String): Int {
    return recordDatabase.transaction {
      recordDatabase.deleteRecordsMatching(pattern)
      recordDatabase.changes().toInt()
    }
  }

  override fun merge(record: Record, cacheHeaders: CacheHeaders, recordMerger: RecordMerger): Set<String> {
    return merge(records = listOf(record), cacheHeaders = cacheHeaders, recordMerger = recordMerger)
  }

  override fun merge(records: Collection<Record>, cacheHeaders: CacheHeaders, recordMerger: RecordMerger): Set<String> {
    if (cacheHeaders.hasHeader(ApolloCacheHeaders.DO_NOT_STORE) || cacheHeaders.hasHeader(ApolloCacheHeaders.MEMORY_CACHE_ONLY)) {
      return emptySet()
    }
    return try {
      internalUpdateRecords(records = records, cacheHeaders = cacheHeaders, recordMerger = recordMerger)
    } catch (e: Exception) {
      // Unable to merge the records in the database, it is possibly corrupted - treat this as a cache miss
      apolloExceptionHandler(Exception("Unable to merge records into the database", e))
      emptySet()
    }
  }

  override fun dump(): Map<KClass<*>, Map<String, Record>> {
    return mapOf(this::class to recordDatabase.selectAllRecords().associateBy { it.key })
  }

  private fun getReferencedKeysRecursively(keys: Collection<String>, visited: MutableSet<String> = mutableSetOf()): Set<String> {
    if (keys.isEmpty()) return emptySet()
    val referencedKeys = recordDatabase.selectRecords(keys - visited).flatMap { it.referencedFields() }.map { it.key }.toSet()
    visited += keys
    return referencedKeys + getReferencedKeysRecursively(referencedKeys, visited)
  }

  /**
   * Assumes an enclosing transaction
   */
  private fun internalDeleteRecords(keys: Collection<String>, cascade: Boolean): Int {
    val referencedKeys = if (cascade) {
      getReferencedKeysRecursively(keys)
    } else {
      emptySet()
    }
    return (keys + referencedKeys).chunked(999).sumOf { chunkedKeys ->
      recordDatabase.deleteRecords(chunkedKeys)
      recordDatabase.changes().toInt()
    }
  }

  /**
   * Update records.
   *
   * As an optimization, the [records] fields are directly upserted into the db when possible. This is possible when using
   * the [DefaultRecordMerger], and [ApolloCacheHeaders.ERRORS_REPLACE_CACHED_VALUES] is set to true.
   * Otherwise, the [records] must be merged programmatically using the given [recordMerger], requiring to load the existing records from
   * the db first.
   */
  private fun internalUpdateRecords(records: Collection<Record>, cacheHeaders: CacheHeaders, recordMerger: RecordMerger): Set<String> {
    val receivedDate = cacheHeaders.headerValue(ApolloCacheHeaders.RECEIVED_DATE)
    val expirationDate = cacheHeaders.headerValue(ApolloCacheHeaders.EXPIRATION_DATE)
    val errorsReplaceCachedValues = cacheHeaders.headerValue(ApolloCacheHeaders.ERRORS_REPLACE_CACHED_VALUES) == "true"
    return if (recordMerger is DefaultRecordMerger && errorsReplaceCachedValues) {
      recordDatabase.transaction {
        for (record in records) {
          recordDatabase.insertOrUpdateRecord(record.withDates(receivedDate = receivedDate, expirationDate = expirationDate))
        }
      }
      records.flatMap { record ->
        record.fieldKeys()
      }.toSet()
    } else {
      recordDatabase.transaction {
        val existingRecords = selectRecords(records.map { it.key }).associateBy { it.key }
        records.flatMap { record ->
          val existingRecord = existingRecords[record.key]
          if (existingRecord == null) {
            recordDatabase.insertOrUpdateRecord(record.withDates(receivedDate = receivedDate, expirationDate = expirationDate))
            record.fieldKeys()
          } else {
            val (mergedRecord, changedKeys) = recordMerger.merge(RecordMergerContext(existing = existingRecord, incoming = record.withDates(receivedDate = receivedDate, expirationDate = expirationDate), cacheHeaders = cacheHeaders))
            if (mergedRecord.isNotEmpty()) {
              recordDatabase.insertOrUpdateRecord(mergedRecord)
            }
            changedKeys
          }
        }.toSet()
      }
    }
  }

  /**
   * Loads a list of records, making sure to not query more than 999 at a time
   * to help with the SQLite limitations
   */
  private fun selectRecords(keys: Collection<String>): List<Record> {
    return keys.chunked(999).flatMap { chunkedKeys ->
      recordDatabase.selectRecords(chunkedKeys)
    }
  }

  override fun trim(maxSizeBytes: Long, trimFactor: Float): Long {
    val size = recordDatabase.databaseSize()
    return if (size >= maxSizeBytes) {
      val count = recordDatabase.count().executeAsOne()
      recordDatabase.trimByReceivedDate((count * trimFactor).toLong())
      recordDatabase.vacuum()
      recordDatabase.databaseSize()
    } else {
      size
    }
  }
}
