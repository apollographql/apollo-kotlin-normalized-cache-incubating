# Next version (unreleased)

- Storage binary format is changed to be a bit more compact
- Add `ApolloStore.trim()` to remove old data from the cache
- `CacheKey` is used in more APIs instead of `String`, for consistency.
- `ApolloCacheHeaders.EVICT_AFTER_READ` is removed. `ApolloStore.remove()` can be used instead.
- `NormalizedCache.remove(pattern: String)` is removed. Please open an issues if you need this feature back.

# Version 0.0.7
_2025-03-03_

- Store errors in the cache, and remove `storePartialResponses()` (#96)

# Version 0.0.6
_2025-02-11_

- Add `ApolloStore.ALL_KEYS` to notify all watchers (#87)
- Support partial responses from the cache (#57)

# Version 0.0.5
_2024-12-18_

- Add Garbage Collection support (see [the documentation](https://apollographql.github.io/apollo-kotlin-normalized-cache-incubating/garbage-collection.html) for details)

# Version 0.0.4
_2024-11-07_

- Cache control support (see [the documentation](https://apollographql.github.io/apollo-kotlin-normalized-cache-incubating/cache-control.html) for details)
- Compatibility with the IntelliJ plugin cache viewer (#42)
- For consistency, `MemoryCacheFactory` and `MemoryCache` are now in the `com.apollographql.cache.normalized.memory` package 
- Remove deprecated symbols
- Add `IdCacheKeyGenerator` and `IdCacheKeyResolver` (#41)
- Add `ApolloStore.writeOptimisticUpdates` API for fragments (#55)

# Version 0.0.3
_2024-09-20_

Tweaks to the `ApolloResolver` API: `resolveField()` now takes a `ResolverContext`

# Version 0.0.2
_2024-07-08_

Update to Apollo Kotlin 4.0.0-rc.1

# Version 0.0.1
_2024-06-20_

Initial release
