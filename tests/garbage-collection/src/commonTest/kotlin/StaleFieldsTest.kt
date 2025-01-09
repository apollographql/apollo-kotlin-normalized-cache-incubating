package test

import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.mpp.currentTimeMillis
import com.apollographql.apollo.testing.internal.runTest
import com.apollographql.cache.normalized.ApolloStore
import com.apollographql.cache.normalized.FetchPolicy
import com.apollographql.cache.normalized.allRecords
import com.apollographql.cache.normalized.api.ApolloCacheHeaders
import com.apollographql.cache.normalized.api.CacheHeaders
import com.apollographql.cache.normalized.api.CacheKey
import com.apollographql.cache.normalized.api.GlobalMaxAgeProvider
import com.apollographql.cache.normalized.api.SchemaCoordinatesMaxAgeProvider
import com.apollographql.cache.normalized.cacheHeaders
import com.apollographql.cache.normalized.fetchPolicy
import com.apollographql.cache.normalized.memory.MemoryCacheFactory
import com.apollographql.cache.normalized.removeStaleFields
import com.apollographql.cache.normalized.sql.SqlNormalizedCacheFactory
import com.apollographql.cache.normalized.store
import com.apollographql.mockserver.MockServer
import com.apollographql.mockserver.enqueueString
import okio.use
import test.cache.Cache
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class StaleFieldsTest {
  @Test
  fun clientControlledRemoveFieldsMemory() = clientControlledRemoveFields(ApolloStore(MemoryCacheFactory()))

  @Test
  fun clientControlledRemoveFieldsSql() = clientControlledRemoveFields(ApolloStore(SqlNormalizedCacheFactory()))

  @Test
  fun clientControlledRemoveFieldsChained() =
    clientControlledRemoveFields(ApolloStore(MemoryCacheFactory().chain(SqlNormalizedCacheFactory())))

  private fun clientControlledRemoveFields(apolloStore: ApolloStore) = runTest {
    val mockServer = MockServer()
    val store = apolloStore.also { it.clearAll() }
    ApolloClient.Builder()
        .serverUrl(mockServer.url())
        .store(store)
        .build()
        .use { apolloClient ->
          mockServer.enqueueString(REPOSITORY_LIST_RESPONSE)
          apolloClient.query(RepositoryListQuery())
              .fetchPolicy(FetchPolicy.NetworkOnly)
              .cacheHeaders(receivedDate(currentTimeSeconds() - 60))
              .execute()

          var allRecords = store.accessCache { it.allRecords() }
          assertTrue(allRecords["Repository:0"]!!.fields.containsKey("stars"))
          assertTrue(allRecords["Repository:0"]!!.fields.containsKey("starGazers"))
          assertTrue(allRecords["Repository:1"]!!.fields.containsKey("stars"))
          assertTrue(allRecords["Repository:1"]!!.fields.containsKey("starGazers"))

          val maxAgeProvider = SchemaCoordinatesMaxAgeProvider(
              Cache.maxAges,
              defaultMaxAge = 120.seconds,
          )
          var removedFieldsAndRecords = store.removeStaleFields(maxAgeProvider)
          // Repository.stars has a max age of 60 seconds, so they should be removed / User has a max age of 90 seconds, so Repository.starGazers should be kept
          assertEquals(
              setOf(
                  "Repository:0.stars",
                  "Repository:1.stars",
              ), removedFieldsAndRecords.removedFields
          )
          assertEquals(
              emptySet(), removedFieldsAndRecords.removedRecords
          )
          allRecords = store.accessCache { it.allRecords() }
          assertFalse(allRecords["Repository:0"]!!.fields.containsKey("stars"))
          assertTrue(allRecords["Repository:0"]!!.fields.containsKey("starGazers"))
          assertFalse(allRecords["Repository:1"]!!.fields.containsKey("stars"))
          assertTrue(allRecords["Repository:1"]!!.fields.containsKey("starGazers"))

          mockServer.enqueueString(REPOSITORY_LIST_RESPONSE)
          apolloClient.query(RepositoryListQuery())
              .fetchPolicy(FetchPolicy.NetworkOnly)
              .cacheHeaders(receivedDate(currentTimeSeconds() - 90))
              .execute()
          removedFieldsAndRecords = store.removeStaleFields(maxAgeProvider)
          // Repository.stars and Repository.starGazers should be removed
          assertEquals(
              setOf(
                  "Repository:0.stars",
                  "Repository:0.starGazers",
                  "Repository:1.stars",
                  "Repository:1.starGazers",
              ), removedFieldsAndRecords.removedFields
          )
          assertEquals(
              emptySet(), removedFieldsAndRecords.removedRecords
          )
          allRecords = store.accessCache { it.allRecords() }
          assertFalse(allRecords["Repository:0"]!!.fields.containsKey("stars"))
          assertFalse(allRecords["Repository:0"]!!.fields.containsKey("starGazers"))
          assertFalse(allRecords["Repository:1"]!!.fields.containsKey("stars"))
          assertFalse(allRecords["Repository:1"]!!.fields.containsKey("starGazers"))
        }
  }

  @Test
  fun clientControlledRemoveRecordsMemory() = clientControlledRemoveRecords(ApolloStore(MemoryCacheFactory()))

  @Test
  fun clientControlledRemoveRecordsSql() = clientControlledRemoveRecords(ApolloStore(SqlNormalizedCacheFactory()))

  @Test
  fun clientControlledRemoveRecordsChained() =
    clientControlledRemoveRecords(ApolloStore(MemoryCacheFactory().chain(SqlNormalizedCacheFactory())))

  private fun clientControlledRemoveRecords(apolloStore: ApolloStore) = runTest {
    val mockServer = MockServer()
    val store = apolloStore.also { it.clearAll() }
    ApolloClient.Builder()
        .serverUrl(mockServer.url())
        .store(store)
        .build()
        .use { apolloClient ->
          mockServer.enqueueString(PROJECT_LIST_RESPONSE)
          apolloClient.query(ProjectListQuery())
              .fetchPolicy(FetchPolicy.NetworkOnly)
              .cacheHeaders(receivedDate(currentTimeSeconds() - 60))
              .execute()

          var allRecords = store.accessCache { it.allRecords() }
          assertTrue(allRecords["projects.0"]!!.fields.containsKey("velocity"))
          assertTrue(allRecords["projects.0"]!!.fields.containsKey("isUrgent"))
          assertTrue(allRecords["projects.1"]!!.fields.containsKey("velocity"))
          assertTrue(allRecords["projects.1"]!!.fields.containsKey("isUrgent"))

          val maxAgeProvider = SchemaCoordinatesMaxAgeProvider(
              Cache.maxAges,
              defaultMaxAge = 120.seconds,
          )
          var removedFieldsAndRecords = store.removeStaleFields(maxAgeProvider)
          // Project.velocity has a max age of 60 seconds, so they should be removed / Project.isUrgent has a max age of 90 seconds, so they should be kept
          assertEquals(
              setOf(
                  "projects.0.velocity",
                  "projects.1.velocity",
              ), removedFieldsAndRecords.removedFields
          )
          assertEquals(
              emptySet(), removedFieldsAndRecords.removedRecords
          )
          allRecords = store.accessCache { it.allRecords() }
          assertFalse(allRecords["projects.0"]!!.fields.containsKey("velocity"))
          assertTrue(allRecords["projects.0"]!!.fields.containsKey("isUrgent"))
          assertFalse(allRecords["projects.1"]!!.fields.containsKey("velocity"))
          assertTrue(allRecords["projects.1"]!!.fields.containsKey("isUrgent"))

          mockServer.enqueueString(PROJECT_LIST_RESPONSE)
          apolloClient.query(ProjectListQuery())
              .fetchPolicy(FetchPolicy.NetworkOnly)
              .cacheHeaders(receivedDate(currentTimeSeconds() - 90))
              .execute()
          removedFieldsAndRecords = store.removeStaleFields(maxAgeProvider)
          // Project.velocity and Project.isUrgent should be removed, their records being empty they should be removed
          assertEquals(
              setOf(
                  "projects.0.velocity",
                  "projects.0.isUrgent",
                  "projects.1.velocity",
                  "projects.1.isUrgent",
              ), removedFieldsAndRecords.removedFields
          )
          assertEquals(
              setOf(
                  CacheKey("projects.0"),
                  CacheKey("projects.1"),
              ), removedFieldsAndRecords.removedRecords
          )
          allRecords = store.accessCache { it.allRecords() }
          assertFalse(allRecords.containsKey("projects.0"))
          assertFalse(allRecords.containsKey("projects.1"))
        }
  }

  @Test
  fun serverControlledRemoveFieldsMemory() = serverControlledRemoveFields(ApolloStore(MemoryCacheFactory()))

  @Test
  fun serverControlledRemoveFieldsSql() = serverControlledRemoveFields(ApolloStore(SqlNormalizedCacheFactory()))

  @Test
  fun serverControlledRemoveFieldsChained() =
    serverControlledRemoveFields(ApolloStore(MemoryCacheFactory().chain(SqlNormalizedCacheFactory())))

  private fun serverControlledRemoveFields(apolloStore: ApolloStore) = runTest {
    val mockServer = MockServer()
    val store = apolloStore.also { it.clearAll() }
    ApolloClient.Builder()
        .serverUrl(mockServer.url())
        .store(store)
        .build()
        .use { apolloClient ->
          mockServer.enqueueString(REPOSITORY_LIST_RESPONSE)
          apolloClient.query(RepositoryListQuery())
              .fetchPolicy(FetchPolicy.NetworkOnly)
              .cacheHeaders(expirationDate(currentTimeSeconds() - 60))
              .execute()

          var allRecords = store.accessCache { it.allRecords() }
          assertTrue(allRecords["Repository:0"]!!.fields.containsKey("stars"))
          assertTrue(allRecords["Repository:0"]!!.fields.containsKey("starGazers"))
          assertTrue(allRecords["Repository:1"]!!.fields.containsKey("stars"))
          assertTrue(allRecords["Repository:1"]!!.fields.containsKey("starGazers"))

          var removedFieldsAndRecords = store.removeStaleFields(GlobalMaxAgeProvider(Duration.INFINITE))
          // Everything is stale
          assertEquals(
              setOf(
                  "Repository:0.__typename",
                  "Repository:0.id",
                  "Repository:0.stars",
                  "Repository:0.starGazers",
                  "User:0.__typename",
                  "User:0.id",
                  "User:0.name",
                  "Repository:1.__typename",
                  "Repository:1.id",
                  "Repository:1.stars",
                  "Repository:1.starGazers",
                  "User:2.__typename",
                  "User:2.id",
                  "User:2.name",
                  "QUERY_ROOT.repositories({\"first\":15})",
                  "User:1.__typename",
                  "User:1.id",
                  "User:1.name"
              ), removedFieldsAndRecords.removedFields
          )
          assertEquals(
              setOf(
                  CacheKey("Repository:0"),
                  CacheKey("Repository:1"),
                  CacheKey("User:0"),
                  CacheKey("User:1"),
                  CacheKey("User:2"),
                  CacheKey("QUERY_ROOT"),
              ), removedFieldsAndRecords.removedRecords
          )
          allRecords = store.accessCache { it.allRecords() }
          assertTrue(allRecords.isEmpty())

          mockServer.enqueueString(REPOSITORY_LIST_RESPONSE)
          apolloClient.query(RepositoryListQuery())
              .fetchPolicy(FetchPolicy.NetworkOnly)
              .cacheHeaders(expirationDate(currentTimeSeconds() - 60))
              .execute()

          removedFieldsAndRecords = store.removeStaleFields(GlobalMaxAgeProvider(Duration.INFINITE), maxStale = 70.seconds)
          // Nothing is stale
          assertEquals(
              emptySet(),
              removedFieldsAndRecords.removedFields
          )
          assertEquals(
              emptySet(),
              removedFieldsAndRecords.removedRecords
          )
          allRecords = store.accessCache { it.allRecords() }
          assertTrue(allRecords["Repository:0"]!!.fields.containsKey("stars"))
          assertTrue(allRecords["Repository:0"]!!.fields.containsKey("starGazers"))
          assertTrue(allRecords["Repository:1"]!!.fields.containsKey("stars"))
          assertTrue(allRecords["Repository:1"]!!.fields.containsKey("starGazers"))
        }
  }

  // language=JSON
  private val REPOSITORY_LIST_RESPONSE = """
  {
    "data": {
      "repositories": [
        {
          "__typename": "Repository",
          "id": "0",
          "stars": 10,
          "starGazers": [
            {
              "__typename": "User",
              "id": "0",
              "name": "John"
            },
            {
              "__typename": "User",
              "id": "1",
              "name": "Jane"
            }
          ]
        },
        {
          "__typename": "Repository",
          "id": "1",
          "stars": 20,
          "starGazers": [
            {
              "__typename": "User",
              "id": "0",
              "name": "John"
            },
            {
              "__typename": "User",
              "id": "2",
              "name": "Alice"
            }
          ]
        }
      ]
    }
  }
  """.trimIndent()

  // language=JSON
  private val PROJECT_LIST_RESPONSE = """
  {
    "data": {
      "projects": [
        {
          "__typename": "Project",
          "velocity": 12,
          "isUrgent": true
        },
        {
          "__typename": "Project",
          "velocity": 3,
          "isUrgent": false
        }
      ]
    }
  }
  """.trimIndent()
}

fun currentTimeSeconds() = currentTimeMillis() / 1000

fun receivedDate(receivedDateSeconds: Long): CacheHeaders {
  return CacheHeaders.Builder().addHeader(ApolloCacheHeaders.RECEIVED_DATE, receivedDateSeconds.toString()).build()
}

fun expirationDate(expirationDateSeconds: Long): CacheHeaders {
  return CacheHeaders.Builder().addHeader(ApolloCacheHeaders.EXPIRATION_DATE, expirationDateSeconds.toString()).build()
}
