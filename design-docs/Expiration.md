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

## Programmatic API

```kotlin

/**
 * A cache resolver that raises a cache miss if the field's received date is older than its max age
 * (configurable via [maxAgeProvider]) or its expiration date has passed.
 *
 * Received dates are stored by calling `storeReceiveDate(true)` on your `ApolloClient`.
 *
 * Expiration dates are stored by calling `storeExpirationDate(true)` on your `ApolloClient`.
 *
 * A maximum staleness can be configured via the [ApolloCacheHeaders.MAX_STALE] cache header.
 *
 * @see MutableExecutionOptions.storeReceiveDate
 * @see MutableExecutionOptions.storeExpirationDate
 * @see MutableExecutionOptions.maxStale
 */
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
  val parentType: String,
)
// Note: using a class instead of arguments allows for future evolutions. 

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
  private val coordinatesToDurations: Map<String, Duration>,
  private val defaultMaxAge: Duration? = null,
) : MaxAgeProvider {
  override fun getMaxAge(maxAgeContext: maxAgeContext): Duration? {
    // ...
  }
}

// Example usage:
val maxAgeProvider = SchemaCoordinatesMaxAgeProvider(
  coordinatesToDurations = mapOf(
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
val resolvedField = // delegate to the default resolver
val maxStale = // max stale duration from cache headers (if any, or 0)

// First consider the field's max age (client side)
val fieldMaxAge = // field's max age from the passed MaxAgeProvider
if (fieldMaxAge != null) {
  val fieldReceivedDate = // field's received date from the Record
  if (fieldReceivedDate != null) {
    val fieldAge = currentDate - fieldReceivedDate
    val stale = fieldAge - fieldMaxAge
    if (stale >= maxStale) {
      // throw cache miss
    }
  }
}

// Then consider the field's expiration date (server side)
val fieldExpirationDate = // field's expiration date from the Record
if (fieldExpirationDate != null) {
  val stale = currentDate - fieldExpirationDate
  if (stale >= maxStale) {
    // throw cache miss
  }
}

return resolvedField
```

Note: the `maxStale` duration is to allow for a per-operation override of the max age / expiration date. 

## Declarative API

### Schema directives

These directives will land in a `cache` v0.1 [Apollo Spec](https://specs.apollo.dev/).

```graphql
"""
Indicates that a field (or a type's fields) should be considered stale after the given duration
in seconds has passed since it has been received.

When applied on a type, all fields of the type inherit the max age.

When applied on a field whose parent type has a max age, the field's max age takes precedence.

```graphql
type User @maxAge(seconds: 10) {
  id: ID!
  email: String @maxAge(seconds: 20)
}
\```

`User.id` is considered stale after 10 seconds, and `User.email` after 20 seconds.
"""
directive @maxAge(seconds: Int!) on FIELD_DEFINITION | OBJECT

"""
Indicates that a field should be considered stale after the given duration in seconds has passed
since it has been received.

When applied on a field whose parent type has a max age, the field's max age takes precedence.

`@maxAgeField` is the same as `@maxAge` but can be used on type system extensions for services
that do not own the schema like client services:

```graphql
# extend the schema to set a max age on User.email.
extend type User @maxAgeField(name: "email", seconds: 20)
\```

`User.email` is considered stale after 20 seconds.
"""
directive @maxAgeField(name: String!, seconds: Int!) repeatable on OBJECT
```

### Codegen changes

#### Option A: Add max age info to `ObjectType`

```kotlin
class ObjectType internal constructor(
  name: String,
  keyFields: List<String>,
  implements: List<InterfaceType>,
  embeddedFields: List<String>,
  typeMaxAge: Int?, // NEW! Contains the value of the @maxAge directive on the type (or null if not set)
  fieldsMaxAge: Map<String, Int>?, // NEW! Contains the value of the @maxAge directive on the type's fields (and of @maxAgeField on the type) (or null if not set)
) : CompiledNamedType(name) {... }
```

With this option, a `CacheResolver` can access the max age information directly from the `ObjectType` that is passed to it.

Note: currently we pass only the parent type name to `CacheResolver` but we can pass the `CompiledNamedType` instead.

- Pro: the `CacheResolver` is autonomous, no need to do any 'plumbing' to pass it the generated information.
- Con: generates more fields for everybody, even users not using the feature (albeit with null values for them).

#### Option B: Generate a dedicated file for max age info

We generate a file looking like this:

```kotlin
object Expiration {
  val maxAges: Map<String, Int> = mapOf(
    "MyType" to 20,
    "MyType.id" to 10,
    // ...
  )
}
```

This is the approach we took for the `Pagination` feature where we need a list of connection types.

- Pro: no codegen impact for non-users of the feature, the file can be generated only when there are fields selected that have a max age in
  the schema.
- Con: more 'plumbing' - it requires to manually pass `Expiration.maxAges` to the constructor of the `CacheResolver`.
