package com.apollographql.cache.normalized.internal

internal actual fun Lock(): Lock = object : Lock {
  override fun <T> read(block: () -> T): T {
    return block()
  }

  override fun <T> write(block: () -> T): T {
    return block()
  }
}
