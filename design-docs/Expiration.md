# Expiration

- Addition of an API to configure a max age per field / per type.
- When a field is resolved, if its age is greater than the configured max age, it will result in a cache miss.
- The server's expiration date is also considered: if it is set and in the past, it will result in a cache miss.

## 2 types of date

Currently, a Record can associate a date to each field, and the significance of this date is up to the resolver:

- received date: the date at which the field was received (set by the client)
- expiration date: the date at which the field is considered expired (normally comes from the server)

However, one may want to use both information in conjunction, e.g. use the expiration date from the server
by default, unless a max age is defined on the client.

To do this, let's store both dates in the Record. Implementation: we can remove the current `Record.date` field,
and instead use the `Record.metadata` map which can store arbitrary data per field.

## API

```kotlin

class ExpirationCacheResolver(private val maxAgeProvider: MaxAgeProvider) : CacheResolver {
  // ...
}

interface MaxAgeProvider {
  /**
   * Returns the max age for the given type and field.
   * @return null if no max age is defined for the given type and field.
   */
  fun getMaxAge(maxAgeContext: maxAgeContext): Duration?
}


class maxAgeContext(
  val field: CompiledField,
)
// Note: it's a single field for now, but having a class allows for future evolutions. 


/**
 * A provider that returns a single max age for all types.
 */
class GlobalMaxAgeProvider(private val maxAge: Duration) : MaxAgeProvider {
  override fun getMaxAge(maxAgeContext: maxAgeContext): Duration = maxAge
}

/**
 * A provider that returns a max age based on [schema coordinates](https://github.com/graphql/graphql-spec/pull/794).
 * The given coordinates must be object (e.g. `MyType`) or field (e.g. `MyType.myField`) coordinates.
 * If a field matches both field and object coordinates, the field ones are used.
 */
class SchemaCoordinatesMaxAgeProvider(
  private val coordinatesToDurations: List<Pair<String, Duration>>,
  private val defaultMaxAge: Duration? = null,
) : MaxAgeProvider {
  override fun getMaxAge(maxAgeContext: maxAgeContext): Duration? {
    // ...
  }
}

// Example usage:
val maxAgeProvider = SchemaCoordinatesMaxAgeProvider(
  coordinatesToDurations = listOf(
    "MyType.myField" to 5.minutes,
    "MyType" to 10.minutes,
  ),
  defaultMaxAge = 1.days,
)

/**
 * A provider that returns the max ages configured via directives in the schema.
 */
class SchemaMaxAgeProvider : MaxAgeProvider {
  // TBD
}
```

## Behavior of ExpirationCacheResolver

Pseudo code:

```kotlin
val maxStale = // max stale duration from cache headers (if any, or 0)

// First consider the field's max age (client side)
val fieldMaxAge = // field's max age from the passed MaxAgeProvider
if (fieldMaxAge != null) {
  val fieldReceivedDate = // field's received date from the Record
  if (fieldReceivedDate != null) {
    val fieldAge = currentDate - fieldReceivedDate
    if (fieldAge - fieldMaxAge > maxStale) {
      // throw cache miss
    }
  }
}

// Then consider the field's expiration date (server side)
val fieldExpirationDate = // field's expiration date from the Record
if (fieldExpirationDate != null) {
  if (currentDate - fieldExpirationDate > maxStale) {
    // throw cache miss
  }
}

// Fall back to default resolver
```

Note: the `maxStale` duration is to allow for a per-operation override of the max age / expiration date. 
