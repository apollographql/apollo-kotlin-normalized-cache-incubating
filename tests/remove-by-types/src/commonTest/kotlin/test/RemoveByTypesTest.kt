package test

import com.apollographql.apollo.ApolloClient
import com.apollographql.cache.normalized.ApolloStore
import com.apollographql.cache.normalized.FetchPolicy
import com.apollographql.cache.normalized.allRecords
import com.apollographql.cache.normalized.fetchPolicy
import com.apollographql.cache.normalized.memory.MemoryCacheFactory
import com.apollographql.cache.normalized.sql.SqlNormalizedCacheFactory
import com.apollographql.cache.normalized.store
import com.apollographql.cache.normalized.testing.runTest
import com.apollographql.mockserver.MockServer
import com.apollographql.mockserver.enqueueString
import okio.use
import kotlin.test.Test
import kotlin.test.assertTrue

class RemoveByTypesTest {
  private lateinit var mockServer: MockServer

  private fun setUp() {
    mockServer = MockServer()
  }

  private fun tearDown() {
    mockServer.close()
  }

  private val memoryStore = ApolloStore(MemoryCacheFactory())

  private val sqlStore = ApolloStore(SqlNormalizedCacheFactory()).also { it.clearAll() }

  private val memoryThenSqlStore = ApolloStore(MemoryCacheFactory().chain(SqlNormalizedCacheFactory())).also { it.clearAll() }

  @Test
  fun removeByTypesMemory() = runTest(before = { setUp() }, after = { tearDown() }) {
    removeByTypes(memoryStore)
  }

  @Test
  fun removeByTypesSql() = runTest(before = { setUp() }, after = { tearDown() }) {
    removeByTypes(sqlStore)
  }

  @Test
  fun removeByTypesMemoryThenSql() = runTest(before = { setUp() }, after = { tearDown() }) {
    removeByTypes(memoryThenSqlStore)
  }

  private suspend fun removeByTypes(store: ApolloStore) {
    mockServer.enqueueString(
        // language=JSON
        """
          {
            "data": {
              "medias": [
                {
                  "id": "1",
                  "__typename": "Image",
                  "width": 1920
                },
                {
                  "id": "2",
                  "__typename": "Image",
                  "width": 1280
                },
                {
                  "id": "3",
                  "__typename": "Video",
                  "duration": 120
                },
                {
                  "id": "4",
                  "__typename": "Video",
                  "duration": 60
                },
                {
                  "id": "5",
                  "__typename": "Audio",
                  "sampleRate": 44100
                },
                {
                  "id": "6",
                  "__typename": "Audio",
                  "sampleRate": 48000
                }
              ]
            }
          }
          """
    )
    ApolloClient.Builder()
        .serverUrl(mockServer.url())
        .store(store)
        .build()
        .use { apolloClient ->
          apolloClient.query(MediaListQuery())
              .fetchPolicy(FetchPolicy.NetworkOnly)
              .execute()
          apolloClient.store.accessCache { cache ->
            assertTrue(cache.allRecords().any { it.value.type == "Image" })
            assertTrue(cache.allRecords().any { it.value.type == "Video" })
            assertTrue(cache.allRecords().any { it.value.type == "Audio" })
          }
          apolloClient.store.removeByTypes(listOf("Image", "Video"))
          apolloClient.store.accessCache { cache ->
            assertTrue(cache.allRecords().none { it.value.type == "Image" })
            assertTrue(cache.allRecords().none { it.value.type == "Video" })
            assertTrue(cache.allRecords().any { it.value.type == "Audio" })
          }
        }
  }
}
