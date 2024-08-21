package com.apollographql.cache.normalized.api

import com.apollographql.apollo.api.CompiledField
import com.apollographql.apollo.api.isComposite
import kotlin.time.Duration

interface MaxAgeProvider {
  /**
   * Returns the max age for the given field.
   */
  fun getMaxAge(maxAgeContext: MaxAgeContext): Duration
}

class MaxAgeContext(
    /**
     * The path of the field to get the max age of.
     * The first element is the root object, the last element is the field to get the max age of.
     */
    val fieldPath: List<CompiledField>,
)

/**
 * A provider that returns a single max age for all types.
 */
class GlobalMaxAgeProvider(private val maxAge: Duration) : MaxAgeProvider {
  override fun getMaxAge(maxAgeContext: MaxAgeContext): Duration = maxAge
}

sealed interface MaxAge {
  class Duration(val duration: kotlin.time.Duration) : MaxAge
  data object Inherit : MaxAge
}

/**
 * A provider that returns a max age based on [schema coordinates](https://github.com/graphql/graphql-spec/pull/794).
 * The given coordinates must be object/interface/union (e.g. `MyType`) or field (e.g. `MyType.myField`) coordinates.
 *
 * The max age of a field is determined as follows:
 * - If the field has a [MaxAge.Duration] max age, return it.
 * - Else, if the field has a [MaxAge.Inherit] max age, return the max age of the parent field.
 * - Else, if the field's type has a [MaxAge.Duration] max age, return it.
 * - Else, if the field's type has a [MaxAge.Inherit] max age, return the max age of the parent field.
 * - Else, if the field is a root field, or the field's type is composite, return the default max age.
 * - Else, return the max age of the parent field.
 *
 * Then the lowest of the field's max age and its parent field's max age is returned.
 */
class SchemaCoordinatesMaxAgeProvider(
    private val coordinatesToMaxAges: Map<String, MaxAge>,
    private val defaultMaxAge: Duration,
) : MaxAgeProvider {
  override fun getMaxAge(maxAgeContext: MaxAgeContext): Duration {
    if (maxAgeContext.fieldPath.size == 1) {
      // Root field
      return defaultMaxAge
    }

    val fieldName = maxAgeContext.fieldPath.last().name
    val fieldParentTypeName = maxAgeContext.fieldPath[maxAgeContext.fieldPath.lastIndex - 1].type.rawType().name
    val fieldCoordinates = "$fieldParentTypeName.$fieldName"
    val computedFieldMaxAge = when (val fieldMaxAge = coordinatesToMaxAges[fieldCoordinates]) {
      is MaxAge.Duration -> {
        fieldMaxAge.duration
      }

      is MaxAge.Inherit -> {
        getParentMaxAge(maxAgeContext)
      }

      null -> {
        getTypeMaxAge(maxAgeContext)
      }
    }
    val isRootField = maxAgeContext.fieldPath.size == 2
    return if (isRootField) {
      computedFieldMaxAge
    } else {
      minOf(computedFieldMaxAge, getParentMaxAge(maxAgeContext))
    }
  }

  private fun getParentMaxAge(maxAgeContext: MaxAgeContext): Duration = getMaxAge(MaxAgeContext(maxAgeContext.fieldPath.dropLast(1)))

  private fun getTypeMaxAge(maxAgeContext: MaxAgeContext): Duration {
    val field = maxAgeContext.fieldPath.last()
    val fieldTypeName = field.type.rawType().name
    return when (val typeMaxAge = coordinatesToMaxAges[fieldTypeName]) {
      is MaxAge.Duration -> {
        typeMaxAge.duration
      }

      is MaxAge.Inherit -> {
        getParentMaxAge(maxAgeContext)
      }

      null -> {
        getFallbackMaxAge(maxAgeContext)
      }
    }
  }

  // Fallback:
  // - root fields have the default maxAge
  // - same for fields that return a composite type
  // - non root fields that return a leaf type inherit the maxAge of their parent field
  private fun getFallbackMaxAge(maxAgeContext: MaxAgeContext): Duration {
    val field = maxAgeContext.fieldPath.last()
    val isRootField = maxAgeContext.fieldPath.size == 2
    return if (isRootField || field.type.rawType().isComposite()) {
      defaultMaxAge
    } else {
      getParentMaxAge(maxAgeContext)
    }
  }
}
