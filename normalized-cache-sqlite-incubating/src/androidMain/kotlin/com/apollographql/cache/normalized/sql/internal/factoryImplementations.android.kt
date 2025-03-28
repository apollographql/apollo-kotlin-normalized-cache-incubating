package com.apollographql.cache.normalized.sql.internal

import android.os.Build
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import app.cash.sqldelight.db.QueryResult
import com.apollographql.cache.normalized.sql.ApolloInitializer
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema

internal actual fun createDriver(name: String?, baseDir: String?, schema: SqlSchema<QueryResult.Value<Unit>>): SqlDriver {
  check(baseDir == null) {
    "Apollo: Android SqlNormalizedCacheFactory doesn't support 'baseDir'"
  }
  return AndroidSqliteDriver(
      schema,
      ApolloInitializer.context,
      name,
      FrameworkSQLiteOpenHelperFactory(),
  )
}

internal actual fun maybeCreateOrMigrateSchema(driver: SqlDriver, schema: SqlSchema<QueryResult.Value<Unit>>) {
  // no-op
}

// See https://www.sqlite.org/limits.html#:~:text=Maximum%20Number%20Of%20Host%20Parameters
// and https://developer.android.com/reference/android/database/sqlite/package-summary.html
internal actual val parametersMax: Int = if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.R) {
  999
} else {
  32767
}
