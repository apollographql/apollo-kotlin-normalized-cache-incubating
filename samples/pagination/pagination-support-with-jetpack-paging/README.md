# Pagination sample: use the normalized cache pagination support, and Jetpack Paging

Displays a list of repositories fetched from the [GitHub GraphQL API](https://docs.github.com/en/graphql). 

Note: to execute the app, provide a [GitHub access token](https://developer.github.com/v4/guides/forming-calls/#authenticating-with-graphql) in the `gradle.properties` file.

## Architecture

- This is a variation of the [pagination-support](../pagination-support) sample, but with the addition of the [Android Jetpack Paging library](https://developer.android.com/topic/libraries/architecture/paging/v3-overview).
- `MainActivity` shows a `LazyColumn` listing the repositories by watching a `Pager`
- The `Pager` is configured with:
  - `RepositoryPagingSource` that fetches **all** the repositories from the cache, and returns the requested slice.
  - When the returned slice is empty the `Pager` knows that there more items must be fetched from the network and calls the configured `RemoteMediator`.
  - `RepositoryRemoteMediator` that fetches the next page from the network, and stores it in the cache. The pages are automatically merged thanks to the normalized cache pagination support.

These classes are implemented [here](app/src/main/java/com/example/apollokotlinpaginationsample/repository/Apollo.kt).
