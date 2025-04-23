package com.apollographql.cache.normalized.sql

import com.apollographql.cache.normalized.ApolloStore
import com.apollographql.cache.normalized.api.CacheHeaders
import com.apollographql.cache.normalized.api.CacheKey
import com.apollographql.cache.normalized.api.DefaultRecordMerger
import com.apollographql.cache.normalized.api.Record
import com.apollographql.cache.normalized.api.withDates
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TrimTest {
  @Test
  fun trimTest() {
    val apolloStore = ApolloStore(SqlNormalizedCacheFactory()).also { it.clearAll() }

    val largeString = "".padStart(1024, '?')

    val oldRecord = Record(
        key = CacheKey("old"),
        type = "Type",
        fields = mapOf("key" to "value"),
        mutationId = null,
        metadata = emptyMap()
    )
    apolloStore.accessCache { it.merge(oldRecord, CacheHeaders.NONE, recordMerger = DefaultRecordMerger) }

    val newRecords = 0.until(2 * 1024).map {
      Record(
          key = CacheKey("new$it"),
          type = "Type",
          fields = mapOf("key" to largeString),
          mutationId = null,
          metadata = emptyMap()
      ).withDates(receivedDate = it.toString(), expirationDate = null)
    }
    apolloStore.accessCache { it.merge(newRecords, CacheHeaders.NONE, recordMerger = DefaultRecordMerger) }

    val sizeBeforeTrim = apolloStore.trim(-1, 0.1f)
    assertEquals(8515584, sizeBeforeTrim)

    // Trim the cache by 10%
    val sizeAfterTrim = apolloStore.trim(8515584, 0.1f)

    assertEquals(7667712, sizeAfterTrim)
    // The oldest key must have been removed
    assertNull(apolloStore.accessCache { it.loadRecord(CacheKey("old"), CacheHeaders.NONE) })
  }
}
