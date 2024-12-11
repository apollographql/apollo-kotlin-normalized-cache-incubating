package test

import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.testing.internal.runTest
import com.apollographql.cache.normalized.ApolloStore
import com.apollographql.cache.normalized.FetchPolicy
import com.apollographql.cache.normalized.allRecords
import com.apollographql.cache.normalized.api.CacheKey
import com.apollographql.cache.normalized.fetchPolicy
import com.apollographql.cache.normalized.getReachableCacheKeys
import com.apollographql.cache.normalized.memory.MemoryCacheFactory
import com.apollographql.cache.normalized.removeUnreachableRecords
import com.apollographql.cache.normalized.sql.SqlNormalizedCacheFactory
import com.apollographql.cache.normalized.store
import com.apollographql.cache.normalized.storeReceiveDate
import com.apollographql.mockserver.MockServer
import com.apollographql.mockserver.enqueueString
import okio.use
import test.fragment.RepositoryFragment
import test.fragment.RepositoryFragmentImpl
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class ReachableCacheKeysTest {
  @Test
  fun getReachableCacheKeys() = runTest {
    val mockServer = MockServer()
    val store = ApolloStore(MemoryCacheFactory().chain(SqlNormalizedCacheFactory())).also { it.clearAll() }
    ApolloClient.Builder()
        .serverUrl(mockServer.url())
        .store(store)
        .storeReceiveDate(true)
        .build()
        .use { apolloClient ->
          val query = MainQuery(userIds = listOf("42", "43"))
          mockServer.enqueueString(
              // language=JSON
              """
              {
                "data": {
                  "me": {
                    "__typename": "User",
                    "id": "0",
                    "name": "John",
                    "email": "me@example.com",
                    "admin": true,
                    "repositories": [
                      {
                        "__typename": "Repository",
                        "id": "1",
                        "starGazers": [
                          {
                            "__typename": "User",
                            "id": "0"
                          }
                        ]
                      },
                      {
                        "__typename": "Repository",
                        "id": "2",
                        "starGazers": []
                      }
                    ]
                  },
                  "users": [
                    {
                      "__typename": "User",
                      "id": "42",
                      "name": "Jane",
                      "email": "jane@example.com",
                      "admin": false,
                      "repositories": [
                        {
                          "__typename": "Repository",
                          "id": "3",
                          "starGazers": []
                        },
                        {
                          "__typename": "Repository",
                          "id": "4",
                          "starGazers": []
                        }
                      ]
                    },
                    {
                      "__typename": "User",
                      "id": "43",
                      "name": "John",
                      "email": "john@example.com",
                      "admin": false,
                      "repositories": [
                        {
                          "__typename": "Repository",
                          "id": "5",
                          "starGazers": []
                        },
                        {
                          "__typename": "Repository",
                          "id": "6",
                          "starGazers": []
                        },
                        {
                          "__typename": "Repository",
                          "id": "7",
                          "starGazers": []
                        }
                      ]
                    }
                  ],
                  "repositories": [
                    {
                      "__typename": "Repository",
                      "id": "7",
                      "starGazers": []
                    },
                    {
                      "__typename": "Repository",
                      "id": "8",
                      "starGazers": []
                    }
                  ]
                }
              }
              """.trimIndent()
          )

          apolloClient.query(query).fetchPolicy(FetchPolicy.NetworkOnly).execute()
          var reachableCacheKeys = store.accessCache { it.allRecords().getReachableCacheKeys() }
          assertContentEquals(
              listOf(
                  CacheKey("QUERY_ROOT"),
                  CacheKey("Repository:8"),
                  CacheKey("Repository:7"),
                  CacheKey("User:43"),
                  CacheKey("User:42"),
                  CacheKey("User:0"),
                  CacheKey("Repository:6"),
                  CacheKey("Repository:5"),
                  CacheKey("Repository:4"),
                  CacheKey("Repository:3"),
                  CacheKey("Repository:2"),
                  CacheKey("Repository:1"),
              ),
              reachableCacheKeys
          )

          // Remove User 43, now Repositories 5 and 6 should not be reachable / 7 should still be reachable
          store.remove(CacheKey("User:43"), cascade = false)
          reachableCacheKeys = store.accessCache { it.allRecords().getReachableCacheKeys() }
          assertContentEquals(
              listOf(
                  CacheKey("QUERY_ROOT"),
                  CacheKey("Repository:8"),
                  CacheKey("Repository:7"),
                  CacheKey("User:42"),
                  CacheKey("User:0"),
                  CacheKey("Repository:4"),
                  CacheKey("Repository:3"),
                  CacheKey("Repository:2"),
                  CacheKey("Repository:1"),
              ),
              reachableCacheKeys
          )

          // Add a non-reachable Repository, reachableCacheKeys should not change
          store.writeFragment(
              RepositoryFragmentImpl(),
              CacheKey("Repository:500"),
              RepositoryFragment(id = "500", __typename = "Repository", starGazers = emptyList()),
          )
          reachableCacheKeys = store.accessCache { it.allRecords().getReachableCacheKeys() }
          assertContentEquals(
              listOf(
                  CacheKey("QUERY_ROOT"),
                  CacheKey("Repository:8"),
                  CacheKey("Repository:7"),
                  CacheKey("User:42"),
                  CacheKey("User:0"),
                  CacheKey("Repository:4"),
                  CacheKey("Repository:3"),
                  CacheKey("Repository:2"),
                  CacheKey("Repository:1"),
              ),
              reachableCacheKeys
          )
          assertEquals(
              setOf(
                  CacheKey("User:42"),
                  CacheKey("Repository:6"),
                  CacheKey("User:0"),
                  CacheKey("Repository:8"),
                  CacheKey("Repository:3"),
                  CacheKey("Repository:1"),
                  CacheKey("Repository:2"),
                  CacheKey("Repository:4"),
                  CacheKey("QUERY_ROOT"),
                  CacheKey("Repository:5"),
                  CacheKey("Repository:500"),
                  CacheKey("Repository:7"),
              ),
              store.accessCache { it.allRecords() }.keys.map { CacheKey(it) }.toSet()
          )

          // Remove unreachable records, should remove Repositories 5, 6, and 500
          val removedKeys = store.removeUnreachableRecords()
          assertEquals(
              setOf(
                  CacheKey("QUERY_ROOT"),
                  CacheKey("Repository:8"),
                  CacheKey("Repository:7"),
                  CacheKey("User:42"),
                  CacheKey("User:0"),
                  CacheKey("Repository:4"),
                  CacheKey("Repository:3"),
                  CacheKey("Repository:2"),
                  CacheKey("Repository:1"),
              ),
              store.accessCache { it.allRecords() }.keys.map { CacheKey(it) }.toSet()
          )
          assertEquals(
              setOf(
                  CacheKey("Repository:6"),
                  CacheKey("Repository:5"),
                  CacheKey("Repository:500"),
              ),
              removedKeys
          )
        }
  }
}
