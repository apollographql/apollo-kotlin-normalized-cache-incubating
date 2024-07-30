package com.apollographql.cache.normalized.api

import com.apollographql.apollo.api.CompiledField
import kotlin.time.Duration

interface MaxAgeProvider {
  /**
   * Returns the max age for the given type and field.
   * @return null if no max age is defined for the given type and field.
   */
  fun getMaxAge(maxAgeContext: MaxAgeContext): Duration?
}

class MaxAgeContext(
    val field: CompiledField,
    val parentType: String,
)

/**
 * A provider that returns a single max age for all types.
 */
class GlobalMaxAgeProvider(private val maxAge: Duration) : MaxAgeProvider {
  override fun getMaxAge(maxAgeContext: MaxAgeContext): Duration = maxAge
}

/**
 * A provider that returns a max age based on [schema coordinates](https://github.com/graphql/graphql-spec/pull/794).
 * The given coordinates must be object (e.g. `MyType`) or field (e.g. `MyType.myField`) coordinates.
 * If a field matches both field and object coordinates, the field ones are used.
 */
class SchemaCoordinatesMaxAgeProvider(
    private val coordinatesToDurations: Map<String, Duration>,
    private val defaultMaxAge: Duration? = null,
) : MaxAgeProvider {
  override fun getMaxAge(maxAgeContext: MaxAgeContext): Duration? {
    return coordinatesToDurations["${maxAgeContext.parentType}.${maxAgeContext.field.name}"]
        ?: coordinatesToDurations[maxAgeContext.parentType]
        ?: defaultMaxAge
  }
}
