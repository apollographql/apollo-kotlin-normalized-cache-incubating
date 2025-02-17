package com.apollographql.cache.normalized.sql.internal

import com.apollographql.apollo.api.json.ApolloJsonElement
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
    writeInt(value.utf8Size().toInt())
    writeUtf8(value)
  }

  private fun Buffer.readString(): String {
    return readUtf8(readInt().toLong())
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

      is Int -> {
        when (value) {
          0 -> {
            writeByte(NUMBER_0)
          }

          in Byte.MIN_VALUE..Byte.MAX_VALUE -> {
            writeByte(NUMBER_BYTE)
            writeByte(value)
          }

          in Short.MIN_VALUE..Short.MAX_VALUE -> {
            writeByte(NUMBER_SHORT)
            writeShort(value)
          }

          else -> {
            writeByte(NUMBER_INT)
            writeInt(value)
          }
        }
      }

      is Long -> {
        when (value) {
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
            writeLong(value)
          }
        }
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
          buffer.writeInt(value.size)
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
          buffer.writeInt(value.size)
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
        CacheKey(readString())
      }

      LIST -> {
        val size = readInt()
        0.until(size).map {
          readAny()
        }
      }
      EMPTY_LIST -> emptyList<ApolloJsonElement>()

      MAP -> {
        val size = readInt()
        0.until(size).associate {
          readString() to readAny()
        }
      }
      MAP_EMPTY -> emptyMap<String, ApolloJsonElement>()

      NULL -> null
      else -> error("Trying to read unsupported Record value: $what")
    }
  }

  private const val STRING = 0
  private const val EMPTY_STRING = 1
  private const val NUMBER_0 = 2
  private const val NUMBER_BYTE = 3
  private const val NUMBER_SHORT = 4
  private const val NUMBER_INT = 5
  private const val NUMBER_LONG = 6
  private const val BOOLEAN_TRUE = 7
  private const val BOOLEAN_FALSE = 8
  private const val DOUBLE = 9
  private const val JSON_NUMBER = 10
  private const val LIST = 11
  private const val EMPTY_LIST = 12
  private const val MAP = 13
  private const val MAP_EMPTY = 14
  private const val CACHE_KEY = 15
  private const val NULL = 16
}
