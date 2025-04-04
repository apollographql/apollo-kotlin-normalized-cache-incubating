# Migration guide

> This guide is a work in progress

{style="warning"}

This guide highlights the main differences between this library and the version hosted on the
[main Apollo Kotlin repository](https://github.com/apollographql/apollo-kotlin).

## Artifacts and packages

To use this library, update the dependencies to your project:

```kotlin
// build.gradle.kts
dependencies {
  // Replace
  implementation("com.apollographql.apollo:apollo-normalized-cache") // Memory cache
  implementation("com.apollographql.apollo:apollo-normalized-cache-sqlite") // SQLite cache
  
  // With
  implementation("com.apollographql.cache:normalized-cache-incubating:%latest_version%") // Memory cache
  implementation("com.apollographql.cache:normalized-cache-sqlite-incubating:%latest_version%") // SQLite cache
}
```

Note: the `com.apollographql.apollo:apollo-normalized-cache-api` artifact no longer exists, the code it contained has been merged into `com.apollographql.cache:normalized-cache-incubating`.

Then update your imports:

```kotlin
// Replace
import com.apollographql.apollo.cache.normalized.* 
// With
import com.apollographql.cache.normalized.*

// Replace
import com.apollographql.apollo.cache.normalized.api.MemoryCacheFactory
// With
import com.apollographql.cache.normalized.memory.MemoryCacheFactory



```

## Database schema

The SQLite cache now uses a different schema.

- Previously, records were stored as JSON in a text column.
- Now they are stored in an equivalent binary format in a blob column.

When this library opens an existing database and finds the old schema, it will **automatically delete** any existing data and create the new schema.

> This is a destructive operation.

{style="warning"}

If your application relies on the data stored in the cache, you can manually transfer all the records from the an old database to a new one.
See an example on how to do that [here](https://github.com/apollographql/apollo-kotlin-normalized-cache-incubating/blob/main/tests/migration/src/commonTest/kotlin/MigrationTest.kt#L157).

Make sure you thoroughly test migration scenarios before deploying to production.

> Expect more changes to the schema as the library evolves and stabilizes.

{style="warning"}

## `ApolloStore`

### Partial cache reads
`readOperation()` now returns an `ApolloResponse<D>` (it previously returned a `<D>`). This allows for returning partial data from the cache, whereas
previously no data and a `CacheMissException` would be returned if any field was not found.

Now data with null fields (when possible) is returned with `Error`s in `ApolloResponse.errors` for any missing field

`ApolloResponse.cacheInfo.isCacheHit` will be false when any field is missing.

### Partial responses and errors are stored

Previously, partial responses were not stored by default, but you could opt in with `storePartialResponses(true)`.

Now `storePartialResponses()` is removed and is the default, and errors returned by the server are stored in the cache and `readOperation()` will return them.

By default, errors will not replace existing data in the cache. You can change this behavior with `errorsReplaceCachedValues(true)`.

> The built-in fetch policies treat any missing or error field as a full cache miss (same behavior as previous versions).
>
> You can implement your own fetch policy interceptor to handle partial cache reads, as shown in [this example](https://github.com/apollographql/apollo-kotlin-normalized-cache-incubating/blob/main/tests/partial-results/src/commonTest/kotlin/test/CachePartialResultTest.kt#L809).

### Publishing changes to watchers

Previously, write methods had 2 flavors:
- a `suspend` one that accepts a `publish` parameter to control whether changes should be published to watchers
- a non-suspend one (e.g. `writeOperationSync`) that doesn't publish changes

Now only the non-suspend ones exist and don't publish. Manually call `publish()` to notify watchers of changes.

```kotlin
// Replace
store.writeOperation(operation, data)
// With
store.writeOperation(operation, data).also { store.publish(it) }
```

### CustomScalarAdapters

Individual `ApolloStore` methods no longer accept a `CustomScalarAdapters`. Instead it can be passed to the `ApolloStore` constructor.

Make sure to pass the same `CustomScalarAdapters` you used to create the `ApolloClient`:

```kotlin
val customScalarAdapters = CustomScalarAdapters.Builder()/* ... */.build()
val store = ApolloStore(cacheFactory, customScalarAdapters = customScalarAdapters)
val client = ApolloClient.Builder()
    /* ... */
    .customScalarAdapters(customScalarAdapters)
    .store(store)
    .build()
```

### Other changes

- `readFragment()` now returns a `ReadResult<D>` (it previously returned a `<D>`). This allows for surfacing metadata associated to the returned data, e.g. staleness.
- Records are now rooted per operation type (`QUERY_ROOT`, `MUTATION_ROOT`, `SUBSCRIPTION_ROOT`), when previously these were all at the same level, which could cause conflicts.
- `ApolloClient.apolloStore` is deprecated in favor of `ApolloClient.store` for consistency.

## CacheResolver, CacheKeyResolver

The APIs of `CacheResolver` and `CacheKeyResolver` have been tweaked to be more future-proof. The main change is that the methods now takes a `ResolverContext` instead of
individual parameters.

```kotlin
// Before
interface CacheResolver {
  fun resolveField(
      field: CompiledField,
      variables: Executable.Variables,
      parent: Map<String, @JvmSuppressWildcards Any?>,
      parentId: String,
  ): Any?
}

// After
interface CacheResolver {
  fun resolveField(context: ResolverContext): Any?
}
```

`resolveField` can also now return a `ResolvedValue` when metadata should be returned with the resolved value (e.g. staleness).

### CacheKey

For consistency, the `CacheKey` type is now used instead of `String` in more APIs, e.g.:

- `ApolloStore.remove()`
- `Record.key`
- `NormalizedCache.loadRecord()`

### Removed APIs

- `ApolloCacheHeaders.EVICT_AFTER_READ` is removed. Manually call `ApolloStore.remove()` when needed instead.
- `NormalizedCache.remove(pattern: String)` is removed. Please open an issue if you need this feature back.
