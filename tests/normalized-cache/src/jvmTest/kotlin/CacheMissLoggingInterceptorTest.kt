package test

import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.testing.internal.runTest
import com.apollographql.cache.normalized.FetchPolicy
import com.apollographql.cache.normalized.fetchPolicy
import com.apollographql.cache.normalized.internal.hashed
import com.apollographql.cache.normalized.logCacheMisses
import com.apollographql.cache.normalized.memory.MemoryCacheFactory
import com.apollographql.cache.normalized.normalizedCache
import com.apollographql.mockserver.MockServer
import com.apollographql.mockserver.enqueueString
import normalizer.HeroAppearsInQuery
import normalizer.HeroNameQuery
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * We're only doing this on the JVM because it saves time and the CacheMissLoggingInterceptor
 * touches mutable data from different threads
 */
class CacheMissLoggingInterceptorTest {

  @Test
  fun cacheMissLogging() = runTest {
    val recordedLogs = mutableListOf<String>()
    val mockServer = MockServer()
    val apolloClient = ApolloClient.Builder()
        .serverUrl(mockServer.url())
        .logCacheMisses {
          synchronized(recordedLogs) {
            recordedLogs.add(it)
          }
        }
        .normalizedCache(MemoryCacheFactory())
        .build()

    mockServer.enqueueString("""
      {
        "data": {
          "hero": {
            "name": "Luke"
          }
        }
      }
    """.trimIndent()
    )
    apolloClient.query(HeroNameQuery()).execute()
    assertNotNull(
        apolloClient.query(HeroAppearsInQuery()).fetchPolicy(FetchPolicy.CacheOnly).execute().exception
    )

    assertEquals(
        listOf(
            "Object 'QUERY_ROOT' has no field named 'hero'",
            "Object '${"hero".hashed()}' has no field named 'appearsIn'"
        ),
        recordedLogs
    )
    mockServer.close()
    apolloClient.close()
  }

  @Test
  fun logCacheMissesMustBeCalledFirst() {
    try {
      ApolloClient.Builder()
          .normalizedCache(MemoryCacheFactory())
          .logCacheMisses()
          .build()
      error("We expected an exception")
    } catch (e: Exception) {
      assertTrue(e.message?.contains("logCacheMisses() must be called before setting up your normalized cache") == true)
    }
  }
}
