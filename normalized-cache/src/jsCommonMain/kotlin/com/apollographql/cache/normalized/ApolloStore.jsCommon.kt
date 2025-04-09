package com.apollographql.cache.normalized

import kotlin.reflect.KClass

internal actual fun KClass<*>.normalizedCacheName(): String {
  // qualifiedName is unsupported in JS
  return simpleName ?: toString()
}
