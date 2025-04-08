package com.apollographql.cache.normalized.testing

import kotlinx.cinterop.ExperimentalForeignApi
import platform.posix.pthread_self

@OptIn(ExperimentalForeignApi::class)
actual fun currentThreadId(): String {
  return pthread_self()?.rawValue.toString()
}
