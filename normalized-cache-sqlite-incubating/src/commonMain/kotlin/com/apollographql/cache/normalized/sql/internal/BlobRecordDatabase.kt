package com.apollographql.cache.normalized.sql.internal

import com.apollographql.cache.normalized.api.Record
import com.apollographql.cache.normalized.sql.internal.blob.BlobQueries

internal class BlobRecordDatabase(private val blobQueries: BlobQueries) : RecordDatabase {
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

  override fun delete(keys: Collection<String>) {
    blobQueries.deleteRecords(keys)
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
    blobQueries.insert(record.key, BlobRecordSerializer.serialize(record))
  }

  override fun update(record: Record) {
    blobQueries.update(BlobRecordSerializer.serialize(record), record.key)
  }

  override fun selectAll(): List<Record> {
    return blobQueries.selectRecords().executeAsList().map {
      BlobRecordSerializer.deserialize(it.key, it.blob)
    }
  }
}
