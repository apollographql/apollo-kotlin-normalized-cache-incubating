package com.apollographql.cache.normalized.sql.internal

import app.cash.sqldelight.Query
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import com.apollographql.apollo.mpp.currentTimeMillis
import com.apollographql.cache.normalized.api.Record
import com.apollographql.cache.normalized.sql.internal.record.RecordQueries
import com.apollographql.cache.normalized.sql.internal.record.SqlRecordDatabase

internal class RecordDatabase(private val driver: SqlDriver) {
  private val recordQueries: RecordQueries = SqlRecordDatabase(driver).recordQueries

  fun <T> transaction(body: () -> T): T {
    return recordQueries.transactionWithResult {
      body()
    }
  }

  /**
   * @param keys the keys of the records to select, size must be <= 999
   */
  fun selectRecords(keys: Collection<String>): List<Record> {
    return recordQueries.selectRecords(keys).executeAsList().map { RecordSerializer.deserialize(it.key, it.record) }
  }

  fun selectAllRecords(): List<Record> {
    return recordQueries.selectAllRecords().executeAsList().map { RecordSerializer.deserialize(it.key, it.record) }
  }

  fun insertOrUpdateRecord(record: Record) {
    recordQueries.insertOrUpdateRecord(key = record.key.key, record = RecordSerializer.serialize(record), updated_date = currentTimeMillis())
  }


  /**
   * @param keys the keys of the records to delete, size must be <= 999
   */
  fun deleteRecords(keys: Collection<String>) {
    recordQueries.deleteRecords(keys)
  }

  fun deleteAllRecords() {
    recordQueries.deleteAllRecords()
  }

  fun databaseSize(): Long {
    return driver.executeQuery(null, "SELECT page_count * page_size FROM pragma_page_count(), pragma_page_size();", {
      it.next()
      QueryResult.Value(it.getLong(0)!!)
    }, 0).value
  }

  fun count(): Query<Long> {
    return recordQueries.count()
  }

  fun trimByUpdatedDate(limit: Long) {
    recordQueries.trimByUpdatedDate(limit)
  }

  fun vacuum() {
    driver.execute(null, "VACUUM", 0)
  }

  fun changes(): Long {
    return recordQueries.changes().executeAsOne()
  }
}
