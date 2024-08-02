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

It could be beneficial to re-use this directive on the client side, rather that inventing a new one. This raises a few questions.

##### Default max age

Apollo Server [uses heuristics](https://www.apollographql.com/docs/apollo-server/performance/caching/#default-maxage) to decide on the
default max age when no directive is applied:
- root fields are not cacheable by default (maxAge=0)
- same for fields that return a composite type
- non root fields that return a leaf type have the maxAge of their parent field (which is 0 by default, not cacheable)

On the backend it is reasonable to avoid over-caching, which can lead to bugs.

On the client side however, users opt-in to the cache globally and are expecting everything to be cached indefinitely by default
(which is the current behavior). The max age is an additional configuration. Because of this, the backend heuristics which essentially 
default to not cacheable would probably be confusing to a client user.

If we don't have these heuristics, the `inheritMaxAge` argument becomes unneeded, and we can remove it. If it is removed, then the `maxAge`
argument should be required.

##### Meaning of scope

On the client side the meaning of the `scope` argument is unclear for the moment, but could be useful if we want to share a cache between
users. This is probably out of scope for now and can either be removed or ignored. 

##### Ability to use backend and client directives at the same time

The ability to use both server side cache control (expiration date computed from the `Cache-Control` or `Age` header in the response), and
client side cache control (max ages configured on the client) is desirable. The client app can override the server side values.

If we use the same directive for both, there is a potential conflict: clients will have `@cacheControl` directives in their schema that are
meant for the server, and mustn't be interpreted by the codegen.

However, this can be solved thanks to the `@link` mechanism which ensures that the codegen only considers the directives that are properly
namespaced. An [alias](https://specs.apollo.dev/link/v1.0/#example-import-an-aliased-name) can be used to avoid a conflict.

##### Meaning of applying the directive to a type

On the server side, applying the `@cacheControl` directive to a type means that all fields that return this type have the configured max
age.

```graphql
type Query {
  me: User!
  user(id: ID!): User
}

type User @cacheControl(maxAge: 10) {
  id: ID!
  name: String!
  picture: String @cacheControl(maxAge: 20)
}
```
is equivalent to:
```graphql
type Query {
  me: User! @cacheControl(maxAge: 10)
  user(id: ID!): User @cacheControl(maxAge: 10)
}

type User {
  id: ID!
  name: String!
  picture: String @cacheControl(maxAge: 20)
}
```

Another way to interpret it could be that it represents the default max age for the type's fields. In that case it would be equivalent to:
```graphql
type Query {
  me: User!
  user(id: ID!): User
}

type User {
  id: ID! @cacheControl(maxAge: 10)
  name: String! @cacheControl(maxAge: 10)
  picture: String @cacheControl(maxAge: 20)
}
```

I think the backend meaning is more intuitive and expressive and is the one we should go with. In that case the same meaning should
be used when object coordinates are passed to `SchemaCoordinatesMaxAgeProvider`.

#### New client directives

I propose we use a simplified version of the backend directive.

These directives will land in a `cache` v0.1 [Apollo Spec](https://specs.apollo.dev/).

```graphql
"""
Indicates that a field or a type should be considered stale after the given max
age in seconds has passed since it has been received.

When applied to a type, the max age applies to all fields in the schema that
are of that type.

When applied to a field whose parent type has a max age, the field's max age
takes precedence.

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

`Query.me` is considered stale after 10 seconds, and `Query.user` after 5
seconds.
"""
directive @cacheControl(maxAge: Int!) on FIELD_DEFINITION | OBJECT | INTERFACE | UNION

"""
Indicates that a field should be considered stale after the given duration in
seconds has passed since it has been received.

When applied to a field whose parent type has a max age, the field's max age
takes precedence.

`@cacheControlField` is the same as `@cacheControl` but can be used on type
system extensions for services that do not own the schema like client services:

```graphql
# extend the schema to set a max age on User.email.
extend type User @cacheControlField(name: "email", maxAge: 20)
\```

`User.email` is considered stale after 20 seconds.
"""
directive @cacheControlField(name: String!, maxAge: Int!) repeatable on OBJECT | INTERFACE
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
