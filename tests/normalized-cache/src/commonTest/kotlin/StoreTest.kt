package test

import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.exception.CacheMissException
import com.apollographql.apollo.testing.QueueTestNetworkTransport
import com.apollographql.apollo.testing.enqueueTestResponse
import com.apollographql.apollo.testing.internal.runTest
import com.apollographql.cache.normalized.ApolloStore
import com.apollographql.cache.normalized.FetchPolicy
import com.apollographql.cache.normalized.api.CacheKey
import com.apollographql.cache.normalized.api.IdCacheKeyGenerator
import com.apollographql.cache.normalized.api.IdCacheKeyResolver
import com.apollographql.cache.normalized.fetchPolicy
import com.apollographql.cache.normalized.isFromCache
import com.apollographql.cache.normalized.memory.MemoryCacheFactory
import com.apollographql.cache.normalized.store
import normalizer.CharacterNameByIdQuery
import normalizer.HeroAndFriendsNamesWithIDsQuery
import normalizer.type.Episode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Tests that write into the store programmatically.
 *
 * XXX: Do we need a client and mockServer for these tests?
 */
class StoreTest {
  private lateinit var apolloClient: ApolloClient
  private lateinit var store: ApolloStore

  private fun setUp() {
    store = ApolloStore(MemoryCacheFactory(), cacheKeyGenerator = IdCacheKeyGenerator(), cacheResolver = IdCacheKeyResolver())
    apolloClient = ApolloClient.Builder().networkTransport(QueueTestNetworkTransport()).store(store).build()
  }

  @Test
  fun removeFromStore() = runTest(before = { setUp() }) {
    storeAllFriends()
    assertFriendIsCached("1002", "Han Solo")

    // remove the root query object
    var removed = store.remove(CacheKey("Character:2001"))
    assertEquals(true, removed)

    // Trying to get the full response should fail
    assertRootNotCached()

    // put everything in the cache
    storeAllFriends()
    assertFriendIsCached("1002", "Han Solo")

    // remove a single object from the list
    removed = store.remove(CacheKey("Character:1002"))
    assertEquals(true, removed)

    // Trying to get the full response should fail
    assertRootNotCached()

    // Trying to get the object we just removed should fail
    assertFriendIsNotCached("1002")

    // Trying to get another object we did not remove should work
    assertFriendIsCached("1003", "Leia Organa")
  }

  @Test
  @Throws(Exception::class)
  fun removeMultipleFromStore() = runTest(before = { setUp() }) {
    storeAllFriends()
    assertFriendIsCached("1000", "Luke Skywalker")
    assertFriendIsCached("1002", "Han Solo")
    assertFriendIsCached("1003", "Leia Organa")

    // Now remove multiple keys
    val removed = store.remove(listOf(CacheKey("Character:1002"), CacheKey("Character:1000")))

    assertEquals(2, removed)

    // Trying to get the objects we just removed should fail
    assertFriendIsNotCached("1000")
    assertFriendIsNotCached("1002")
    assertFriendIsCached("1003", "Leia Organa")
  }

  @Test
  @Throws(Exception::class)
  fun cascadeRemove() = runTest(before = { setUp() }) {
    // put everything in the cache
    storeAllFriends()

    assertFriendIsCached("1000", "Luke Skywalker")
    assertFriendIsCached("1002", "Han Solo")
    assertFriendIsCached("1003", "Leia Organa")

    // test remove root query object
    val removed = store.remove(CacheKey("Character:2001"), true)
    assertEquals(true, removed)

    // Nothing should be cached anymore
    assertRootNotCached()
    assertFriendIsNotCached("1000")
    assertFriendIsNotCached("1002")
    assertFriendIsNotCached("1003")
  }

  @Test
  @Throws(Exception::class)
  fun directAccess() = runTest(before = { setUp() }) {
    // put everything in the cache
    storeAllFriends()

    store.accessCache {
      it.remove(CacheKey("Character:1000"), false)
    }
    assertFriendIsNotCached("1000")
  }

  @Test
  fun testNewBuilderNewStore() = runTest(before = { setUp() }) {
    storeAllFriends()
    assertFriendIsCached("1000", "Luke Skywalker")

    val newStore = ApolloStore(MemoryCacheFactory())
    val newClient = apolloClient.newBuilder().store(newStore).build()

    assertFriendIsNotCached("1000", newClient)
  }

  private suspend fun storeAllFriends() {
    val query = HeroAndFriendsNamesWithIDsQuery(Episode.NEWHOPE)
    apolloClient.enqueueTestResponse(query, HeroAndFriendsNamesWithIDsQuery.Data(
        HeroAndFriendsNamesWithIDsQuery.Hero(
            "2001",
            "R2-D2",
            listOf(
                HeroAndFriendsNamesWithIDsQuery.Friend(
                    "1000",
                    "Luke Skywalker"
                ),
                HeroAndFriendsNamesWithIDsQuery.Friend(
                    "1002",
                    "Han Solo"
                ),
                HeroAndFriendsNamesWithIDsQuery.Friend(
                    "1003",
                    "Leia Organa"
                ),
            )
        )
    )
    )
    val response = apolloClient.query(query)
        .fetchPolicy(FetchPolicy.NetworkOnly).execute()

    assertEquals(response.data?.hero?.name, "R2-D2")
    assertEquals(response.data?.hero?.friends?.size, 3)
  }

  private suspend fun assertFriendIsCached(id: String, name: String) {
    val characterResponse = apolloClient.query(CharacterNameByIdQuery(id))
        .fetchPolicy(FetchPolicy.CacheOnly)
        .execute()

    assertEquals<Any?>(true, characterResponse.isFromCache)
    assertEquals<Any?>(name, characterResponse.data?.character?.name)
  }

  private suspend fun assertFriendIsNotCached(
      id: String,
      apolloClientToUse: ApolloClient = apolloClient,
  ) {
    assertIs<CacheMissException>(
        apolloClientToUse.query(CharacterNameByIdQuery(id))
            .fetchPolicy(FetchPolicy.CacheOnly)
            .execute()
            .exception
    )
  }

  private suspend fun assertRootNotCached() {
    assertIs<CacheMissException>(
        apolloClient.query(HeroAndFriendsNamesWithIDsQuery(Episode.NEWHOPE))
            .fetchPolicy(FetchPolicy.CacheOnly)
            .execute()
            .exception
    )
  }
}
