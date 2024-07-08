package test

import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.ApolloResponse
import com.apollographql.apollo.api.CustomScalarAdapters
import com.apollographql.apollo.exception.CacheMissException
import com.apollographql.apollo.mpp.currentTimeMillis
import com.apollographql.apollo.testing.internal.runTest
import com.apollographql.cache.normalized.FetchPolicy
import com.apollographql.cache.normalized.api.ApolloCacheHeaders
import com.apollographql.cache.normalized.api.CacheHeaders
import com.apollographql.cache.normalized.api.DefaultRecordMerger
import com.apollographql.cache.normalized.api.EmptyMetadataGenerator
import com.apollographql.cache.normalized.api.ExpireDateCacheResolver
import com.apollographql.cache.normalized.api.ReceiveDateApolloResolver
import com.apollographql.cache.normalized.api.TypePolicyCacheKeyGenerator
import com.apollographql.cache.normalized.api.normalize
import com.apollographql.cache.normalized.apolloStore
import com.apollographql.cache.normalized.cacheHeaders
import com.apollographql.cache.normalized.fetchPolicy
import com.apollographql.cache.normalized.normalizedCache
import com.apollographql.cache.normalized.sql.SqlNormalizedCacheFactory
import com.apollographql.cache.normalized.storeExpirationDate
import com.apollographql.cache.normalized.storeReceiveDate
import com.apollographql.mockserver.MockResponse
import com.apollographql.mockserver.MockServer
import sqlite.GetUserQuery
import kotlin.test.Test
import kotlin.test.assertTrue

@Suppress("JoinDeclarationAndAssignment")
class ExpirationTest {
  @Test
  fun clientSideExpiration() = runTest {
    val maxAge = 10
    val client = ApolloClient.Builder()
        .normalizedCache(
            normalizedCacheFactory = SqlNormalizedCacheFactory(name = null, withDates = true),
            cacheKeyGenerator = TypePolicyCacheKeyGenerator,
            apolloResolver = ReceiveDateApolloResolver(maxAge),
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

  @Test
  fun serverSideExpiration() = runTest {
    val mockServer = MockServer()
    val client = ApolloClient.Builder()
        .normalizedCache(
            normalizedCacheFactory = SqlNormalizedCacheFactory(name = null, withDates = true),
            cacheKeyGenerator = TypePolicyCacheKeyGenerator,
            cacheResolver = ExpireDateCacheResolver()
        )
        .storeExpirationDate(true)
        .serverUrl(mockServer.url())
        .build()
    val query = GetUserQuery()
    val data = """
      {
        "data": {
          "user": {
            "name": "John",
            "email": "john@doe.com"
          }
        }
      }
    """.trimIndent()

    val response: ApolloResponse<GetUserQuery.Data>

    // store data with an expiration date in the future
    mockServer.enqueue(
        MockResponse.Builder()
            .addHeader("Cache-Control", "max-age=10")
            .body(data)
            .build()
    )
    client.query(query).fetchPolicy(FetchPolicy.NetworkOnly).execute()
    // read from cache -> it should succeed
    response = client.query(query).fetchPolicy(FetchPolicy.CacheOnly).execute()
    assertTrue(response.data?.user?.name == "John")

    // store expired data
    mockServer.enqueue(
        MockResponse.Builder()
            .addHeader("Cache-Control", "max-age=0")
            .body(data)
            .build()
    )
    client.query(query).fetchPolicy(FetchPolicy.NetworkOnly).execute()
    // read from cache -> it should fail
    val e = client.query(GetUserQuery()).fetchPolicy(FetchPolicy.CacheOnly).execute().exception as CacheMissException
    assertTrue(e.stale)
  }


  private fun cacheHeaders(date: Long): CacheHeaders {
    return CacheHeaders.Builder().addHeader(ApolloCacheHeaders.DATE, date.toString()).build()
  }
}
