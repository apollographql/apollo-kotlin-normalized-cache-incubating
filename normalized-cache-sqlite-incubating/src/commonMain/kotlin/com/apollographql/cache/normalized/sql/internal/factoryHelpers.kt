package com.apollographql.cache.normalized.sql.internal

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import com.apollographql.apollo.exception.apolloExceptionHandler
import com.apollographql.cache.normalized.sql.internal.record.SqlRecordDatabase

internal fun createRecordDatabase(driver: SqlDriver): RecordDatabase {
  maybeCreateOrMigrateSchema(driver, getSchema())

  val tableNames = mutableListOf<String>()

  try {
    // https://sqlite.org/forum/info/d90adfbb0a6eea88
    // The name is sqlite_schema these days but older versions use sqlite_master and sqlite_master is recognized everywhere so use that
    driver.executeQuery(null, "SELECT name FROM sqlite_master WHERE type='table' ORDER BY name;",
        { cursor ->
          while (cursor.next().value) {
            tableNames.add(cursor.getString(0) ?: "")
          }
          QueryResult.Unit
        },
        0
    )
  } catch (e: Exception) {
    apolloExceptionHandler(Exception("An exception occurred while looking up the table names", e))
    /**
     * Best effort: if we can't find any table, open the DB anyway and let's see what's happening
     */
  }

  val expectedTableName = "record"
  check(tableNames.isEmpty() || tableNames.contains(expectedTableName)) {
    "Apollo: Cannot find the '$expectedTableName' table (found '$tableNames' instead)"
  }

  try {
    // Increase the memory cache to 8 MiB
    // https://www.sqlite.org/pragma.html#pragma_cache_size
    driver.executeQuery(null, "PRAGMA cache_size = -8192;", { QueryResult.Unit }, 0)
  } catch (_: Exception) {
    // Not supported on all platforms, ignore
  }

  return RecordDatabase(driver)
}

internal fun getSchema(): SqlSchema<QueryResult.Value<Unit>> = SqlRecordDatabase.Schema
