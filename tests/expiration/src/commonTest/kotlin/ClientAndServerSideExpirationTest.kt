package test

import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.exception.CacheMissException
import com.apollographql.apollo.testing.internal.runTest
import com.apollographql.cache.normalized.FetchPolicy
import com.apollographql.cache.normalized.api.CacheControlCacheResolver
import com.apollographql.cache.normalized.api.MaxAge
import com.apollographql.cache.normalized.api.NormalizedCacheFactory
import com.apollographql.cache.normalized.api.SchemaCoordinatesMaxAgeProvider
import com.apollographql.cache.normalized.apolloStore
import com.apollographql.cache.normalized.cacheHeaders
import com.apollographql.cache.normalized.cacheInfo
import com.apollographql.cache.normalized.fetchPolicy
import com.apollographql.cache.normalized.maxStale
import com.apollographql.cache.normalized.memory.MemoryCacheFactory
import com.apollographql.cache.normalized.normalizedCache
import com.apollographql.cache.normalized.sql.SqlNormalizedCacheFactory
import com.apollographql.cache.normalized.storeStaleDate
import com.apollographql.mockserver.MockResponse
import com.apollographql.mockserver.MockServer
import programmatic.GetUserEmailQuery
import programmatic.GetUserNameQuery
import programmatic.GetUserQuery
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class ClientAndServerSideExpirationTest {
  @Test
  fun cacheMissesMemory() {
    cacheMisses(MemoryCacheFactory())
  }

  @Test
  fun cacheMissesSql() {
    cacheMisses(SqlNormalizedCacheFactory())
  }

  @Test
  fun cacheMissesChained() {
    cacheMisses(MemoryCacheFactory().chain(SqlNormalizedCacheFactory()))
  }

  private fun cacheMisses(normalizedCacheFactory: NormalizedCacheFactory) = runTest {
    val mockServer = MockServer()
    val client = ApolloClient.Builder()
        .normalizedCache(
            normalizedCacheFactory = normalizedCacheFactory,
            cacheResolver = CacheControlCacheResolver(
                SchemaCoordinatesMaxAgeProvider(
                    mapOf(
                        "User.email" to MaxAge.Duration(2.seconds),
                    ),
                    defaultMaxAge = 20.seconds,
                )
            )
        )
        .storeStaleDate(true)
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

    // Store data with a stale date 10s in the future, and a received date 10s in the past
    mockServer.enqueue(
        MockResponse.Builder()
            .addHeader("Cache-Control", "max-age=10")
            .body(data)
            .build()
    )
    client.query(GetUserQuery()).fetchPolicy(FetchPolicy.NetworkOnly).cacheHeaders(receivedDate(currentTimeSeconds() - 10)).execute()

    // Read User.name from cache -> it should succeed
    val userNameResponse = client.query(GetUserNameQuery()).fetchPolicy(FetchPolicy.CacheOnly).execute()
    assertTrue(userNameResponse.data?.user?.name == "John")

    // Read User.email from cache -> it should fail
    var userEmailResponse = client.query(GetUserEmailQuery()).fetchPolicy(FetchPolicy.CacheOnly).execute()
    var e = userEmailResponse.exception as CacheMissException
    assertTrue(e.stale)

    // Store data with an expired date of now, and a received date of now
    mockServer.enqueue(
        MockResponse.Builder()
            .addHeader("Cache-Control", "max-age=0")
            .body(data)
            .build()
    )
    client.query(GetUserQuery()).fetchPolicy(FetchPolicy.NetworkOnly).cacheHeaders(receivedDate(currentTimeSeconds())).execute()

    // Read User.email from cache -> it should fail
    userEmailResponse = client.query(GetUserEmailQuery()).fetchPolicy(FetchPolicy.CacheOnly).execute()
    e = userEmailResponse.exception as CacheMissException
    assertTrue(e.stale)
  }

  @Test
  fun isStaleMemory() {
    isStale(MemoryCacheFactory())
  }

  @Test
  fun isStaleSql() {
    isStale(SqlNormalizedCacheFactory())
  }

  @Test
  fun isStaleChained() {
    isStale(MemoryCacheFactory().chain(SqlNormalizedCacheFactory()))
  }

  private fun isStale(normalizedCacheFactory: NormalizedCacheFactory) = runTest {
    val mockServer = MockServer()
    val client = ApolloClient.Builder()
        .normalizedCache(
            normalizedCacheFactory = normalizedCacheFactory,
            cacheResolver = CacheControlCacheResolver(
                SchemaCoordinatesMaxAgeProvider(
                    mapOf(
                        "User.email" to MaxAge.Duration(2.seconds),
                    ),
                    defaultMaxAge = 20.seconds,
                )
            )
        )
        .storeStaleDate(true)
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

    // Store data with a stale date 10s in the future, and a received date 10s in the past
    mockServer.enqueue(
        MockResponse.Builder()
            .addHeader("Cache-Control", "max-age=10")
            .body(data)
            .build()
    )
    client.query(GetUserQuery()).fetchPolicy(FetchPolicy.NetworkOnly).cacheHeaders(receivedDate(currentTimeSeconds() - 10)).execute()

    // Read User.name from cache -> it should succeed, and not indicate that it's stale
    val userNameResponse = client.query(GetUserNameQuery()).fetchPolicy(FetchPolicy.CacheOnly).execute()
    assertTrue(userNameResponse.data?.user?.name == "John")
    assertTrue(userNameResponse.cacheInfo?.isStale == false)

    // Read User.email from cache, with a maxStale of 10 -> it should succeed but indicate that it's stale
    var userEmailResponse = client.query(GetUserEmailQuery()).fetchPolicy(FetchPolicy.CacheOnly).maxStale(10.seconds).execute()
    assertTrue(userEmailResponse.data?.user?.email == "john@doe.com")
    assertTrue(userEmailResponse.cacheInfo?.isStale == true)

    // Store data with an expired date of now, and a received date of now
    mockServer.enqueue(
        MockResponse.Builder()
            .addHeader("Cache-Control", "max-age=0")
            .body(data)
            .build()
    )
    client.query(GetUserQuery()).fetchPolicy(FetchPolicy.NetworkOnly).cacheHeaders(receivedDate(currentTimeSeconds())).execute()

    // Read User.email from cache, with a maxStale of 10 -> it should succeed but indicate that it's stale
    userEmailResponse = client.query(GetUserEmailQuery()).fetchPolicy(FetchPolicy.CacheOnly).maxStale(10.seconds).execute()
    assertTrue(userEmailResponse.data?.user?.email == "john@doe.com")
    assertTrue(userEmailResponse.cacheInfo?.isStale == true)
  }
}
