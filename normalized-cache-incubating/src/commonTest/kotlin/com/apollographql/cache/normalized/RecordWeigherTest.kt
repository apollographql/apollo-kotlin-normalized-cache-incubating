package com.apollographql.cache.normalized

import com.apollographql.apollo.api.json.JsonNumber
import com.apollographql.cache.normalized.api.CacheKey
import com.apollographql.cache.normalized.api.Record
import kotlin.test.Test
import kotlin.test.assertTrue

class RecordWeigherTest {

  @Test
  fun testRecordWeigher() {
    val expectedDouble = 1.23
    val expectedLongValue = Long.MAX_VALUE
    val expectedStringValue = "StringValue"
    val expectedBooleanValue = true
    val expectedNumberValue = JsonNumber("10")
    val expectedCacheKey = CacheKey("foo")
    val expectedCacheKeyList = listOf(CacheKey("bar"), CacheKey("baz"))
    val expectedScalarList = listOf("scalarOne", "scalarTwo")
    val record = Record(
        key = "root",
        fields = mapOf(
            "double" to expectedDouble,
            "string" to expectedStringValue,
            "boolean" to expectedBooleanValue,
            "long" to expectedLongValue,
            "number" to expectedNumberValue,
            "cacheReference" to expectedCacheKey,
            "scalarList" to expectedScalarList,
            "referenceList" to expectedCacheKeyList,
        )
    )

    assertTrue(record.sizeInBytes <= 284)
    assertTrue(record.sizeInBytes >= 258) // JS takes less space, maybe for strings?
  }
}
