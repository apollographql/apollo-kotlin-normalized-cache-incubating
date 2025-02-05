package test

import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.Error
import com.apollographql.apollo.api.Error.Location
import com.apollographql.apollo.testing.internal.runTest
import com.apollographql.cache.normalized.ApolloStore
import com.apollographql.cache.normalized.FetchPolicy
import com.apollographql.cache.normalized.api.CacheControlCacheResolver
import com.apollographql.cache.normalized.api.CacheKey
import com.apollographql.cache.normalized.api.IdCacheKeyGenerator
import com.apollographql.cache.normalized.api.IdCacheKeyResolver
import com.apollographql.cache.normalized.api.SchemaCoordinatesMaxAgeProvider
import com.apollographql.cache.normalized.apolloStore
import com.apollographql.cache.normalized.fetchPolicy
import com.apollographql.cache.normalized.memory.MemoryCacheFactory
import com.apollographql.cache.normalized.normalizedCache
import com.apollographql.cache.normalized.returnPartialResponses
import com.apollographql.cache.normalized.store
import com.apollographql.cache.normalized.storePartialResponses
import com.apollographql.cache.normalized.storeReceiveDate
import com.apollographql.mockserver.MockServer
import com.apollographql.mockserver.enqueueString
import okio.use
import test.cache.Cache
import test.fragment.UserFields
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration

class CachePartialResultTest {
  private lateinit var mockServer: MockServer

  private fun setUp() {
    mockServer = MockServer()
  }

  private fun tearDown() {
    mockServer.close()
  }

