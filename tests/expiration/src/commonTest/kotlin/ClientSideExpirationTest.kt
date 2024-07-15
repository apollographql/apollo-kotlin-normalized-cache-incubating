package test

import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.CustomScalarAdapters
import com.apollographql.apollo.exception.CacheMissException
import com.apollographql.apollo.mpp.currentTimeMillis
import com.apollographql.apollo.testing.internal.runTest
import com.apollographql.cache.normalized.FetchPolicy
import com.apollographql.cache.normalized.api.ApolloCacheHeaders
import com.apollographql.cache.normalized.api.CacheHeaders
import com.apollographql.cache.normalized.api.DefaultRecordMerger
import com.apollographql.cache.normalized.api.EmptyMetadataGenerator
import com.apollographql.cache.normalized.api.MemoryCacheFactory
import com.apollographql.cache.normalized.api.NormalizedCacheFactory
import com.apollographql.cache.normalized.api.ReceiveDateCacheResolver
import com.apollographql.cache.normalized.api.TypePolicyCacheKeyGenerator
import com.apollographql.cache.normalized.api.normalize
import com.apollographql.cache.normalized.apolloStore
import com.apollographql.cache.normalized.cacheHeaders
import com.apollographql.cache.normalized.fetchPolicy
import com.apollographql.cache.normalized.normalizedCache
import com.apollographql.cache.normalized.sql.SqlNormalizedCacheFactory
import com.apollographql.cache.normalized.storeReceiveDate
import sqlite.GetUserQuery
import kotlin.test.Test
import kotlin.test.assertTrue

class ClientSideExpirationTest {
  @Test
  fun memoryCache() {
    test(MemoryCacheFactory())
  }

  @Test
  fun sqlCache() {
    test(SqlNormalizedCacheFactory())
  }

  @Test
  fun chainedCache() {
    test(MemoryCacheFactory().chain(SqlNormalizedCacheFactory()))
  }

  private fun test(normalizedCacheFactory: NormalizedCacheFactory) = runTest {
    val maxAge = 10
    val client = ApolloClient.Builder()
        .normalizedCache(
            normalizedCacheFactory = normalizedCacheFactory,
            cacheKeyGenerator = TypePolicyCacheKeyGenerator,
            cacheResolver = ReceiveDateCacheResolver(maxAge),
            recordMerger = DefaultRecordMerger,
            metadataGenerator = EmptyMetadataGenerator,
        )
        .storeReceiveDate(true)
        .serverUrl("unused")
        .build()
    val query = GetUserQuery()
    val data = GetUserQuery.Data(GetUserQuery.User("John", "john@doe.com"))

    val records = query.normalize(data, CustomScalarAdapters.Empty, TypePolicyCacheKeyGenerator).values

    client.apolloStore.accessCache {
      // store records in the past
      it.merge(records, cacheHeaders(currentTimeMillis() / 1000 - 15), DefaultRecordMerger)
    }

    val e = client.query(GetUserQuery()).fetchPolicy(FetchPolicy.CacheOnly).execute().exception as CacheMissException
    assertTrue(e.stale)

    // with max stale, should succeed
    val response1 = client.query(GetUserQuery()).fetchPolicy(FetchPolicy.CacheOnly)
        .cacheHeaders(CacheHeaders.Builder().addHeader(ApolloCacheHeaders.MAX_STALE, "10").build())
        .execute()
    assertTrue(response1.data?.user?.name == "John")

    client.apolloStore.accessCache {
      // update records to be in the present
      it.merge(records, cacheHeaders(currentTimeMillis() / 1000), DefaultRecordMerger)
    }

    val response2 = client.query(GetUserQuery()).fetchPolicy(FetchPolicy.CacheOnly).execute()
    assertTrue(response2.data?.user?.name == "John")
  }

  private fun cacheHeaders(date: Long): CacheHeaders {
    return CacheHeaders.Builder().addHeader(ApolloCacheHeaders.DATE, date.toString()).build()
  }
}
