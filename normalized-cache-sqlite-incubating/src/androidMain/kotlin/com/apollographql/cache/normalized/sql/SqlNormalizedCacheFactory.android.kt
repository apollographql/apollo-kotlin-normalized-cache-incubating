package com.apollographql.cache.normalized.sql

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import com.apollographql.cache.normalized.api.NormalizedCacheFactory
import com.apollographql.cache.normalized.sql.internal.createDriver
import com.apollographql.cache.normalized.sql.internal.getSchema
import app.cash.sqldelight.driver.android.AndroidSqliteDriver

actual fun SqlNormalizedCacheFactory(name: String?): NormalizedCacheFactory =
  SqlNormalizedCacheFactory(createDriver(name, null, getSchema()), manageDriver = true)

/**
 * @param [name] Name of the database file, or null for an in-memory database (as per Android framework implementation).
 * @param [factory] Factory class to create instances of [SupportSQLiteOpenHelper]
 * @param [configure] Optional callback, called when the database connection is being configured, to enable features such as
 *                    write-ahead logging or foreign key support. It should not modify the database except to configure it.
 * @param [useNoBackupDirectory] Sets whether to use a no backup directory or not.
 * @param [windowSizeBytes] Size of cursor window in bytes, per [android.database.CursorWindow] (Android 28+ only), or null to use the default.
 */
@JvmOverloads
fun SqlNormalizedCacheFactory(
    context: Context,
    name: String? = "apollo.db",
    factory: SupportSQLiteOpenHelper.Factory = FrameworkSQLiteOpenHelperFactory(),
    configure: ((SupportSQLiteDatabase) -> Unit)? = null,
    useNoBackupDirectory: Boolean = false,
    windowSizeBytes: Long? = null,
): NormalizedCacheFactory = SqlNormalizedCacheFactory(
    AndroidSqliteDriver(
        getSchema(),
        context.applicationContext,
        name,
        factory,
        object : AndroidSqliteDriver.Callback(getSchema()) {
          override fun onConfigure(db: SupportSQLiteDatabase) {
            super.onConfigure(db)
            configure?.invoke(db)
          }
        },
        useNoBackupDirectory = useNoBackupDirectory,
        windowSizeBytes = windowSizeBytes,
    ),
    manageDriver = true,
)
