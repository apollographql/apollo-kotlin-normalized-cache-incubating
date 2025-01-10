package com.apollographql.cache.normalized.sql

import com.apollographql.cache.normalized.api.CacheHeaders
import com.apollographql.cache.normalized.api.DefaultRecordMerger
import com.apollographql.cache.normalized.api.Record
import com.apollographql.cache.normalized.api.withDates
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TrimTest {
  @Test
  fun trimTest() {
    val cache = SqlNormalizedCacheFactory().create().also { it.clearAll() }

    val largeString = "".padStart(1024, '?')

    val oldRecord = Record(
        key = "old",
        fields = mapOf("key" to "value"),
        mutationId = null,
        metadata = emptyMap()
    ).withDates(receivedDate = "0", expirationDate = null)
    cache.merge(oldRecord, CacheHeaders.NONE, recordMerger = DefaultRecordMerger)

    val newRecords = 0.until(2 * 1024).map {
      Record(
          key = "new$it",
          fields = mapOf("key" to largeString),
          mutationId = null,
          metadata = emptyMap()
      ).withDates(receivedDate = it.toString(), expirationDate = null)
    }
    cache.merge(newRecords, CacheHeaders.NONE, recordMerger = DefaultRecordMerger)

    val sizeBeforeTrim = cache.trim(-1)
    assertEquals(8515584, sizeBeforeTrim)

    // Trim the cache by 10%
    val sizeAfterTrim = cache.trim(8515584, 0.1f)

    assertEquals(7667712, sizeAfterTrim)
    // The oldest key must have been removed
    assertNull(cache.loadRecord("old", CacheHeaders.NONE))
  }
}
