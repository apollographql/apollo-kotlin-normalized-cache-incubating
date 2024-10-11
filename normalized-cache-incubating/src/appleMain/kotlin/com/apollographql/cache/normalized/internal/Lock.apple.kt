package com.apollographql.cache.normalized.internal

import kotlinx.atomicfu.locks.ReentrantLock
import kotlinx.atomicfu.locks.reentrantLock
import kotlinx.atomicfu.locks.withLock

internal actual fun Lock(): Lock = object : Lock {
  private val lock: ReentrantLock = reentrantLock()

  override fun <T> read(block: () -> T): T {
    return lock.withLock(block)
  }

  override fun <T> write(block: () -> T): T {
    return lock.withLock(block)
  }
}
