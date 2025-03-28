package com.apollographql.cache.normalized.testing

import com.apollographql.cache.normalized.api.CacheKey

fun CacheKey.fieldKey(fieldName: String): String {
  return "${keyToString()}.$fieldName"
}

fun CacheKey.append(vararg keys: String): CacheKey {
  var cacheKey: CacheKey = this
  for (key in keys) {
    cacheKey = CacheKey("${cacheKey.key}.$key")
  }
  return cacheKey
}

fun CacheKey.keyToString(): String {
  return key
}
