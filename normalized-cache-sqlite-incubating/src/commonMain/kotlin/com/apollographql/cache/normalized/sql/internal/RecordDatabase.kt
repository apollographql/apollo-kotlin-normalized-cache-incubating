package com.apollographql.cache.normalized.sql.internal

import app.cash.sqldelight.Query
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import com.apollographql.apollo.api.json.ApolloJsonElement
import com.apollographql.cache.normalized.api.ApolloCacheHeaders
import com.apollographql.cache.normalized.api.Record
import com.apollographql.cache.normalized.api.expirationDate
import com.apollographql.cache.normalized.api.receivedDate
import com.apollographql.cache.normalized.sql.internal.fields.Field_
import com.apollographql.cache.normalized.sql.internal.fields.FieldsDatabase
import com.apollographql.cache.normalized.sql.internal.fields.FieldsQueries

internal class RecordDatabase(private val driver: SqlDriver) {
  private val fieldsQueries: FieldsQueries = FieldsDatabase(driver).fieldsQueries

  fun <T> transaction(body: () -> T): T {
    return fieldsQueries.transactionWithResult {
      body()
    }
  }

  /**
   * @param keys the keys of the records to select
   */
  fun selectRecords(keys: Collection<String>): List<Record> {
    // SQLite has a limitation of 999 parameters per query
    val fieldsByRecordKey: Map<String, List<Field_>> = keys.chunked(999).flatMap { chunkedKeys ->
      fieldsQueries.selectRecords(chunkedKeys).executeAsList()
    }.groupBy { it.record }
    return fieldsByRecordKey.toRecords()
  }

  /**
   * @param keysAndFields the keys and fields of the records to select
   */
  fun selectRecords(keysAndFields: Map<String, Set<String>>): List<Record> {
    val mapper: (SqlCursor) -> Field_ = { cursor ->
      Field_(
          record = cursor.getString(0)!!,
          field_ = cursor.getString(1)!!,
          value_ = cursor.getBytes(2),
          metadata = cursor.getBytes(3),
          received_date = cursor.getLong(4),
          expiration_date = cursor.getLong(5),
      )
    }
    val filters = keysAndFields.flatMap { (record, fields) ->
      fields.map { field -> record to field }
    }
    // SQLite has a limitation of 999 parameters per query. Since each filter is 2 parameters, we chunk to half of that.
    val fieldsByRecordKey = filters.chunked(499).flatMap { chunkedFilters ->
      SelectRecordsWithFilterQuery(chunkedFilters, mapper).executeAsList()
    }.groupBy { it.record }
    return fieldsByRecordKey.toRecords()
  }

  private inner class SelectRecordsWithFilterQuery<out T : Any>(
      private val filters: List<Pair<String, String>>,
      mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Listener) {
      driver.addListener("field", listener = listener)
    }

    override fun removeListener(listener: Listener) {
      driver.removeListener("field", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> {
      val placeholders = filters.joinToString(", ") { "(?, ?)" }
      val sql = """
        WITH filter(record, field) AS (VALUES $placeholders)
        SELECT f.record, f.field, f.value, f.metadata, f.received_date, f.expiration_date
        FROM field f
        JOIN filter ON f.record = filter.record AND f.field = filter.field
      """.trimIndent()

      val parameters = filters.flatMap { listOf(it.first, it.second) }
      return driver.executeQuery(
          identifier = null,
          sql = sql,
          mapper = mapper,
          parameters = parameters.size,
          binders = {
            for ((index, parameter) in parameters.withIndex()) {
              bindString(index, parameter)
            }
          }
      )
    }
  }

  fun selectAllRecords(): List<Record> {
    val fieldsByRecordKey: Map<String, List<Field_>> = fieldsQueries.selectAllRecords().executeAsList().groupBy { it.record }
    return fieldsByRecordKey.toRecords()
  }

  private fun Map<String, List<Field_>>.toRecords(): List<Record> =
    mapValues { (key, fieldList) ->
      val fields: Map<String, ApolloJsonElement> =
        fieldList.associate { field -> field.field_ to ApolloJsonElementSerializer.deserialize(field.value_) }

      @Suppress("UNCHECKED_CAST")
      val metadata: Map<String, Map<String, ApolloJsonElement>> =
        fieldList.associate { field ->
          field.field_ to (ApolloJsonElementSerializer.deserialize(field.metadata) as Map<String, ApolloJsonElement>?).orEmpty() +
              buildMap {
                // Dates are stored separately in their own columns
                if (field.received_date != null) {
                  put(ApolloCacheHeaders.RECEIVED_DATE, field.received_date)
                }
                if (field.expiration_date != null) {
                  put(ApolloCacheHeaders.EXPIRATION_DATE, field.expiration_date)
                }
              }
        }.filterValues { it.isNotEmpty() }
      Record(
          key = key,
          fields = fields,
          metadata = metadata,
      )
    }.values.toList()

  fun insertOrUpdateRecord(record: Record) {
    for ((field, value) in record.fields) {
      insertOrUpdateField(
          record = record.key,
          field = field,
          value = value,
          metadata = record.metadata[field],
          receivedDate = record.receivedDate(field),
          expirationDate = record.expirationDate(field),
      )
    }
  }

  private fun insertOrUpdateField(
      record: String,
      field: String,
      value: ApolloJsonElement,
      metadata: Map<String, ApolloJsonElement>?,
      receivedDate: Long?,
      expirationDate: Long?,
  ) {
    fieldsQueries.insertOrUpdateField(
        record = record,
        field_ = field,
        value_ = ApolloJsonElementSerializer.serialize(value),
        metadata = metadata
            ?.takeIf { it.isNotEmpty() }
            ?.let {
              ApolloJsonElementSerializer.serialize(
                  // Don't store the dates in the metadata as they are stored separately in their own columns
                  it - ApolloCacheHeaders.RECEIVED_DATE - ApolloCacheHeaders.EXPIRATION_DATE
              )
            },
        received_date = receivedDate,
        expiration_date = expirationDate,
    )
  }

  /**
   * @param keys the keys of the records to delete, size must be <= 999
   */
  fun deleteRecords(keys: Collection<String>) {
    fieldsQueries.deleteRecords(keys)
  }

  fun deleteRecordsMatching(pattern: String) {
    fieldsQueries.deleteRecordsMatching(pattern)
  }

  fun deleteAllRecords() {
    fieldsQueries.deleteAllRecords()
  }

  fun databaseSize(): Long {
    return driver.executeQuery(null, "SELECT page_count * page_size FROM pragma_page_count(), pragma_page_size();", {
      it.next()
      QueryResult.Value(it.getLong(0)!!)
    }, 0).value
  }

  fun count(): Query<Long> {
    return fieldsQueries.count()
  }

  fun trimByReceivedDate(limit: Long) {
    fieldsQueries.trimByReceivedDate(limit)
  }

  fun vacuum() {
    driver.execute(null, "VACUUM", 0)
  }

  fun changes(): Long {
    return fieldsQueries.changes().executeAsOne()
  }
}
