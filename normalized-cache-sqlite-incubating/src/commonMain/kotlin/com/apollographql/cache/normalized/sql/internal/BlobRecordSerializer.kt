package com.apollographql.cache.normalized.sql.internal

import com.apollographql.apollo.api.json.JsonNumber
import com.apollographql.cache.normalized.api.CacheKey
import com.apollographql.cache.normalized.api.Record
import okio.Buffer
import okio.utf8Size

/**
 * A serializer that serializes/deserializes a [Record] to a [ByteArray]
 *
 * It's a very basic implementation that encodes a record like below
 *
 * number of entries - Int
 * ------
 * name of the entry0 - String
 * timestamp of entry0 - Long?
 * value of entry0 - Any?
 * ------
 * name of the entry1 - String
 * timestamp of entry1 - Long?
 * value of entry1 - Any?
 * ------
 * etc...
 *
 * For each value, the type of the value is encoded using a single identifier byte so that deserialization can deserialize
 * to the expected type
 *
 * This should be revisited/optimized
 */
internal object BlobRecordSerializer {
  fun serialize(record: Record): ByteArray {
    val buffer = Buffer()

    buffer.writeAny(record.metadata)
    val keys = record.fields.keys
    buffer.writeInt(keys.size)
    for (key in keys) {
      buffer.writeString(key)
      buffer.writeAny(record.fields[key])
    }

    return buffer.readByteArray()
  }

  /**
   * returns the [Record] for the given Json
   *
   * @throws Exception if the [Record] cannot be deserialized
   */
  @Suppress("UNCHECKED_CAST")
  fun deserialize(key: String, bytes: ByteArray): Record {
    val buffer = Buffer().write(bytes)

    val metadata = buffer.readAny() as Map<String, Map<String, Any?>>

    val fields = mutableMapOf<String, Any?>()
    val size = buffer.readInt()

    for (i in 0.until(size)) {
      val name = buffer.readString()
      fields[name] = buffer.readAny()
    }

    return Record(key, fields, null, metadata)
  }

  private fun Buffer.writeString(value: String) {
    writeInt(value.utf8Size().toInt())
    writeUtf8(value)
  }

  private fun Buffer.readString(): String {
    return readUtf8(readInt().toLong())
  }

  private fun Buffer.writeAny(value: Any?) {
    when (value) {
      is String -> {
        buffer.writeByte(STRING)
        buffer.writeString(value)
      }

      is Int -> {
        buffer.writeByte(INT)
        buffer.writeInt(value)
      }

      is Long -> {
        buffer.writeByte(LONG)
        buffer.writeLong(value)
      }

      is Double -> {
        buffer.writeByte(DOUBLE)
        buffer.writeLong(value.toBits())
      }

      is JsonNumber -> {
        buffer.writeByte(JSON_NUMBER)
        buffer.writeString(value.value)
      }

      is Boolean -> {
        buffer.writeByte(BOOLEAN)
        buffer.writeByte(if (value) 1 else 0)
      }

      is CacheKey -> {
        buffer.writeByte(CACHE_KEY)
        buffer.writeString(value.key)
      }

      is List<*> -> {
        buffer.writeByte(LIST)
        buffer.writeInt(value.size)
        value.forEach {
          buffer.writeAny(it)
        }
      }

      is Map<*, *> -> {
        buffer.writeByte(MAP)
        buffer.writeInt(value.size)
        @Suppress("UNCHECKED_CAST")
        value as Map<String, Any?>
        value.forEach {
          buffer.writeString(it.key)
          buffer.writeAny(it.value)
        }
      }

      null -> {
        buffer.writeByte(NULL)
      }

      else -> error("Trying to write unsupported Record value: $value")
    }
  }

  private fun Buffer.readAny(): Any? {
    return when (val what = readByte().toInt()) {
      STRING -> readString()
      INT -> readInt()
      LONG -> readLong()
      DOUBLE -> Double.fromBits(readLong())
      JSON_NUMBER -> JsonNumber(readString())
      BOOLEAN -> readByte() > 0
      CACHE_KEY -> {
        CacheKey(readString())
      }

      LIST -> {
        val size = readInt()
        0.until(size).map {
          readAny()
        }
      }

      MAP -> {
        val size = readInt()
        0.until(size).associate {
          readString() to readAny()
        }
      }

      NULL -> null
      else -> error("Trying to read unsupported Record value: $what")
    }
  }

  private const val STRING = 0
  private const val INT = 1
  private const val LONG = 2
  private const val BOOLEAN = 3
  private const val DOUBLE = 4
  private const val JSON_NUMBER = 5
  private const val LIST = 6
  private const val MAP = 7
  private const val CACHE_KEY = 8
  private const val NULL = 9
}
