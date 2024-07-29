package com.apollographql.cache.normalized.sql.internal

import com.apollographql.cache.normalized.api.ApolloCacheHeaders
import com.apollographql.cache.normalized.api.Record
import com.apollographql.cache.normalized.api.internal.BlobRecordSerializer
import com.apollographql.cache.normalized.sql.internal.blob2.Blob2Queries

internal class Blob2RecordDatabase(private val blobQueries: Blob2Queries) : RecordDatabase {
  override fun select(key: String): Record? {
    return blobQueries.recordForKey(key).executeAsList()
        .map {
          BlobRecordSerializer.deserialize(it.key, it.blob)
        }
        .singleOrNull()
  }

  override fun select(keys: Collection<String>): List<Record> {
    return blobQueries.recordsForKeys(keys).executeAsList()
        .map {
          BlobRecordSerializer.deserialize(it.key, it.blob)
        }
  }

  override fun <T> transaction(noEnclosing: Boolean, body: () -> T): T {
    return blobQueries.transactionWithResult {
      body()
    }
  }

  override fun delete(key: String) {
    blobQueries.delete(key)
  }

  override fun deleteMatching(pattern: String) {
    blobQueries.deleteRecordsWithKeyMatching(pattern, "\\")
  }

  override fun deleteAll() {
    blobQueries.deleteAll()
  }

  override fun changes(): Long {
    return blobQueries.changes().executeAsOne()
  }

  override fun insert(record: Record) {
    blobQueries.insert(record.key, BlobRecordSerializer.serialize(record), record.receivedDate())
  }

  override fun update(record: Record) {
    blobQueries.update(BlobRecordSerializer.serialize(record), record.receivedDate(), record.key)
  }

  override fun selectAll(): List<Record> {
    TODO("Not yet implemented")
  }

  /**
   * The most recent of the fields' received dates.
   */
  private fun Record.receivedDate(): Long? {
    return metadata.values.mapNotNull { it[ApolloCacheHeaders.RECEIVED_DATE] as? Long }.maxOrNull()
  }
}
