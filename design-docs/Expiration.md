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
   */
  fun getMaxAge(maxAgeContext: maxAgeContext): Duration
}

class MaxAgeContext(
  /**
   * The path of the field to get the max age of.
   * The first element is the root object, the last element is the field to get the max age of.
   */
  val fieldPath: List<CompiledField>,
)
// Note: using a class instead of arguments allows for future evolutions. 

/**
 * A provider that returns a single max age for all types.
 */
class GlobalMaxAgeProvider(private val maxAge: Duration) : MaxAgeProvider {
  override fun getMaxAge(maxAgeContext: maxAgeContext): Duration = maxAge
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
    // ...
  }
}

// Example usage:
val maxAgeProvider = SchemaCoordinatesMaxAgeProvider(
  coordinatesToMaxAges = mapOf(
    "MyType" to MaxAge.Duration(10.minutes),
    "MyType.myField" to MaxAge.Duration(5.minutes),
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
val fieldReceivedDate = // field's received date from the Record
  if (fieldReceivedDate != null) {
    val fieldAge = currentDate - fieldReceivedDate
    val stale = fieldAge - fieldMaxAge
    if (stale >= maxStale) {
      // throw cache miss
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

#### Existing backend directive

Apollo Server [has a `@cacheControl` directive](https://www.apollographql.com/docs/apollo-server/performance/caching) that can be applied
to fields and types to set a max age. This is used by the server to set a `Cache-Control` HTTP header on the response.

Here's its definition:
```graphql
enum CacheControlScope {
  PUBLIC
  PRIVATE
}

directive @cacheControl(
  maxAge: Int
  scope: CacheControlScope
  inheritMaxAge: Boolean
) on FIELD_DEFINITION | OBJECT | INTERFACE | UNION
```

It would be beneficial to re-use this directive on the client side, rather that inventing a new one. This raises a few questions.

##### Meaning of scope

On the client side the meaning of the `scope` argument is unclear for the moment, but could be useful in the future if we want to share a
cache between users. This is out of scope for now and the argument could be left unused.

##### Default max age

Apollo Server [uses heuristics](https://www.apollographql.com/docs/apollo-server/performance/caching/#default-maxage) to decide on the
default max age when no directive is applied:
- root fields have the default maxAge (which is 0 by default)
- same for fields that return a composite type
- non root fields that return a leaf type inherit the maxAge of their parent field

The default max age [is configurable](https://www.apollographql.com/docs/apollo-server/performance/caching/#setting-a-different-default-maxage)
on the backend and has a default value of 0. We must also make it configurable on the client side, but should make it a required argument
rather than having a default, making the behavior more predictable.

##### Ability to use backend and client directives at the same time

The ability to use both server side cache control (expiration date computed from the `Cache-Control` or `Age` header in the response), and
client side cache control (max ages configured on the client) is desirable. The client app should be able to override the server side
values.

If we use the same directive for both, there is a potential conflict: clients will have `@cacheControl` directives in their schema that are
meant for the server, and mustn't be interpreted by the codegen.

However, this can be solved thanks to the `@link` mechanism which ensures that the codegen only considers the directives that are properly
namespaced. An [alias](https://specs.apollo.dev/link/v1.0/#example-import-an-aliased-name) can be used to avoid a conflict.

#### Directive definitions

We can re-use the backend `@cacheControl` directive on the client, and add a variant `@cacheControlField`, to configure fields via 
extensions.

These definitions will land in a `cache` v0.1 [Apollo Spec](https://specs.apollo.dev/).

```graphql
"""
Possible values for the `@cacheControl` `scope` argument (unused on the client).
"""
enum CacheControlScope {
  PUBLIC
  PRIVATE
}

"""
Configures cache settings for a field or type.

- `maxAge`: The maximum amount of time the field's cached value is valid, in seconds. The default value is configurable.
- `inheritMaxAge`: If true, the field inherits the `maxAge` of its parent field. If set to `true`, `maxAge` must not be provided.
- `scope`: Unused on the client.

When applied to a type, the settings apply to all schema fields that return this type.

Field-level settings override type-level settings.

```graphql
type Query {
  me: User
  user(id: ID!): User @cacheControl(maxAge: 5)
}

type User @cacheControl(maxAge: 10) {
  id: ID!
  email: String
}
\```

`Query.me` is valid for 10 seconds, and `Query.user` for 5 seconds.
"""
directive @cacheControl(
  maxAge: Int
  inheritMaxAge: Boolean
  scope: CacheControlScope
) on FIELD_DEFINITION | OBJECT | INTERFACE | UNION

"""
Configures cache settings for a field.

`@cacheControlField` is the same as `@cacheControl` but can be used on type system extensions for services that do not own the schema like
client services:

```graphql
# extend the schema to set a max age on User.email.
extend type User @cacheControlField(name: "email", maxAge: 20)
\```

`User.email` is valid for 20 seconds.
"""
directive @cacheControlField(
  name: String!
  maxAge: Int
  inheritMaxAge: Boolean
  scope: CacheControlScope
) repeatable on OBJECT | INTERFACE

```

### Codegen changes

#### Option A: Add max age info to `ObjectType`

```kotlin
class ObjectType internal constructor(
  name: String,
  keyFields: List<String>,
  implements: List<InterfaceType>,
  embeddedFields: List<String>,
  typeMaxAge: MaxAge?, // NEW! Contains the value of the @cacheControl directive on the type (or null if not set)
  fieldsMaxAge: Map<String, MaxAge>?, // NEW! Contains the value of the @cacheControl directive on the type's fields (and of @cacheControlField on the type) (or null if not set)
) : CompiledNamedType(name) { ... }
```

With this option, a `CacheResolver` can access the max age information directly from the `ObjectType` that is passed to it.

- Pro: the `CacheResolver` is autonomous, no need to do any 'plumbing' to pass it the generated information.
- Con: generates more fields for everybody, even users not using the feature (albeit with null values for them).

#### Option B: Generate a dedicated file for max age info

We generate a file looking like this:

```kotlin
object Expiration {
  val maxAges: Map<String, MaxAge> = mapOf(
    "MyType" to MaxAge.Value(20),
    "MyType.id" to MaxAge.Inherit,
    // ...
  )
}
```

This is the approach we took for the `Pagination` feature where we need a list of connection types.

- Pro: no codegen impact for non-users of the feature, the file can be generated only when there are fields selected that have a max age in
  the schema.
- Con: more 'plumbing' - it requires to manually pass `Expiration.maxAges` to the constructor of the `CacheResolver`.
