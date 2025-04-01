package test

import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.ApolloRequest
import com.apollographql.apollo.api.ApolloResponse
import com.apollographql.apollo.api.Error
import com.apollographql.apollo.api.Operation
import com.apollographql.apollo.interceptor.ApolloInterceptor
import com.apollographql.apollo.interceptor.ApolloInterceptorChain
import com.apollographql.apollo.testing.internal.runTest
import com.apollographql.cache.normalized.FetchPolicy
import com.apollographql.cache.normalized.api.CacheKey
import com.apollographql.cache.normalized.api.NormalizedCacheFactory
import com.apollographql.cache.normalized.api.SchemaCoordinatesMaxAgeProvider
import com.apollographql.cache.normalized.apolloStore
import com.apollographql.cache.normalized.fetchFromCache
import com.apollographql.cache.normalized.fetchPolicy
import com.apollographql.cache.normalized.fetchPolicyInterceptor
import com.apollographql.cache.normalized.memory.MemoryCacheFactory
import com.apollographql.cache.normalized.normalizedCache
import com.apollographql.cache.normalized.sql.SqlNormalizedCacheFactory
import com.apollographql.cache.normalized.testing.assertErrorsEquals
import com.apollographql.cache.normalized.testing.keyToString
import com.apollographql.mockserver.MockServer
import com.apollographql.mockserver.enqueueString
import donotstore.GetUserQuery
import donotstore.cache.Cache
import kotlinx.coroutines.flow.Flow
import okio.use
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

class DoNotStoreTest {
  private lateinit var mockServer: MockServer

  private fun setUp() {
    mockServer = MockServer()
  }

  private fun tearDown() {
    mockServer.close()
  }

  @Test
  fun doNotStoreMemory() {
    doNotStore(MemoryCacheFactory())
  }

  @Test
  fun doNotStoreSql() {
    doNotStore(SqlNormalizedCacheFactory())
  }

  @Test
  fun doNotStoreChained() {
    doNotStore(MemoryCacheFactory().chain(SqlNormalizedCacheFactory()))
  }

  private fun doNotStore(normalizedCacheFactory: NormalizedCacheFactory) = runTest(before = { setUp() }, after = { tearDown() }) {
    mockServer.enqueueString(
        // language=JSON
        """
        {
          "data": {
            "user": {
              "__typename": "User",
              "id": "42",
              "name": "John",
              "email": "john@example.com",
              "sensitiveScalar": "authToken",
              "sensitiveObject": {
                "password": "password"
              },
              "project": {
                "name": "Stardust"              
              }
            }
          }
        }
        """
    )
    val maxAgeProvider = SchemaCoordinatesMaxAgeProvider(
        Cache.maxAges,
        defaultMaxAge = 20.seconds,
    )
    ApolloClient.Builder()
        .serverUrl(mockServer.url())
        .normalizedCache(
            normalizedCacheFactory = normalizedCacheFactory,
            maxAgeProvider = maxAgeProvider,
        )
        .build()
        .use { apolloClient ->
          apolloClient.apolloStore.clearAll()
          val networkResponse = apolloClient.query(GetUserQuery())
              .fetchPolicy(FetchPolicy.NetworkOnly)
              .execute()
          assertEquals(
              GetUserQuery.Data(
                  GetUserQuery.User(
                      __typename = "User",
                      id = "42",
                      name = "John",
                      email = "john@example.com",
                      sensitiveScalar = "authToken",
                      sensitiveObject = GetUserQuery.SensitiveObject("password"),
                      project = GetUserQuery.Project(
                          name = "Stardust",
                      )
                  )
              ),
              networkResponse.data
          )
          val cacheResponse = apolloClient.query(GetUserQuery())
              .fetchPolicyInterceptor(PartialCacheOnlyInterceptor)
              .execute()
          assertErrorsEquals(
              listOf(
                  Error.Builder("Object '${CacheKey("User:42").keyToString()}' has no field named 'sensitiveScalar' in the cache")
                      .path(listOf("user", "sensitiveScalar")).build(),
                  Error.Builder("Object '${CacheKey("User:42").keyToString()}' has no field named 'sensitiveObject' in the cache")
                      .path(listOf("user", "sensitiveObject")).build(),
                  Error.Builder("Object '${CacheKey("User:42").keyToString()}' has no field named 'project' in the cache")
                      .path(listOf("user", "project")).build(),
              ),
              cacheResponse.errors
          )
        }
  }
}

private val PartialCacheOnlyInterceptor = object : ApolloInterceptor {
  override fun <D : Operation.Data> intercept(request: ApolloRequest<D>, chain: ApolloInterceptorChain): Flow<ApolloResponse<D>> {
    return chain.proceed(
        request = request
            .newBuilder()
            .fetchFromCache(true)
            .build()
    )
  }
}
