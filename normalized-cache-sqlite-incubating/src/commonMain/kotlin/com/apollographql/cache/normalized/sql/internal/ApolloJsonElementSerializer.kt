package com.apollographql.cache.normalized.sql.internal

import com.apollographql.apollo.api.json.ApolloJsonElement
import com.apollographql.apollo.api.Error
import com.apollographql.apollo.api.Error.Builder
import com.apollographql.apollo.api.json.JsonNumber
import com.apollographql.cache.normalized.api.CacheKey
import okio.Buffer
import okio.utf8Size

/**
 * A serializer that serializes/deserializes [ApolloJsonElement]s to/from [ByteArray]s.
 */
internal object ApolloJsonElementSerializer {
  fun serialize(jsonElement: ApolloJsonElement): ByteArray {
    val buffer = Buffer()
    buffer.writeAny(jsonElement)
    return buffer.readByteArray()
  }

  fun deserialize(bytes: ByteArray?): ApolloJsonElement {
    if (bytes == null) return null
    val buffer = Buffer().write(bytes)
    return buffer.readAny()
  }

  private fun Buffer.writeString(value: String) {
    writeNumber(value.utf8Size())
    writeUtf8(value)
  }

  private fun Buffer.readString(): String {
    return readUtf8(readNumber().toLong())
  }

  private fun Buffer.writeNumber(value: Number) {
    when (value.toLong()) {
      0L -> {
        writeByte(NUMBER_0)
      }

      in Byte.MIN_VALUE..Byte.MAX_VALUE -> {
        writeByte(NUMBER_BYTE)
        writeByte(value.toInt())
      }

      in Short.MIN_VALUE..Short.MAX_VALUE -> {
        writeByte(NUMBER_SHORT)
        writeShort(value.toInt())
      }

      in Int.MIN_VALUE..Int.MAX_VALUE -> {
        writeByte(NUMBER_INT)
        writeInt(value.toInt())
      }

      else -> {
        writeByte(NUMBER_LONG)
        writeLong(value.toLong())
      }
    }
  }

  private fun Buffer.readNumber(): Number {
    return when (val what = readByte().toInt()) {
      NUMBER_0 -> 0
      NUMBER_BYTE -> readByte()
      NUMBER_SHORT -> readShort()
      NUMBER_INT -> readInt()
      NUMBER_LONG -> readLong()
      else -> error("Trying to read unsupported Number type: $what")
    }
  }

  private fun Buffer.writeAny(value: ApolloJsonElement) {
    when (value) {
      is String -> {
        if (value.isEmpty()) {
          writeByte(EMPTY_STRING)
        } else {
          writeByte(STRING)
          writeString(value)
        }
      }

      is Int, is Long -> {
        writeNumber(value)
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
        if (value) {
          buffer.writeByte(BOOLEAN_TRUE)
        } else {
          buffer.writeByte(BOOLEAN_FALSE)
        }
      }

      is CacheKey -> {
        buffer.writeByte(CACHE_KEY)
        buffer.writeString(value.key)
      }

      is List<*> -> {
        if (value.isEmpty()) {
          buffer.writeByte(EMPTY_LIST)
        } else {
          buffer.writeByte(LIST)
          buffer.writeNumber(value.size)
          value.forEach {
            buffer.writeAny(it)
          }
        }
      }

      is Map<*, *> -> {
        if (value.isEmpty()) {
          buffer.writeByte(MAP_EMPTY)
        } else {
          buffer.writeByte(MAP)
          buffer.writeNumber(value.size)
          @Suppress("UNCHECKED_CAST")
          value as Map<String, Any?>
          value.forEach {
            buffer.writeString(it.key)
            buffer.writeAny(it.value)
          }
        }
      }

      null -> {
        buffer.writeByte(NULL)
      }

      is Error -> {
        buffer.writeByte(ERROR)
        buffer.writeString(value.message)
        buffer.writeNumber(value.locations?.size ?: 0)
        for (location in value.locations.orEmpty()) {
          buffer.writeNumber(location.line)
          buffer.writeNumber(location.column)
        }
        buffer.writeNumber(value.path?.size ?: 0)
        for (path in value.path.orEmpty()) {
          buffer.writeAny(path)
        }
        buffer.writeAny(value.extensions)
      }

      else -> error("Trying to write unsupported Record value: $value")
    }
  }

  private fun Buffer.readAny(): ApolloJsonElement {
    return when (val what = readByte().toInt()) {
      STRING -> readString()
      EMPTY_STRING -> ""
      NUMBER_0 -> 0
      NUMBER_BYTE -> readByte().toInt()
      NUMBER_SHORT -> readShort().toInt()
      NUMBER_INT -> readInt()
      NUMBER_LONG -> readLong()
      DOUBLE -> Double.fromBits(readLong())
      JSON_NUMBER -> JsonNumber(readString())
      BOOLEAN_TRUE -> true
      BOOLEAN_FALSE -> false
      CACHE_KEY -> {
        CacheKey(readString(), isHashed = true)
      }

      LIST -> {
        val size = readNumber().toInt()
        0.until(size).map {
          readAny()
        }
      }
      EMPTY_LIST -> emptyList<ApolloJsonElement>()

      MAP -> {
        val size = readNumber().toInt()
        0.until(size).associate {
          readString() to readAny()
        }
      }
      MAP_EMPTY -> emptyMap<String, ApolloJsonElement>()

      NULL -> null

      ERROR -> {
        val message = readString()
        val locations = 0.until(readNumber().toInt()).map {
          Error.Location(readNumber().toInt(), readNumber().toInt())
        }
        val path = 0.until(readNumber().toInt()).map {
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
  private const val NUMBER_0 = 3
  private const val NUMBER_BYTE = 4
  private const val NUMBER_SHORT = 5
  private const val NUMBER_INT = 6
  private const val NUMBER_LONG = 7
  private const val BOOLEAN_TRUE = 8
  private const val BOOLEAN_FALSE = 9
  private const val DOUBLE = 10
  private const val JSON_NUMBER = 11
  private const val LIST = 12
  private const val EMPTY_LIST = 13
  private const val MAP = 14
  private const val MAP_EMPTY = 15
  private const val CACHE_KEY = 16
  private const val ERROR = 17
}
