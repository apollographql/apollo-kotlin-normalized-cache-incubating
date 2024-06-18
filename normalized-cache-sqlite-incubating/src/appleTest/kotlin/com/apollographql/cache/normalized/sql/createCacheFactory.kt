package com.apollographql.cache.normalized.sql

actual fun createCacheFactory(
    baseDir: String,
    withDates: Boolean,
): SqlNormalizedCacheFactory {
  return SqlNormalizedCacheFactory(name = "apolloTestDb", withDates, baseDir)
}
