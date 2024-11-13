package com.apollographql.cache.normalized.api

import okio.Closeable

/**
 * A Factory used to construct an instance of a [NormalizedCache] configured with the custom scalar adapters set in
 * ApolloClient.Builder#addCustomScalarAdapter(ScalarType, CustomScalarAdapter).
 */
abstract class NormalizedCacheFactory : Closeable {

  /**
   * ApolloClient.Builder#addCustomScalarAdapter(ScalarType, CustomScalarAdapter).
   * @return An implementation of [NormalizedCache].
   */
  abstract fun create(): NormalizedCache
}
