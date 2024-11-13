package com.apollographql.cache.normalized.sql

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.apollographql.cache.normalized.api.NormalizedCache
import com.apollographql.cache.normalized.api.NormalizedCacheFactory
import com.apollographql.cache.normalized.sql.internal.Blob2RecordDatabase
import com.apollographql.cache.normalized.sql.internal.blob2.Blob2Database
import com.apollographql.cache.normalized.sql.internal.maybeCreateOrMigrateSchema
import java.io.File

/**
 * Experimental database that supports trimming at startup
 *
 * There are no backward compatibilities, DO NOT ship in a production app
 *
 * @param url Database connection URL in the form of `jdbc:sqlite:path` where `path` is either blank
 * @param maxSize if the size of the database is bigger than [maxSize] (in bytes), it will be trimmed
 * @param trimFactor the amount of trimming to do
 */
class TrimmableNormalizedCacheFactory internal constructor(
    private val url: String,
    private val maxSize: Long? = null,
    private val trimFactor: Float = 0.1f,
) : NormalizedCacheFactory() {
  private val driver = JdbcSqliteDriver(url)

  override fun create(): NormalizedCache {
    maybeCreateOrMigrateSchema(driver, Blob2Database.Schema)

    val database = Blob2Database(driver)
    val queries = database.blob2Queries
    if (maxSize != null) {
      val path = url.substringAfter("jdbc:sqlite:")
      if (path.isNotBlank()) {
        val size = File(path).length()
        if (size >= maxSize) {
          val count = queries.count().executeAsOne()
          queries.trim((count * trimFactor).toLong())
          driver.execute(null, "VACUUM", 0)
        }
      }
    }

    return SqlNormalizedCache(Blob2RecordDatabase(queries))
  }

  override fun close() {
    driver.close()
  }
}


