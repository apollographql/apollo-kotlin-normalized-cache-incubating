import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.exception.CacheMissException
import com.apollographql.apollo.testing.QueueTestNetworkTransport
import com.apollographql.apollo.testing.enqueueTestResponse
import com.apollographql.apollo.testing.internal.runTest
import com.apollographql.cache.normalized.ApolloStore
import com.apollographql.cache.normalized.FetchPolicy
import com.apollographql.cache.normalized.api.CacheKey
import com.apollographql.cache.normalized.api.Record
import com.apollographql.cache.normalized.fetchPolicy
import com.apollographql.cache.normalized.memory.MemoryCache
import com.apollographql.cache.normalized.memory.MemoryCacheFactory
import com.apollographql.cache.normalized.memoryCacheOnly
import com.apollographql.cache.normalized.sql.SqlNormalizedCache
import com.apollographql.cache.normalized.sql.SqlNormalizedCacheFactory
import com.apollographql.cache.normalized.store
import main.GetUserQuery
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class MemoryCacheOnlyTest {
  @Test
  fun memoryCacheOnlyDoesNotStoreInSqlCache() = runTest {
    val store = ApolloStore(MemoryCacheFactory().chain(SqlNormalizedCacheFactory())).also { it.clearAll() }
    val apolloClient = ApolloClient.Builder().networkTransport(QueueTestNetworkTransport()).store(store).build()
    val query = GetUserQuery()
    apolloClient.enqueueTestResponse(query, GetUserQuery.Data(GetUserQuery.User("John", "a@a.com")))
    apolloClient.query(query).memoryCacheOnly(true).execute()
    val dump: Map<KClass<*>, Map<CacheKey, Record>> = store.dump()
    assertEquals(2, dump[MemoryCache::class]!!.size)
    assertEquals(0, dump[SqlNormalizedCache::class]!!.size)
  }

  @Test
  fun memoryCacheOnlyDoesNotReadFromSqlCache() = runTest {
    val store = ApolloStore(MemoryCacheFactory().chain(SqlNormalizedCacheFactory())).also { it.clearAll() }
    val query = GetUserQuery()
    store.writeOperation(query, GetUserQuery.Data(GetUserQuery.User("John", "a@a.com")))

    val store2 = ApolloStore(MemoryCacheFactory().chain(SqlNormalizedCacheFactory()))
    val apolloClient = ApolloClient.Builder().serverUrl("unused").store(store2).build()
    // The record in is in the SQL cache, but we request not to access it
    assertIs<CacheMissException>(
        apolloClient.query(query).fetchPolicy(FetchPolicy.CacheOnly).memoryCacheOnly(true).execute().exception
    )
  }
}
