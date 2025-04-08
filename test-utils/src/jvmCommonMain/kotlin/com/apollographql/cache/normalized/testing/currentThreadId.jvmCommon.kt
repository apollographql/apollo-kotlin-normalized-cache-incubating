package com.apollographql.cache.normalized.testing

actual fun currentThreadId(): String {
  @Suppress("DEPRECATION")
  return Thread.currentThread().id.toString()
}
