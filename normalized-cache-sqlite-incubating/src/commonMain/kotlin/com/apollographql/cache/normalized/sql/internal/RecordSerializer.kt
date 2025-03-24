package com.apollographql.cache.normalized.sql.internal

import com.apollographql.apollo.api.Error
import com.apollographql.apollo.api.Error.Builder
import com.apollographql.apollo.api.json.ApolloJsonElement
import com.apollographql.apollo.api.json.JsonNumber
import com.apollographql.cache.normalized.api.CacheKey
import com.apollographql.cache.normalized.api.Record
import com.apollographql.cache.normalized.api.RecordValue
import okio.Buffer
import okio.utf8Size

/**
 * A serializer that serializes/deserializes [RecordValue]s to/from [ByteArray]s.
 */
internal object RecordSerializer {
  fun serialize(record: Record): ByteArray {
    val buffer = Buffer()
    buffer.writeMap(record.fields)
    buffer._writeInt(record.metadata.size)
    for ((k, v) in record.metadata) {
      buffer.writeString(k)
      buffer.writeMap(v)
    }
    return buffer.readByteArray()
  }

  fun deserialize(key: String, bytes: ByteArray): Record {
    val buffer = Buffer().write(bytes)
    val fields = buffer.readMap()
    val metadataSize = buffer._readInt()
    val metadata = HashMap<String, Map<String, ApolloJsonElement>>(metadataSize).apply {
      repeat(metadataSize) {
        val k = buffer.readString()
        val v = buffer.readMap()
        put(k, v)
      }
    }
    return Record(
        key = CacheKey(key),
        fields = fields,
        mutationId = null,
        metadata = metadata
    )
  }

  private fun Buffer.writeString(value: String) {
    _writeInt(value.utf8Size().toInt())
    writeUtf8(value)
  }

  private fun Buffer.readString(): String {
    return readUtf8(_readInt().toLong())
  }

  private fun Buffer._writeInt(value: Int) {
    when (value) {
      0 -> {
        writeByte(INT_0)
      }

      in Byte.MIN_VALUE..Byte.MAX_VALUE -> {
        writeByte(INT_BYTE)
        writeByte(value.toInt())
      }

      in Short.MIN_VALUE..Short.MAX_VALUE -> {
        writeByte(INT_SHORT)
        writeShort(value.toInt())
      }

      else -> {
        writeByte(INT_INT)
        writeInt(value.toInt())
      }
    }
  }

  private fun Buffer._readInt(): Int {
    return when (val what = readByte().toInt()) {
      INT_0 -> 0
      INT_BYTE -> readByte().toInt()
      INT_SHORT -> readShort().toInt()
      INT_INT -> readInt()
      else -> error("Trying to read unsupported Int type: $what")
    }
  }

  private fun Buffer._writeLong(value: Long) {
    when (value) {
      0L -> {
        writeByte(LONG_0)
      }

      in Byte.MIN_VALUE..Byte.MAX_VALUE -> {
        writeByte(LONG_BYTE)
        writeByte(value.toInt())
      }

      in Short.MIN_VALUE..Short.MAX_VALUE -> {
        writeByte(LONG_SHORT)
        writeShort(value.toInt())
      }

      in Int.MIN_VALUE..Int.MAX_VALUE -> {
        writeByte(LONG_INT)
        writeInt(value.toInt())
      }

      else -> {
        writeByte(LONG_LONG)
        writeLong(value.toLong())
      }
    }
  }

  private fun Buffer._readLong(): Long {
    return when (val what = readByte().toInt()) {
      LONG_0 -> 0L
      LONG_BYTE -> readByte().toLong()
      LONG_SHORT -> readShort().toLong()
      LONG_INT -> readInt().toLong()
      LONG_LONG -> readLong()
      else -> error("Trying to read unsupported Long type: $what")
    }
  }

  private fun Buffer.writeMap(value: Map<*, *>) {
    _writeInt(value.size)
    @Suppress("UNCHECKED_CAST")
    value as Map<String, RecordValue>
    for ((k, v) in value) {
      writeString(k)
      writeAny(v)
    }
  }

  private fun Buffer.readMap(): Map<String, RecordValue> {
    val size = _readInt()
    return HashMap<String, RecordValue>(size).apply {
      repeat(size) {
        put(readString(), readAny())
      }
    }
  }

