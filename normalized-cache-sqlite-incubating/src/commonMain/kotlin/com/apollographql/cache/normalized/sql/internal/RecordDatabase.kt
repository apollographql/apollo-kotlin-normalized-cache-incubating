package com.apollographql.cache.normalized.sql.internal

import com.apollographql.apollo.api.json.ApolloJsonElement
import com.apollographql.cache.normalized.api.ApolloCacheHeaders
import com.apollographql.cache.normalized.api.Record
import com.apollographql.cache.normalized.api.expirationDate
import com.apollographql.cache.normalized.api.receivedDate
import com.apollographql.cache.normalized.sql.internal.fields.Field_
import com.apollographql.cache.normalized.sql.internal.fields.FieldsQueries

internal class RecordDatabase(private val fieldsQueries: FieldsQueries) {
  fun <T> transaction(body: () -> T): T {
    return fieldsQueries.transactionWithResult {
      body()
    }
  }

  /**
   * @param keys the keys of the records to select, size must be <= 999
   */
  fun selectRecords(keys: Collection<String>): List<Record> {
    val fieldsByRecordKey: Map<String, List<Field_>> = fieldsQueries.selectRecords(keys).executeAsList().groupBy { it.key }
    return fieldsByRecordKey.toRecords()
  }

  fun selectAllRecords(): List<Record> {
    val fieldsByRecordKey: Map<String, List<Field_>> = fieldsQueries.selectAllRecords().executeAsList().groupBy { it.key }
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
          key = record.key,
          field = field,
          value = value,
          metadata = record.metadata[field],
          receivedDate = record.receivedDate(field),
          expirationDate = record.expirationDate(field),
      )
    }
  }

  private fun insertOrUpdateField(
      key: String,
      field: String,
      value: ApolloJsonElement,
      metadata: Map<String, ApolloJsonElement>?,
      receivedDate: Long?,
      expirationDate: Long?,
  ) {
    fieldsQueries.insertOrUpdateField(
        key = key,
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

  fun trimByReceivedDate(limit: Int) {
    fieldsQueries.trimByReceivedDate(limit.toLong())
  }

  fun changes(): Long {
    return fieldsQueries.changes().executeAsOne()
  }
}
