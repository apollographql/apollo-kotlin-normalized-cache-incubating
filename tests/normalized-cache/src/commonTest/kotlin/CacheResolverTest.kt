package test

import com.apollographql.apollo.ApolloClient
import com.apollographql.cache.normalized.ApolloStore
import com.apollographql.cache.normalized.api.CacheResolver
import com.apollographql.cache.normalized.api.DefaultCacheResolver
import com.apollographql.cache.normalized.api.ResolverContext
import com.apollographql.cache.normalized.memory.MemoryCacheFactory
import com.apollographql.cache.normalized.store
import com.apollographql.cache.normalized.testing.runTest
import normalizer.HeroNameQuery
import kotlin.test.Test
import kotlin.test.assertEquals

class CacheResolverTest {
  @Test
  fun cacheResolverCanResolveQuery() = runTest {
    val resolver = object : CacheResolver {
      override fun resolveField(context: ResolverContext): Any? {
        return when (context.field.name) {
          "hero" -> mapOf("name" to "Luke")
          else -> DefaultCacheResolver.resolveField(context)
        }
      }
    }
    val apolloClient = ApolloClient.Builder().serverUrl(serverUrl = "")
        .store(
            ApolloStore(
                normalizedCacheFactory = MemoryCacheFactory(),
                cacheResolver = resolver
            )
        )
        .build()

    val response = apolloClient.query(HeroNameQuery()).execute()

    assertEquals("Luke", response.data?.hero?.name)
  }
}
