package com.apollographql.cache.normalized

enum class FetchPolicy {
  /**
   * Try the cache, if that failed, try the network.
   *
   * This [FetchPolicy] emits one or more [ApolloResponse]s.
   * Cache misses and network errors have [ApolloResponse.exception] set to a non-null [ApolloException]
   *
   * This is the default behaviour.
   */
  CacheFirst,

  /**
   * Only try the cache.
   *
   * This [FetchPolicy] emits one [ApolloResponse].
   * Cache misses have [ApolloResponse.exception] set to a non-null [ApolloException]
   */
  CacheOnly,

  /**
   * Try the network, if that failed, try the cache.
   *
   * This [FetchPolicy] emits one or more [ApolloResponse]s.
   * Cache misses and network errors have [ApolloResponse.exception] set to a non-null [ApolloException]
   */
  NetworkFirst,

  /**
   * Only try the network.
   *
   * This [FetchPolicy] emits one or more [ApolloResponse]s. Several [ApolloResponse]s
   * may be emitted if your [NetworkTransport] supports it, for example with `@defer`.
   * Network errors have [ApolloResponse.exception] set to a non-null [ApolloException]
   */
  NetworkOnly,

  /**
   * Try the cache, then also try the network.
   *
   * This [FetchPolicy] emits two or more [ApolloResponse]s.
   * Cache misses and network errors have [ApolloResponse.exception] set to a non-null [ApolloException]
   */
  CacheAndNetwork,
}
