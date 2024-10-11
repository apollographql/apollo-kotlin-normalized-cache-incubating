package com.apollographql.cache.normalized.internal

import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

internal actual fun Lock(): Lock = object : Lock {
  private val lock = ReentrantReadWriteLock()

  override fun <T> read(block: () -> T): T {
    return lock.read {
      block()
    }
  }

  override fun <T> write(block: () -> T): T {
    return lock.write {
      block()
    }
  }
}
