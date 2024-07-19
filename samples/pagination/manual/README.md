# Pagination sample: manual merging of pages

Displays a list of repositories fetched from the [GitHub GraphQL API](https://docs.github.com/en/graphql). 

Note: to execute the app, provide a [GitHub access token](https://developer.github.com/v4/guides/forming-calls/#authenticating-with-graphql) in the `gradle.properties` file.

## Architecture

- `MainActivity` shows a `LazyColumn` listing the repositories by watching `RepositoryListQuery`.  
- When scrolling to the end of the list, `fetchAndMergeNextPage()` is called, which
  1. Gets the current list from the cache
  2. Fetches the next page from the network
  3. Manually merges the next page with the current list
  4. Updates the cache with the merged list using the [`ApolloStore`](https://apollographql.github.io/apollo-kotlin-normalized-cache-incubating/kdoc/normalized-cache-incubating/com.apollographql.cache.normalized/-apollo-store/index.html?query=interface%20ApolloStore) API
- Writing to the cache triggers an emission to the watcher, and the `LazyColumn` is updated with the updated list. 

The gist of it is [here](app/src/main/java/com/example/apollokotlinpaginationsample/repository/Apollo.kt#L43).
