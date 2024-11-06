# Welcome

This repository serves as a place to experiment with [Apollo Kotlin](https://github.com/apollographql/apollo-kotlin)'s normalized cache without impacting the main stable repository.

## Use in your project

> The incubating version of the normalized cache is not stable and is subject to change. It is recommended to experiment with it in
> non-critical projects/modules, or behind a feature flag.
>
> In particular,
> - there are no guarantees about the format of the cached data, so you should assume that it may be lost when upgrading
> - performance and size may not be optimal

{style="warning"}

Add the dependencies to your project.

```kotlin
// build.gradle.kts
dependencies {
  // For the memory cache
  implementation("com.apollographql.cache:normalized-cache-incubating:%latest_version%")
  
  // For the SQLite cache
  implementation("com.apollographql.cache:normalized-cache-sqlite-incubating:%latest_version%")
}
```

If you were using the stable Normalized Cache before, you can update your imports to the new package, `com.apollographql.cache.normalized.*`. 