  @Test
  fun simple() = runTest(before = { setUp() }, after = { tearDown() }) {
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
              "email": "jsmith@example.com"
            }
          }
        }
        """
    )
    ApolloClient.Builder()
        .serverUrl(mockServer.url())
        .normalizedCache(MemoryCacheFactory())
        .returnPartialResponses(true)
        .build()
        .use { apolloClient ->
          val networkResult = apolloClient.query(MeWithoutNickNameWithEmailQuery())
              .fetchPolicy(FetchPolicy.NetworkOnly)
              .execute()
          assertEquals(
              MeWithoutNickNameWithEmailQuery.Data(
                  MeWithoutNickNameWithEmailQuery.Me(
                      __typename = "User",
                      firstName = "John",
                      lastName = "Smith",
                      email = "jsmith@example.com",
                      id = "1",
                      onUser = MeWithoutNickNameWithEmailQuery.OnUser(
                          id = "1"
                      )
                  )
              ),
              networkResult.data
          )

          val cacheResult = apolloClient.query(MeWithoutNickNameWithoutEmailQuery())
              .fetchPolicy(FetchPolicy.CacheOnly)
              .execute()
          assertEquals(
              MeWithoutNickNameWithoutEmailQuery.Data(
                  MeWithoutNickNameWithoutEmailQuery.Me(
                      id = "1",
                      firstName = "John",
                      lastName = "Smith",
                      __typename = "User"
                  )
              ),
              cacheResult.data
          )

          val cacheMissResult = apolloClient.query(MeWithNickNameQuery())
              .fetchPolicy(FetchPolicy.CacheOnly)
              .execute()
          assertEquals(
              MeWithNickNameQuery.Data(
                  MeWithNickNameQuery.Me(
                      id = "1",
                      firstName = "John",
                      lastName = "Smith",
                      nickName = null,
                      __typename = "User"
                  )
              ),
              cacheMissResult.data
          )
          assertErrorsEquals(
              listOf(
                  Error.Builder("Object 'User:1' has no field named 'nickName' in the cache").path(listOf("me", "nickName")).build()
              ),
              cacheMissResult.errors
          )
        }
  }

  @Test
  fun lists() = runTest(before = { setUp() }, after = { tearDown() }) {
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
        .store(
            ApolloStore(
                normalizedCacheFactory = MemoryCacheFactory(),
                cacheKeyGenerator = IdCacheKeyGenerator(),
                cacheResolver = IdCacheKeyResolver()
            )
        )
        .returnPartialResponses(true)
        .build()
        .use { apolloClient ->
          val networkResult = apolloClient.query(UsersQuery(listOf("1", "2", "3")))
              .fetchPolicy(FetchPolicy.NetworkOnly)
              .storePartialResponses(true)
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

          val cacheResult = apolloClient.query(UsersQuery(listOf("1", "2", "3")))
              .fetchPolicy(FetchPolicy.CacheOnly)
              .execute()
          assertEquals(
              networkResult.data,
              cacheResult.data,
          )
          assertErrorsEquals(
              listOf(
                  Error.Builder("Object 'User:3' not found in the cache").path(listOf("users", 2)).build()
              ),
              cacheResult.errors
          )
        }
  }

  @Test
  fun composite() = runTest(before = { setUp() }, after = { tearDown() }) {
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
              "bestFriend": {
                "__typename": "User",
                "id": "2",
                "firstName": "Jane",
                "lastName": "Doe"
              },
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
          }
        }
        """
    )
    ApolloClient.Builder()
        .serverUrl(mockServer.url())
        .normalizedCache(MemoryCacheFactory())
        .returnPartialResponses(true)
        .build()
        .use { apolloClient ->
          // Prime the cache
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
                      bestFriend = MeWithBestFriendQuery.BestFriend(
                          __typename = "User",
                          id = "2",
                          firstName = "Jane",
                          lastName = "Doe"
                      ),
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

          // Remove project lead from the cache
          apolloClient.apolloStore.remove(CacheKey("User", "3"))
          val cacheResult = apolloClient.query(MeWithBestFriendQuery())
              .fetchPolicy(FetchPolicy.CacheOnly)
              .execute()
          assertEquals(
              MeWithBestFriendQuery.Data(
                  MeWithBestFriendQuery.Me(
                      __typename = "User",
                      id = "1",
                      firstName = "John",
                      lastName = "Smith",
                      bestFriend = MeWithBestFriendQuery.BestFriend(
                          __typename = "User",
                          id = "2",
                          firstName = "Jane",
                          lastName = "Doe"
                      ),
                      projects = listOf(
                          MeWithBestFriendQuery.Project(
                              lead = null,
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
              cacheResult.data
          )
          assertErrorsEquals(
              listOf(
                  Error.Builder("Object 'User:3' not found in the cache").path(listOf("me", "projects", 0, "lead")).build()
              ),
              cacheResult.errors
          )

          // Remove best friend from the cache
          apolloClient.apolloStore.remove(CacheKey("User", "2"))
          val cacheResult2 = apolloClient.query(MeWithBestFriendQuery())
              .fetchPolicy(FetchPolicy.CacheOnly)
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
                              lead = null,
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
              cacheResult2.data
          )
          assertErrorsEquals(
              listOf(
                  Error.Builder("Object 'User:2' not found in the cache").path(listOf("me", "bestFriend")).build(),
                  Error.Builder("Object 'User:3' not found in the cache").path(listOf("me", "projects", 0, "lead")).build(),
              ),
              cacheResult2.errors
          )

          // Remove project user from the cache
          apolloClient.apolloStore.remove(CacheKey("User", "4"))
          val cacheResult3 = apolloClient.query(MeWithBestFriendQuery())
              .fetchPolicy(FetchPolicy.CacheOnly)
              .execute()
          // Due to null bubbling the whole data is null
          assertNull(cacheResult3.data)
          assertErrorsEquals(
              listOf(
                  Error.Builder("Object 'User:2' not found in the cache").path(listOf("me", "bestFriend")).build(),
                  Error.Builder("Object 'User:3' not found in the cache").path(listOf("me", "projects", 0, "lead")).build(),
                  Error.Builder("Object 'User:4' not found in the cache").path(listOf("me", "projects", 0, "users", 0)).build()
              ),
              cacheResult3.errors
          )
        }
  }

  @Test
  fun argumentsAndAliases() = runTest(before = { setUp() }, after = { tearDown() }) {
    mockServer.enqueueString(
        // language=JSON
        """
        {
          "data": {
            "project": {
              "__typename": "Project",
              "id": "42",
              "name": "El Dorado",
              "description": "The lost city of gold"
            },
            "project2": {
              "__typename": "Project",
              "id": "44",
              "name": "Atlantis",
              "description": "The lost city of water"
            }
          }
        }
        """
    )
    ApolloClient.Builder()
        .serverUrl(mockServer.url())
        .store(
            ApolloStore(
                normalizedCacheFactory = MemoryCacheFactory(),
                cacheKeyGenerator = IdCacheKeyGenerator(),
                cacheResolver = IdCacheKeyResolver()
            )
        )
        .returnPartialResponses(true)
        .build()
        .use { apolloClient ->
          val networkResult = apolloClient.query(DefaultProjectQuery())
              .fetchPolicy(FetchPolicy.NetworkOnly)
              .execute()
          assertEquals(
              DefaultProjectQuery.Data(
                  project = DefaultProjectQuery.Project(
                      id = "42",
                      name = "El Dorado",
                      description = "The lost city of gold"
                  ),
                  project2 = DefaultProjectQuery.Project2(
                      id = "44",
                      name = "Atlantis",
                      description = "The lost city of water"
                  )
              ),
              networkResult.data
          )

          val cacheResult = apolloClient.query(DefaultProjectQuery())
              .fetchPolicy(FetchPolicy.CacheOnly)
              .execute()
          assertEquals(
              networkResult.data,
              cacheResult.data
          )
        }
  }

  @Test
  fun customScalar() = runTest(before = { setUp() }, after = { tearDown() }) {
    mockServer.enqueueString(
        // language=JSON
        """
        {
          "data": {
            "user": {
              "__typename": "User",
              "id": "1",
              "firstName": "John",
              "lastName": "Smith",
              "category": {
                "code": 1,
                "name": "First"
              }
            }
          }
        }
        """
    )
    ApolloClient.Builder()
        .serverUrl(mockServer.url())
        .normalizedCache(MemoryCacheFactory())
        .returnPartialResponses(true)
        .build()
        .use { apolloClient ->
          val networkResult = apolloClient.query(UserByCategoryQuery(Category(2, "Second")))
              .fetchPolicy(FetchPolicy.NetworkOnly)
              .execute()
          assertEquals(
              UserByCategoryQuery.Data(
                  UserByCategoryQuery.User(

                      firstName = "John",
                      lastName = "Smith",
                      category = Category(
                          code = 1,
                          name = "First"
                      ),
                      id = "1",
                      __typename = "User",
                  )
              ),
              networkResult.data
          )

          val cacheResult = apolloClient.query(UserByCategoryQuery(Category(2, "Second")))
              .fetchPolicy(FetchPolicy.CacheOnly)
              .execute()
          assertEquals(
              networkResult.data,
              cacheResult.data
          )
        }
  }

  @Test
  fun fragmentsAndAliases() = runTest(before = { setUp() }, after = { tearDown() }) {
    mockServer.enqueueString(
        // language=JSON
        """
        {
          "data": {
            "me": {
              "__typename": "User",
              "id": "1",
              "firstName0": "John",
              "lastName": "Smith",
              "nickName0": "JS",
              "email0": "jdoe@example.com",
              "category": {
                "code": 1,
                "name": "First"
              }
            }
          }
        }
        """
    )
    ApolloClient.Builder()
        .serverUrl(mockServer.url())
        .normalizedCache(MemoryCacheFactory())
        .returnPartialResponses(true)
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
                      ),
                  )
              ),
              networkResult.data
          )

          val cacheResult = apolloClient.query(WithFragmentsQuery())
              .fetchPolicy(FetchPolicy.CacheOnly)
              .execute()
          assertEquals(
              networkResult.data,
              cacheResult.data
          )
        }
  }

  @Test
  fun cacheControl() = runTest(before = { setUp() }, after = { tearDown() }) {
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
              "nickName": "JS"
            }
          }
        }
        """
    )
    ApolloClient.Builder()
        .serverUrl(mockServer.url())
        .normalizedCache(MemoryCacheFactory(), cacheResolver = CacheControlCacheResolver(SchemaCoordinatesMaxAgeProvider(Cache.maxAges, Duration.INFINITE)))
        .storeReceiveDate(true)
        .returnPartialResponses(true)
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
                      nickName = "JS"
                  )
              ),
              networkResult.data
          )

          val cacheMissResult = apolloClient.query(MeWithNickNameQuery())
              .fetchPolicy(FetchPolicy.CacheOnly)
              .execute()
          assertEquals(
              MeWithNickNameQuery.Data(
                  MeWithNickNameQuery.Me(
                      id = "1",
                      firstName = "John",
                      lastName = "Smith",
                      nickName = null,
                      __typename = "User"
                  )
              ),
              cacheMissResult.data
          )
          assertErrorsEquals(
              listOf(
                  Error.Builder("Field 'User:1' on object 'nickName' is stale in the cache").path(listOf("me", "nickName")).build()
              ),
              cacheMissResult.errors
          )
        }
  }
}

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