  private fun Buffer.writeAny(value: RecordValue) {
    when (value) {
      is String -> {
        if (value.isEmpty()) {
          writeByte(EMPTY_STRING)
        } else {
          writeByte(STRING)
          writeString(value)
        }
      }

      is Int -> {
        _writeInt(value)
      }

      is Long -> {
        _writeLong(value)
      }

      is Double -> {
        writeByte(DOUBLE)
        writeLong(value.toBits())
      }

      is JsonNumber -> {
        writeByte(JSON_NUMBER)
        writeString(value.value)
      }

      is Boolean -> {
        if (value) {
          writeByte(BOOLEAN_TRUE)
        } else {
          writeByte(BOOLEAN_FALSE)
        }
      }

      is CacheKey -> {
        writeByte(CACHE_KEY)
        writeString(value.key)
      }

      is List<*> -> {
        if (value.isEmpty()) {
          writeByte(EMPTY_LIST)
        } else {
          writeByte(LIST)
          _writeInt(value.size)
          value.forEach {
            writeAny(it)
          }
        }
      }

      is Map<*, *> -> {
        if (value.isEmpty()) {
          writeByte(MAP_EMPTY)
        } else {
          writeByte(MAP)
          writeMap(value)
        }
      }

      null -> {
        writeByte(NULL)
      }

      is Error -> {
        writeByte(ERROR)
        writeString(value.message)
        _writeInt(value.locations?.size ?: 0)
        for (location in value.locations.orEmpty()) {
          _writeInt(location.line)
          _writeInt(location.column)
        }
        _writeInt(value.path?.size ?: 0)
        for (path in value.path.orEmpty()) {
          writeAny(path)
        }
        writeAny(value.extensions)
      }

      else -> error("Trying to write unsupported Record value: $value")
    }
  }

  private fun Buffer.readAny(): RecordValue {
    return when (val what = readByte().toInt()) {
      STRING -> readString()
      EMPTY_STRING -> ""
      INT_0 -> 0
      INT_BYTE -> readByte().toInt()
      INT_SHORT -> readShort().toInt()
      INT_INT -> readInt()
      LONG_0 -> 0L
      LONG_BYTE -> readByte().toLong()
      LONG_SHORT -> readShort().toLong()
      LONG_INT -> readInt().toLong()
      LONG_LONG -> readLong()
      DOUBLE -> Double.fromBits(readLong())
      JSON_NUMBER -> JsonNumber(readString())
      BOOLEAN_TRUE -> true
      BOOLEAN_FALSE -> false
      CACHE_KEY -> {
        CacheKey(readString())
      }

      LIST -> {
        val size = _readInt()
        0.until(size).map {
          readAny()
        }
      }

      EMPTY_LIST -> emptyList<RecordValue>()

      MAP -> {
        readMap()
      }

      MAP_EMPTY -> emptyMap<String, RecordValue>()

      NULL -> null

      ERROR -> {
        val message = readString()
        val locations = 0.until(_readInt()).map {
          Error.Location(_readInt(), _readInt())
        }
        val path = 0.until(_readInt()).map {
          readAny()!!
        }

        @Suppress("UNCHECKED_CAST")
        val extensions = readAny() as Map<String, Any?>?
        Builder(message = message)
            .path(path)
            .apply {
              for ((key, value) in extensions.orEmpty()) {
                putExtension(key, value)
              }
              if (locations.isNotEmpty()) {
                locations(locations)
              }
            }
            .build()
      }

      else -> error("Trying to read unsupported Record type: $what")
    }
  }

  private const val NULL = 0
  private const val STRING = 1
  private const val EMPTY_STRING = 2
  private const val INT_0 = 3
  private const val INT_BYTE = 4
  private const val INT_SHORT = 5
  private const val INT_INT = 6
  private const val LONG_0 = 7
  private const val LONG_BYTE = 8
  private const val LONG_SHORT = 9
  private const val LONG_INT = 10
  private const val LONG_LONG = 11
  private const val BOOLEAN_TRUE = 12
  private const val BOOLEAN_FALSE = 13
  private const val DOUBLE = 14
  private const val JSON_NUMBER = 15
  private const val LIST = 16
  private const val EMPTY_LIST = 17
  private const val MAP = 18
  private const val MAP_EMPTY = 19
  private const val CACHE_KEY = 20
  private const val ERROR = 21
}
