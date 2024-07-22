# Pagination sample: use the normalized cache pagination support

Displays a list of repositories fetched from the [GitHub GraphQL API](https://docs.github.com/en/graphql). 

Note: to execute the app, provide a [GitHub access token](https://developer.github.com/v4/guides/forming-calls/#authenticating-with-graphql) in the `gradle.properties` file.

## Architecture

- `MainActivity` shows a `LazyColumn` listing the repositories by watching `RepositoryListQuery`.  
- When scrolling to the end of the list, `fetchAndMergeNextPage()` is called, which
  1. Gets the current list from the cache, to get the cursor for the next page
  2. Fetches the next page from the network
- The new page is automatically merged with the current list in the cache, thanks to the configuration done in `extra.graphqls`:
  ```graphql
  # Declares that the `repositories` field is a Connection type
  extend type Organization @typePolicy(connectionFields: "repositories")
  ```
  and the cache declaration in `Apollo.kt`:
  ```kotlin
  .store(
    ApolloStore(
        normalizedCacheFactory = memoryThenSqlCache,
        cacheKeyGenerator = TypePolicyCacheKeyGenerator,
        metadataGenerator = ConnectionMetadataGenerator(Pagination.connectionTypes), // Use the generated Pagination class
        cacheResolver = FieldPolicyCacheResolver,
        recordMerger = ConnectionRecordMerger, // Use ConnectionRecordMerger that can handle Relay-style pagination
    )
  )
  ```


- Writing to the cache triggers an emission to the watcher, and the `LazyColumn` is updated with the updated list. 

The gist of it is [here](app/src/main/java/com/example/apollokotlinpaginationsample/repository/Apollo.kt#L54).
