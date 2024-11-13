package com.apollographql.cache.normalized.sql

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.apollographql.cache.normalized.api.NormalizedCacheFactory
import com.apollographql.cache.normalized.sql.internal.createDriver
import com.apollographql.cache.normalized.sql.internal.getSchema
import java.util.Properties

/**
 * @param url Database connection URL in the form of `jdbc:sqlite:path` where `path` is either blank
 * (creating an in-memory database) or a path to a file.
 * @param properties
 */
fun SqlNormalizedCacheFactory(
    url: String,
    properties: Properties = Properties(),
): NormalizedCacheFactory = SqlNormalizedCacheFactory(JdbcSqliteDriver(url, properties), manageDriver = true)

/**
 * @param name the name of the database or null for an in-memory database
 * @param baseDir the baseDirectory where to store the database.
 * If [baseDir] does not exist, it will be created
 * If [baseDir] is a relative path, it will be interpreted relative to the current working directory
 */
fun SqlNormalizedCacheFactory(
    name: String?,
    baseDir: String?,
): NormalizedCacheFactory = SqlNormalizedCacheFactory(createDriver(name, baseDir, getSchema()), manageDriver = true)

actual fun SqlNormalizedCacheFactory(name: String?): NormalizedCacheFactory = SqlNormalizedCacheFactory(name, null)
