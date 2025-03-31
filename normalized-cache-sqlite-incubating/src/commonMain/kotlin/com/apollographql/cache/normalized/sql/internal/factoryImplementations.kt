package com.apollographql.cache.normalized.sql.internal

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema


internal expect fun createDriver(name: String?, baseDir: String?, schema: SqlSchema<QueryResult.Value<Unit>>): SqlDriver

/**
 * Some implementations like Native and Android take the schema when creating the driver and the driver
 * will take care of migrations
 *
 * Others like JVM don't do this automatically. This is when [maybeCreateOrMigrateSchema] is needed
 */
internal expect fun maybeCreateOrMigrateSchema(driver: SqlDriver, schema: SqlSchema<QueryResult.Value<Unit>>)

// See https://www.sqlite.org/limits.html#:~:text=Maximum%20Number%20Of%20Host%20Parameters
internal expect val parametersMax: Int
