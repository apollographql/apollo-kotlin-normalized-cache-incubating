package test

import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.exception.CacheMissException
import com.apollographql.apollo.mpp.currentTimeMillis
import com.apollographql.apollo.testing.internal.runTest
import com.apollographql.cache.normalized.FetchPolicy
import com.apollographql.cache.normalized.api.ExpirationCacheResolver
import com.apollographql.cache.normalized.api.MaxAge
import com.apollographql.cache.normalized.api.MemoryCacheFactory
import com.apollographql.cache.normalized.api.NormalizedCacheFactory
import com.apollographql.cache.normalized.api.SchemaCoordinatesMaxAgeProvider
import com.apollographql.cache.normalized.apolloStore
import com.apollographql.cache.normalized.cacheHeaders
import com.apollographql.cache.normalized.fetchPolicy
import com.apollographql.cache.normalized.normalizedCache
import com.apollographql.cache.normalized.sql.SqlNormalizedCacheFactory
import com.apollographql.cache.normalized.storeExpirationDate
import com.apollographql.mockserver.MockResponse
import com.apollographql.mockserver.MockServer
import sqlite.GetUserEmailQuery
import sqlite.GetUserNameQuery
import sqlite.GetUserQuery
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class ClientAndServerSideExpirationTest {
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
    val mockServer = MockServer()
    val client = ApolloClient.Builder()
        .normalizedCache(
            normalizedCacheFactory = normalizedCacheFactory,
            cacheResolver = ExpirationCacheResolver(
                SchemaCoordinatesMaxAgeProvider(
                    mapOf(
                        "User.email" to MaxAge.Duration(2.seconds),
                    ),
                    defaultMaxAge = 20.seconds,
                )
            )
        )
        .storeExpirationDate(true)
        .serverUrl(mockServer.url())
        .build()
    client.apolloStore.clearAll()

    val data = """
      {
        "data": {
          "user": {
            "name": "John",
            "email": "john@doe.com",
            "admin": true
          }
        }
      }
    """.trimIndent()

    // Store data with an expiration date 10s in the future, and a received date 10s in the past
    mockServer.enqueue(
        MockResponse.Builder()
            .addHeader("Cache-Control", "max-age=10")
            .body(data)
            .build()
    )
    client.query(GetUserQuery()).fetchPolicy(FetchPolicy.NetworkOnly).cacheHeaders(cacheHeaders(currentTimeMillis() / 1000 - 10)).execute()

    // Read User.name from cache -> it should succeed
    val userNameResponse = client.query(GetUserNameQuery()).fetchPolicy(FetchPolicy.CacheOnly).execute()
    assertTrue(userNameResponse.data?.user?.name == "John")

    // Read User.email from cache -> it should fail
    var userEmailResponse = client.query(GetUserEmailQuery()).fetchPolicy(FetchPolicy.CacheOnly).execute()
    var e = userEmailResponse.exception as CacheMissException
    assertTrue(e.stale)

    // Store data with an expired date of now
    mockServer.enqueue(
        MockResponse.Builder()
            .addHeader("Cache-Control", "max-age=0")
            .body(data)
            .build()
    )
    client.query(GetUserQuery()).fetchPolicy(FetchPolicy.NetworkOnly).execute()
    // Read User.name from cache -> it should fail
    userEmailResponse = client.query(GetUserEmailQuery()).fetchPolicy(FetchPolicy.CacheOnly).execute()
    e = userEmailResponse.exception as CacheMissException
    assertTrue(e.stale)
  }
}
