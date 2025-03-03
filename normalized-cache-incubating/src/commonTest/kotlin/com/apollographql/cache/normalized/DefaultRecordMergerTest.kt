package com.apollographql.cache.normalized

import com.apollographql.cache.normalized.api.CacheHeaders
import com.apollographql.cache.normalized.api.DefaultRecordMerger
import com.apollographql.cache.normalized.api.Record
import com.apollographql.cache.normalized.api.RecordMergerContext
import kotlin.test.Test
import kotlin.test.assertEquals

class DefaultRecordMergerTest {
  @Test
  fun mergeMetaData() {
    val existing = Record(
        key = "key",
        fields = mapOf(
            "field1" to "value1",
            "field2" to "value2"
        ),
        mutationId = null,
        metadata = mapOf(
            "field1" to mapOf(
                "field1meta1" to "field1metaValue1",
                "field1meta2" to "field1metaValue2",
            ),
            "field2" to mapOf(
                "field2meta1" to "field2metaValue1",
                "field2meta2" to "field2metaValue2",
            ),
        ),
    )

    val incoming = Record(
        key = "key",
        fields = mapOf(
            "field1" to "value1.incoming",
            "field3" to "value3",
        ),
        mutationId = null,
        metadata = mapOf(
            "field1" to mapOf(
                "field1meta1" to "field1metaValue1.incoming",
                "field1meta3" to "field1metaValue3",
            ),
            "field3" to mapOf(
                "field3meta1" to "field3metaValue1",
                "field3meta2" to "field3metaValue2",
            ),
        ),
    )

    val mergedRecord = DefaultRecordMerger.merge(RecordMergerContext(existing, incoming, CacheHeaders.NONE)).first

    val expected = Record(
        key = "key",
        fields = mapOf(
            "field1" to "value1.incoming",
            "field2" to "value2",
            "field3" to "value3",
        ),
        mutationId = null,
        metadata = mapOf(
            "field1" to mapOf(
                "field1meta1" to "field1metaValue1.incoming",
                "field1meta2" to "field1metaValue2",
                "field1meta3" to "field1metaValue3",
            ),
            "field2" to mapOf(
                "field2meta1" to "field2metaValue1",
                "field2meta2" to "field2metaValue2",
            ),
            "field3" to mapOf(
                "field3meta1" to "field3metaValue1",
                "field3meta2" to "field3metaValue2",
            ),
        ),
    )

    assertEquals(expected.fields, mergedRecord.fields)
    assertEquals(expected.metadata, mergedRecord.metadata)
  }
}
