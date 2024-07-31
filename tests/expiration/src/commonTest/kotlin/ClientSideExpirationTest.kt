package test

import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.CustomScalarAdapters
import com.apollographql.apollo.exception.CacheMissException
import com.apollographql.apollo.mpp.currentTimeMillis
import com.apollographql.apollo.testing.internal.runTest
import com.apollographql.cache.normalized.FetchPolicy
import com.apollographql.cache.normalized.api.ApolloCacheHeaders
import com.apollographql.cache.normalized.api.CacheHeaders
import com.apollographql.cache.normalized.api.DefaultRecordMerger
import com.apollographql.cache.normalized.api.ExpirationCacheResolver
import com.apollographql.cache.normalized.api.GlobalMaxAgeProvider
import com.apollographql.cache.normalized.api.MemoryCacheFactory
import com.apollographql.cache.normalized.api.NormalizedCacheFactory
import com.apollographql.cache.normalized.api.SchemaCoordinatesMaxAgeProvider
import com.apollographql.cache.normalized.api.TypePolicyCacheKeyGenerator
import com.apollographql.cache.normalized.api.normalize
import com.apollographql.cache.normalized.apolloStore
import com.apollographql.cache.normalized.fetchPolicy
import com.apollographql.cache.normalized.maxStale
import com.apollographql.cache.normalized.normalizedCache
import com.apollographql.cache.normalized.sql.SqlNormalizedCacheFactory
import sqlite.GetCompanyQuery
import sqlite.GetUserAdminQuery
import sqlite.GetUserEmailQuery
import sqlite.GetUserNameQuery
import sqlite.GetUserQuery
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class ClientSideExpirationTest {
  @Test
  fun globalMaxAgeMemoryCache() {
    globalMaxAge(MemoryCacheFactory())
  }

  @Test
  fun globalMaxAgeSqlCache() {
    globalMaxAge(SqlNormalizedCacheFactory())
  }

  @Test
  fun globalMaxAgeChainedCache() {
    globalMaxAge(MemoryCacheFactory().chain(SqlNormalizedCacheFactory()))
  }

  @Test
  fun schemaCoordinatesMaxAgeMemoryCache() {
    schemaCoordinatesMaxAge(MemoryCacheFactory())
  }

  @Test
  fun schemaCoordinatesMaxAgeSqlCache() {
    schemaCoordinatesMaxAge(SqlNormalizedCacheFactory())
  }

  @Test
  fun schemaCoordinatesMaxAgeChainedCache() {
    schemaCoordinatesMaxAge(MemoryCacheFactory().chain(SqlNormalizedCacheFactory()))
  }


  private fun globalMaxAge(normalizedCacheFactory: NormalizedCacheFactory) = runTest {
    val maxAge = 10
    val client = ApolloClient.Builder()
        .normalizedCache(
            normalizedCacheFactory = normalizedCacheFactory,
            cacheResolver = ExpirationCacheResolver(GlobalMaxAgeProvider(maxAge.seconds)),
        )
        .serverUrl("unused")
        .build()
    client.apolloStore.clearAll()

    val query = GetUserQuery()
    val data = GetUserQuery.Data(GetUserQuery.User("John", "john@doe.com", true))

    val records = query.normalize(data, CustomScalarAdapters.Empty, TypePolicyCacheKeyGenerator).values

    client.apolloStore.accessCache {
      // store records in the past
      it.merge(records, cacheHeaders(currentTimeMillis() / 1000 - 15), DefaultRecordMerger)
    }

    val e = client.query(GetUserQuery()).fetchPolicy(FetchPolicy.CacheOnly).execute().exception as CacheMissException
    assertTrue(e.stale)

    // with max stale, should succeed
    val response1 = client.query(GetUserQuery()).fetchPolicy(FetchPolicy.CacheOnly)
        .maxStale(10.seconds)
        .execute()
    assertTrue(response1.data?.user?.name == "John")

    client.apolloStore.accessCache {
      // update records to be in the present
      it.merge(records, cacheHeaders(currentTimeMillis() / 1000), DefaultRecordMerger)
    }

    val response2 = client.query(GetUserQuery()).fetchPolicy(FetchPolicy.CacheOnly).execute()
    assertTrue(response2.data?.user?.name == "John")
  }

  private fun schemaCoordinatesMaxAge(normalizedCacheFactory: NormalizedCacheFactory) = runTest {
    val maxAgeProvider = SchemaCoordinatesMaxAgeProvider(
        mapOf(
            "User" to 10.seconds,
            "User.name" to 5.seconds,
            "User.email" to 2.seconds,
        ),
        defaultMaxAge = 20.seconds,
    )

    val client = ApolloClient.Builder()
        .normalizedCache(
            normalizedCacheFactory = normalizedCacheFactory,
            cacheResolver = ExpirationCacheResolver(maxAgeProvider),
        )
        .serverUrl("unused")
        .build()
    client.apolloStore.clearAll()

    // Store records 25 seconds ago, more than default max age: should cache miss
    mergeCompanyQueryResults(client, 25)
    var e = client.query(GetCompanyQuery()).fetchPolicy(FetchPolicy.CacheOnly).execute().exception as CacheMissException
    assertTrue(e.stale)

    // Store records 15 seconds ago, less than default max age: should not cache miss
    mergeCompanyQueryResults(client, 15)
    // Company fields are not configured so the default max age should be used
    val companyResponse = client.query(GetCompanyQuery()).fetchPolicy(FetchPolicy.CacheOnly).execute()
    assertTrue(companyResponse.data?.company?.id == "42")


    // Store records 15 seconds ago, more than max age for User: should cache miss
    mergeUserQueryResults(client, 15)
    e = client.query(GetUserAdminQuery()).fetchPolicy(FetchPolicy.CacheOnly).execute().exception as CacheMissException
    assertTrue(e.stale)

    // Store records 5 seconds ago, less than max age for User: should not cache miss
    mergeUserQueryResults(client, 5)
    val userAdminResponse = client.query(GetUserAdminQuery()).fetchPolicy(FetchPolicy.CacheOnly).execute()
    assertTrue(userAdminResponse.data?.user?.admin == true)


    // Store records 10 seconds ago, more than max age for User.name: should cache miss
    mergeUserQueryResults(client, 10)
    e = client.query(GetUserNameQuery()).fetchPolicy(FetchPolicy.CacheOnly).execute().exception as CacheMissException
    assertTrue(e.stale)

    // Store records 2 seconds ago, less than max age for User.name: should not cache miss
    mergeUserQueryResults(client, 2)
    val userNameResponse = client.query(GetUserNameQuery()).fetchPolicy(FetchPolicy.CacheOnly).execute()
    assertTrue(userNameResponse.data?.user?.name == "John")


    // Store records 5 seconds ago, more than max age for User.email: should cache miss
    mergeUserQueryResults(client, 5)
    e = client.query(GetUserEmailQuery()).fetchPolicy(FetchPolicy.CacheOnly).execute().exception as CacheMissException
    assertTrue(e.stale)

    // Store records 1 second ago, less than max age for User.email: should not cache miss
    mergeUserQueryResults(client, 1)
    val userEmailResponse = client.query(GetUserEmailQuery()).fetchPolicy(FetchPolicy.CacheOnly).execute()
    assertTrue(userEmailResponse.data?.user?.email == "john@doe.com")
  }

  private fun mergeCompanyQueryResults(client: ApolloClient, secondsAgo: Int) {
    val data = GetCompanyQuery.Data(GetCompanyQuery.Company("42"))
    val records = GetCompanyQuery().normalize(data, CustomScalarAdapters.Empty, TypePolicyCacheKeyGenerator).values
    client.apolloStore.accessCache {
      it.merge(records, cacheHeaders(currentTimeMillis() / 1000 - secondsAgo), DefaultRecordMerger)
    }
  }

  private fun mergeUserQueryResults(client: ApolloClient, secondsAgo: Int) {
    val data = GetUserQuery.Data(GetUserQuery.User("John", "john@doe.com", true))
    val records = GetUserQuery().normalize(data, CustomScalarAdapters.Empty, TypePolicyCacheKeyGenerator).values
    client.apolloStore.accessCache {
      it.merge(records, cacheHeaders(currentTimeMillis() / 1000 - secondsAgo), DefaultRecordMerger)
    }
  }

}

fun cacheHeaders(receivedDate: Long): CacheHeaders {
  return CacheHeaders.Builder().addHeader(ApolloCacheHeaders.RECEIVED_DATE, receivedDate.toString()).build()
}
