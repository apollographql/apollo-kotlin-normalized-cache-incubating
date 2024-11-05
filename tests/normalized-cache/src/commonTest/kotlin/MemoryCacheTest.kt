package test

import com.apollographql.apollo.testing.internal.runTest
import com.apollographql.cache.normalized.api.CacheHeaders
import com.apollographql.cache.normalized.api.DefaultRecordMerger
import com.apollographql.cache.normalized.api.Record
import com.apollographql.cache.normalized.memory.MemoryCache
import kotlinx.coroutines.delay
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MemoryCacheTest {
  @Test
  fun testDoesNotExpireBeforeMillis() = runTest {
    val record = Record(
        key = "key",
        fields = mapOf(
            "field" to "value"
        )
    )
    val memoryCache = MemoryCache(expireAfterMillis = 200)
    memoryCache.merge(record, CacheHeaders.NONE, DefaultRecordMerger)

    val cacheRecord = checkNotNull(memoryCache.loadRecord(record.key, CacheHeaders.NONE))
    assertEquals(record.key, cacheRecord.key)
    assertEquals(record.fields, cacheRecord.fields)

    delay(250)
    assertNull(memoryCache.loadRecord(record.key, CacheHeaders.NONE))
  }
}
