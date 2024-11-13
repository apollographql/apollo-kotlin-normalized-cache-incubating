package com.apollographql.cache.normalized.sql

import com.apollographql.cache.normalized.api.NormalizedCacheFactory
import com.apollographql.cache.normalized.sql.internal.createDriver
import com.apollographql.cache.normalized.sql.internal.getSchema

/**
 * @param name the name of the database or null for an in-memory database
 * @param baseDir the baseDirectory where to store the database.
 * [baseDir] must exist and be a directory
 * If [baseDir] is a relative path, it will be interpreted relative to the current working directory
 */
fun SqlNormalizedCacheFactory(
    name: String?,
    baseDir: String?,
): NormalizedCacheFactory = SqlNormalizedCacheFactory(createDriver(name, baseDir, getSchema()), manageDriver = true)

actual fun SqlNormalizedCacheFactory(name: String?): NormalizedCacheFactory = SqlNormalizedCacheFactory(name, null)
