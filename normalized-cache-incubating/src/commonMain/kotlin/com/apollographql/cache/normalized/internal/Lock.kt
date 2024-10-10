package com.apollographql.cache.normalized.internal

/**
 * A lock with read/write semantics where possible.
 *
 * - uses Java's `ReentrantReadWriteLock` on the JVM
 * - uses AtomicFu's [ReentrantLock] on Native (read and write are not distinguished)
 */
internal expect fun Lock(): Lock

internal interface Lock {
  fun <T> read(block: () -> T): T
  fun <T> write(block: () -> T): T
}
