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

  fun serialize(): String {
    return "$SERIALIZATION_TEMPLATE{${keyToString()}}"
  }

  companion object {
    // IntelliJ complains about the invalid escape but looks like JS still needs it.
    // See https://youtrack.jetbrains.com/issue/KT-47189
    @Suppress("RegExpRedundantEscape")
    private val SERIALIZATION_REGEX_PATTERN = Regex("ApolloCacheReference\\{(.*)\\}")
    private const val SERIALIZATION_TEMPLATE = "ApolloCacheReference"

    @JvmStatic
    fun deserialize(serializedCacheKey: String): CacheKey {
      val values = SERIALIZATION_REGEX_PATTERN.matchEntire(serializedCacheKey)?.groupValues
      require(values != null && values.size > 1) {
        "Not a cache reference: $serializedCacheKey Must be of the form: $SERIALIZATION_TEMPLATE{%s}"
      }
      return CacheKey(values[1])
    }

    @JvmStatic
    fun canDeserialize(value: String): Boolean {
      return SERIALIZATION_REGEX_PATTERN.matches(value)
    }

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
