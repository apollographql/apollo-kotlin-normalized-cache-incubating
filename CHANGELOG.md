# Next version (unreleased)

- Add `NormalizedCacheFactory.close()` (#66)

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
