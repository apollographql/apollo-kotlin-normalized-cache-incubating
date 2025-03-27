package com.apollographql.cache.normalized.api

import kotlin.jvm.JvmInline
import kotlin.jvm.JvmStatic

/**
 * A [CacheKey] identifies an object in the cache.
 */
@JvmInline
value class CacheKey(
    /**
     * The key of the object in the cache.
     */
    val key: String,
) {
  /**
   * Builds a [CacheKey] from a typename and a list of Strings.
   *
   * This can be used for the common case where [CacheKey] use [typename] as a namespace and [values] as a path.
   */
  constructor(typename: String, values: List<String>) : this(
      buildString {
        append(typename)
        append(":")
        values.forEach {
          append(it)
        }
      }
  )

  /**
   * Builds a [CacheKey] from a typename and a list of Strings.
   *
   * This can be used for the common case where [CacheKey] use [typename] as a namespace and [values] as a path.
   */
  constructor(typename: String, vararg values: String) : this(typename, values.toList())

  internal fun keyToString(): String {
    return key
  }

  override fun toString() = "CacheKey(${keyToString()})"

  companion object {
    private val ROOT_CACHE_KEY = CacheKey("QUERY_ROOT")

    @JvmStatic
    fun rootKey(): CacheKey {
      return ROOT_CACHE_KEY
    }
  }
}

fun CacheKey.isRootKey(): Boolean {
  return this == CacheKey.rootKey()
}

internal fun CacheKey.fieldKey(fieldName: String): String {
  return "${keyToString()}.$fieldName"
}

internal fun CacheKey.append(vararg keys: String): CacheKey {
  var cacheKey: CacheKey = this
  for (key in keys) {
    cacheKey = CacheKey("${cacheKey.key}.$key")
  }
  return cacheKey
}
