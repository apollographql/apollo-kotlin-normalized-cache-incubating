package test

import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.Error
import com.apollographql.apollo.api.Error.Location
import com.apollographql.apollo.testing.internal.runTest
import com.apollographql.cache.normalized.ApolloStore
import com.apollographql.cache.normalized.FetchPolicy
import com.apollographql.cache.normalized.fetchPolicy
import com.apollographql.cache.normalized.memory.MemoryCacheFactory
import com.apollographql.cache.normalized.sql.SqlNormalizedCacheFactory
import com.apollographql.cache.normalized.store
import com.apollographql.cache.normalized.storePartialResponses
import com.apollographql.mockserver.MockServer
import com.apollographql.mockserver.enqueueString
import okio.use
import test.fragment.UserFields
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class StorePartialResponsesTest {
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
  fun simpleMemory() = runTest(before = { setUp() }, after = { tearDown() }) {
    simple(memoryStore)
  }

  @Test
  fun simpleSql() = runTest(before = { setUp() }, after = { tearDown() }) {
    simple(sqlStore)
  }

  @Test
  fun simpleMemoryThenSql() = runTest(before = { setUp() }, after = { tearDown() }) {
    simple(memoryThenSqlStore)
  }

  private suspend fun simple(store: ApolloStore) {
    mockServer.enqueueString(
        // language=JSON
        """
          {
            "data": {
              "me": {
                "__typename": "User",
                "id": "1",
                "firstName": "John",
                "lastName": "Smith",
                "nickName": null
              }
            },
            "errors": [
              {
                "message": "'nickName' can't be reached",
                "path": ["me", "nickName"]
              }
            ]
          }
          """
    )
    ApolloClient.Builder()
        .serverUrl(mockServer.url())
        .store(store)
        .storePartialResponses(true)
        .build()
        .use { apolloClient ->
          val networkResult = apolloClient.query(MeWithNickNameQuery())
              .fetchPolicy(FetchPolicy.NetworkOnly)
              .execute()
          assertEquals(
              MeWithNickNameQuery.Data(
                  MeWithNickNameQuery.Me(
                      __typename = "User",
                      id = "1",
                      firstName = "John",
                      lastName = "Smith",
                      nickName = null
                  )
              ),
              networkResult.data
          )
          assertErrorsEquals(
              listOf(
                  Error.Builder("'nickName' can't be reached").path(listOf("me", "nickName")).build()
              ),
              networkResult.errors
          )

          val cacheResult = apolloClient.query(MeWithNickNameQuery())
              .execute()
          assertEquals(
              networkResult.data,
              cacheResult.data
          )
          assertErrorsEquals(
              networkResult.errors,
              cacheResult.errors
          )
        }
  }

  @Test
  fun listsMemory() = runTest(before = { setUp() }, after = { tearDown() }) {
    lists(memoryStore)
  }

  @Test
  fun listsSql() = runTest(before = { setUp() }, after = { tearDown() }) {
    lists(sqlStore)
  }

  @Test
  fun listsMemoryThenSql() = runTest(before = { setUp() }, after = { tearDown() }) {
    lists(memoryThenSqlStore)
  }

  private suspend fun lists(store: ApolloStore) {
    mockServer.enqueueString(
        // language=JSON
        """
          {
            "data": {
              "users": [
                {
                  "__typename": "User",
                  "id": "1",
                  "firstName": "John",
                  "lastName": "Smith",
                  "email": "jsmith@example.com"
                },
                {
                  "__typename": "User",
                  "id": "2",
                  "firstName": "Jane",
                  "lastName": "Doe",
                  "email": "jdoe@example.com"
                },
                null
              ]
            },
            "errors": [
              {
                "message": "User `3` not found",
                "path": ["users", 2]
              }
            ]
          }
          """
    )
    ApolloClient.Builder()
        .serverUrl(mockServer.url())
        .store(store)
        .storePartialResponses(true)
        .build()
        .use { apolloClient ->
          val networkResult = apolloClient.query(UsersQuery(listOf("1", "2", "3")))
              .fetchPolicy(FetchPolicy.NetworkOnly)
              .execute()
          assertEquals(
              UsersQuery.Data(
                  users = listOf(
                      UsersQuery.User(
                          __typename = "User",
                          id = "1",
                          firstName = "John",
                          lastName = "Smith",
                          email = "jsmith@example.com",
                      ),
                      UsersQuery.User(
                          __typename = "User",
                          id = "2",
                          firstName = "Jane",
                          lastName = "Doe",
                          email = "jdoe@example.com",
                      ),
                      null,
                  )
              ),
              networkResult.data
          )
          assertErrorsEquals(
              listOf(
                  Error.Builder("User `3` not found").path(listOf("users", 2)).build()
              ),
              networkResult.errors
          )

          val cacheResult = apolloClient.query(UsersQuery(listOf("1", "2", "3"))).execute()
          assertEquals(
              networkResult.data,
              cacheResult.data,
          )
          assertErrorsEquals(
              networkResult.errors,
              cacheResult.errors
          )
        }
  }

  @Test
  fun compositeMemory() = runTest(before = { setUp() }, after = { tearDown() }) {
    composite(memoryStore)
  }

  @Test
  fun compositeSql() = runTest(before = { setUp() }, after = { tearDown() }) {
    composite(sqlStore)
  }

  @Test
  fun compositeMemoryThenSql() = runTest(before = { setUp() }, after = { tearDown() }) {
    composite(memoryThenSqlStore)
  }

  private suspend fun composite(store: ApolloStore) {
    mockServer.enqueueString(
        // language=JSON
        """
          {
            "data": {
              "me": {
                "__typename": "User",
                "id": "1",
                "firstName": "John",
                "lastName": "Smith",
                "bestFriend": null,
                "projects": [
                  {
                    "__typename": "Project",
                    "lead": {
                      "__typename": "User",
                      "id": "3",
                      "firstName": "Amanda",
                      "lastName": "Brown"
                    },
                    "users": [
                      {
                        "__typename": "User",
                        "id": "4",
                        "firstName": "Alice",
                        "lastName": "White"
                      }
                    ]
                  }
                ]
              }
            },
            "errors": [
              {
                "message": "Cannot find best friend",
                "path": ["me", "bestFriend"]
              }
            ]
          }
          """
    )
    ApolloClient.Builder()
        .serverUrl(mockServer.url())
        .store(store)
        .storePartialResponses(true)
        .build()
        .use { apolloClient ->
          val networkResult = apolloClient.query(MeWithBestFriendQuery())
              .fetchPolicy(FetchPolicy.NetworkOnly)
              .execute()
          assertEquals(
              MeWithBestFriendQuery.Data(
                  MeWithBestFriendQuery.Me(
                      __typename = "User",
                      id = "1",
                      firstName = "John",
                      lastName = "Smith",
                      bestFriend = null,
                      projects = listOf(
                          MeWithBestFriendQuery.Project(
                              lead = MeWithBestFriendQuery.Lead(
                                  __typename = "User",
                                  id = "3",
                                  firstName = "Amanda",
                                  lastName = "Brown"
                              ),
                              users = listOf(
                                  MeWithBestFriendQuery.User(
                                      __typename = "User",
                                      id = "4",
                                      firstName = "Alice",
                                      lastName = "White"
                                  )
                              )
                          )
                      )
                  )
              ),
              networkResult.data
          )
          assertErrorsEquals(
              listOf(
                  Error.Builder("Cannot find best friend").path(listOf("me", "bestFriend")).build()
              ),
              networkResult.errors
          )
          val cacheResult = apolloClient.query(MeWithBestFriendQuery()).execute()
          assertEquals(
              networkResult.data,
              cacheResult.data
          )
          assertErrorsEquals(
              networkResult.errors,
              cacheResult.errors
          )
        }
  }

  @Test
  fun aliasesMemory() = runTest(before = { setUp() }, after = { tearDown() }) {
    aliases(memoryStore)
  }

  @Test
  fun aliasesSql() = runTest(before = { setUp() }, after = { tearDown() }) {
    aliases(sqlStore)
  }

  @Test
  fun aliasesMemoryThenSql() = runTest(before = { setUp() }, after = { tearDown() }) {
    aliases(memoryThenSqlStore)
  }

  private suspend fun aliases(store: ApolloStore) {
    mockServer.enqueueString(
        // language=JSON
        """
          {
            "data": {
              "project": null,
              "project2": {
                "__typename": "Project",
                "id": "44",
                "name": "Atlantis",
                "description": "The lost city of water"
              }
            },
            "errors": [
              {
                "message": "Project `42` not found",
                "path": ["project"]
              }
            ]
          }
          """
    )
    ApolloClient.Builder()
        .serverUrl(mockServer.url())
        .store(store)
        .storePartialResponses(true)
        .build()
        .use { apolloClient ->
          val networkResult = apolloClient.query(DefaultProjectQuery())
              .fetchPolicy(FetchPolicy.NetworkOnly)
              .execute()
          assertEquals(
              DefaultProjectQuery.Data(
                  project = null,
                  project2 = DefaultProjectQuery.Project2(
                      id = "44",
                      name = "Atlantis",
                      description = "The lost city of water"
                  )
              ),
              networkResult.data
          )
          assertErrorsEquals(
              listOf(
                  Error.Builder("Project `42` not found").path(listOf("project")).build()
              ),
              networkResult.errors
          )

          val cacheResult = apolloClient.query(DefaultProjectQuery()).execute()
          assertEquals(
              networkResult.data,
              cacheResult.data
          )
          assertErrorsEquals(
              networkResult.errors,
              cacheResult.errors
          )
        }
  }

  @Test
  fun fragmentsAndAliasesMemory() = runTest(before = { setUp() }, after = { tearDown() }) {
    fragmentsAndAliases(memoryStore)
  }

  @Test
  fun fragmentsAndAliasesSql() = runTest(before = { setUp() }, after = { tearDown() }) {
    fragmentsAndAliases(sqlStore)
  }

  @Test
  fun fragmentsAndAliasesMemoryThenSql() = runTest(before = { setUp() }, after = { tearDown() }) {
    fragmentsAndAliases(memoryThenSqlStore)
  }

  private fun fragmentsAndAliases(store: ApolloStore) = runTest(before = { setUp() }, after = { tearDown() }) {
    mockServer.enqueueString(
        // language=JSON
        """
        {
          "data": {
            "me": {
              "__typename": "User",
              "id": "1",
              "firstName0": "John",
              "mainProject": {
                "id": "1",
                "lead0": {
                  "id": "2",
                  "__typename": "User",
                  "firstName": "Jane"
                }
              },
              "lastName": "Smith",
              "nickName0": "JS",
              "email0": "jdoe@example.com",
              "category": {
                "code": 1,
                "name": "First"
              },
              "bestFriend0": null
            }
          },
          "errors": [
            {
              "message": "Cannot find best friend",
              "path": ["me", "bestFriend0"]
            }
          ]
        }
        """
    )
    ApolloClient.Builder()
        .serverUrl(mockServer.url())
        .store(store)
        .storePartialResponses(true)
        .build()
        .use { apolloClient ->
          val networkResult = apolloClient.query(WithFragmentsQuery())
              .fetchPolicy(FetchPolicy.NetworkOnly)
              .execute()
          assertEquals(
              WithFragmentsQuery.Data(
                  WithFragmentsQuery.Me(
                      __typename = "User",
                      id = "1",
                      firstName0 = "John",
                      mainProject = WithFragmentsQuery.MainProject(
                          id = "1",
                          lead0 = WithFragmentsQuery.Lead0(
                              id = "2",
                              __typename = "User",
                              firstName = "Jane",
                          ),
                      ),
                      onUser = WithFragmentsQuery.OnUser(
                          lastName = "Smith",
                          onUser = WithFragmentsQuery.OnUser1(
                              nickName0 = "JS"
                          ),
                          __typename = "User",
                      ),
                      userFields = UserFields(
                          email0 = "jdoe@example.com",
                          category = Category(
                              code = 1,
                              name = "First"
                          ),
                          id = "1",
                          __typename = "User",
                          bestFriend0 = null
                      ),
                  )
              ),
              networkResult.data
          )

          assertErrorsEquals(
              listOf(
                  Error.Builder("Cannot find best friend").path(listOf("me", "bestFriend0")).build()
              ),
              networkResult.errors
          )

          val cacheResult = apolloClient.query(WithFragmentsQuery()).execute()
          assertEquals(
              networkResult.data,
              cacheResult.data
          )
        }
  }
}

// TODO tests with ApolloStore directly

/**
 * Helps using assertEquals.
 */
private data class ComparableError(
    val message: String,
    val locations: List<Location>?,
    val path: List<Any>?,
)

private fun assertErrorsEquals(expected: Iterable<Error>?, actual: Iterable<Error>?) =
  assertContentEquals(expected?.map {
    ComparableError(
        message = it.message,
        locations = it.locations,
        path = it.path,
    )
  }, actual?.map {
    ComparableError(
        message = it.message,
        locations = it.locations,
        path = it.path,
    )
  })
