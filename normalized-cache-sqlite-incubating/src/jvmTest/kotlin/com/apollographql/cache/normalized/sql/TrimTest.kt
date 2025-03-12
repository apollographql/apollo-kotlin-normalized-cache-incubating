package com.apollographql.cache.normalized.sql

import com.apollographql.cache.normalized.api.CacheHeaders
import com.apollographql.cache.normalized.api.CacheKey
import com.apollographql.cache.normalized.api.DefaultRecordMerger
import com.apollographql.cache.normalized.api.Record
import com.apollographql.cache.normalized.api.withDates
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TrimTest {
  @Test
  fun trimTest() {
    val dbName = "build/test.db"
    val dbUrl = "jdbc:sqlite:$dbName"
    val dbFile = File(dbName)

    dbFile.delete()

    val cache = TrimmableNormalizedCacheFactory(dbUrl).create()

    val largeString = "".padStart(1024, '?')

    val oldRecord = Record(
        key = CacheKey("old"),
        fields = mapOf("key" to "value"),
        mutationId = null,
        metadata = emptyMap()
    ).withDates(receivedDate = "0", expirationDate = null)
    cache.merge(oldRecord, CacheHeaders.NONE, recordMerger = DefaultRecordMerger)

    val newRecords = 0.until(2 * 1024).map {
      Record(
          key = CacheKey("new$it"),
          fields = mapOf("key" to largeString),
          mutationId = null,
          metadata = emptyMap()
      ).withDates(receivedDate = it.toString(), expirationDate = null)
    }
    cache.merge(newRecords, CacheHeaders.NONE, recordMerger = DefaultRecordMerger)

    assertEquals(9646080, dbFile.length())

    // Trim the cache by 10%
    val trimmedCache = TrimmableNormalizedCacheFactory(dbUrl, 9646080, 0.1f).create()

    assertEquals(8552448, dbFile.length())
    // The oldest key must have been removed
    assertNull(trimmedCache.loadRecord(CacheKey("old"), CacheHeaders.NONE))
  }
}
