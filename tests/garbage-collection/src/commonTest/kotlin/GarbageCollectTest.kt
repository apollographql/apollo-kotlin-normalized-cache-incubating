package test

import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.testing.internal.runTest
import com.apollographql.cache.normalized.ApolloStore
import com.apollographql.cache.normalized.FetchPolicy
import com.apollographql.cache.normalized.allRecords
import com.apollographql.cache.normalized.api.SchemaCoordinatesMaxAgeProvider
import com.apollographql.cache.normalized.cacheHeaders
import com.apollographql.cache.normalized.fetchPolicy
import com.apollographql.cache.normalized.garbageCollect
import com.apollographql.cache.normalized.memory.MemoryCacheFactory
import com.apollographql.cache.normalized.sql.SqlNormalizedCacheFactory
import com.apollographql.cache.normalized.store
import com.apollographql.mockserver.MockServer
import com.apollographql.mockserver.enqueueString
import test.cache.Cache
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

class GarbageCollectTest {
  @Test
  fun garbageCollect() = runTest {
    val mockServer = MockServer()
    val store = ApolloStore(MemoryCacheFactory().chain(SqlNormalizedCacheFactory())).also { it.clearAll() }
    val apolloClient = ApolloClient.Builder()
        .serverUrl(mockServer.url())
        .store(store)
        .build()
    mockServer.enqueueString(META_PROJECT_LIST_RESPONSE)
    apolloClient.query(MetaProjectListQuery())
        .fetchPolicy(FetchPolicy.NetworkOnly)
        .cacheHeaders(receivedDate(currentTimeSeconds() - 90))
        .execute()

    // (metaProjects.0.0.type).owners is stale
    // thus (metaProjects.0.0.type) is empty and removed
    // thus (metaProjects.0.0).type is a dangling reference
    // thus (metaProjects.0.0) is empty and removed
    // thus (QUERY_ROOT).metaProjects is a dangling reference
    // thus QUERY_ROOT is empty and removed
    // every other record is unreachable and removed
    val maxAgeProvider = SchemaCoordinatesMaxAgeProvider(
        Cache.maxAges,
        defaultMaxAge = 120.seconds,
    )
    store.garbageCollect(maxAgeProvider)
    val allRecords = store.accessCache { it.allRecords() }
    assertEquals(emptyMap(), allRecords)
  }

  // language=JSON
  private val META_PROJECT_LIST_RESPONSE = """
  {
    "data": {
      "metaProjects": [
        [
          {
            "__typename": "Project",
            "type": {
              "__typename": "ProjectType",
              "owners": [
                {
                  "__typename": "User",
                  "id": "0",
                  "name": "User 0"
                }
              ]
            }
          },
          {
            "__typename": "Project",
            "type": {
              "__typename": "ProjectType",
              "owners": [
                {
                  "__typename": "User",
                  "id": "1",
                  "name": "User 1"
                }
              ]
            }
          }
        ],
        [
          {
            "__typename": "Project",
            "type": {
              "__typename": "ProjectType",
              "owners": [
                {
                  "__typename": "User",
                  "id": "2",
                  "name": "User 2"
                }
              ]
            }
          }
        ]
      ]
    }
  }
  """.trimIndent()
}
