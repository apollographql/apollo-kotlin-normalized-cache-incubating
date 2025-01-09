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
    // TODO: special case for empty string, saves 4 bytes
    writeInt(value.utf8Size().toInt()) // TODO: sizes should be unsigned
    writeUtf8(value)
  }

  private fun Buffer.readString(): String {
    return readUtf8(readInt().toLong())
  }

  private fun Buffer.writeAny(value: ApolloJsonElement) {
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
        buffer.writeByte(BOOLEAN) // TODO: 1 byte for BOOLEAN_TRUE, 1 byte for BOOLEAN_FALSE
        buffer.writeByte(if (value) 1 else 0)
      }

      is CacheKey -> {
        buffer.writeByte(CACHE_KEY)
        buffer.writeString(value.key)
      }

      is List<*> -> {
        buffer.writeByte(LIST) // TODO: special case for empty list, saves 4 bytes
        buffer.writeInt(value.size) // TODO: sizes should be unsigned
        value.forEach {
          buffer.writeAny(it)
        }
      }

      is Map<*, *> -> {
        buffer.writeByte(MAP) // TODO: special case for empty map, saves 4 bytes
        buffer.writeInt(value.size) // TODO: sizes should be unsigned
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

      is Error -> {
        buffer.writeByte(ERROR)
        buffer.writeString(value.message)
        buffer.writeInt(value.locations?.size ?: 0)
        for (location in value.locations.orEmpty()) {
          buffer.writeInt(location.line)
          buffer.writeInt(location.column)
        }
        buffer.writeInt(value.path?.size ?: 0)
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

      ERROR -> {
        val message = readString()
        val locations = 0.until(readInt()).map {
          Error.Location(readInt(), readInt())
        }
        val path = 0.until(readInt()).map {
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

      else -> error("Trying to read unsupported Record value: $what")
    }
  }

  private const val STRING = 0
  private const val INT = 1
  private const val LONG = 2 // TODO replace INT and LONG by BYTE, UBYTE, SHORT, USHORT, UINT for smaller values
  private const val BOOLEAN = 3
  private const val DOUBLE = 4
  private const val JSON_NUMBER = 5
  private const val LIST = 6
  private const val MAP = 7
  private const val CACHE_KEY = 8
  private const val NULL = 9
  private const val ERROR = 10
}
