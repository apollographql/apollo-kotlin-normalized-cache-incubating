package com.apollographql.cache.normalized

import com.apollographql.apollo.api.Error
import com.apollographql.apollo.api.json.ApolloJsonElement
import com.apollographql.apollo.exception.CacheMissException

private const val EXTENSION_CACHE_MISS_EXCEPTION = "cacheMissException"

internal fun CacheMissException.toJsonElement(): ApolloJsonElement {
  return mapOf(
      "message" to message,
      "key" to key,
      "fieldName" to fieldName,
      "stale" to stale,
  )
}

internal fun Error.Builder.cacheMissException(cacheMissException: CacheMissException) = apply {
  putExtension(EXTENSION_CACHE_MISS_EXCEPTION, cacheMissException.toJsonElement())
}

/**
 * If this [Error] represents a cache miss, returns an equivalent [CacheMissException].
 */
val Error.cacheMissException: CacheMissException?
  get() = (extensions?.get(EXTENSION_CACHE_MISS_EXCEPTION) as? Map<*, *>)?.let { jsonElement ->
    CacheMissException(
        key = jsonElement["key"] as String,
        fieldName = jsonElement["fieldName"] as String?,
        stale = jsonElement["stale"] as Boolean,
    )
  }
