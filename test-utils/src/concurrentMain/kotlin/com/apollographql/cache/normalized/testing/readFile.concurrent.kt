package com.apollographql.cache.normalized.testing

import okio.FileSystem
import okio.SYSTEM

/**
 * The host filesystem
 */
actual val HostFileSystem: FileSystem
  get() = FileSystem.SYSTEM
