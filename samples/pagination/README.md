# Pagination samples

This directory contains samples that demonstrate how to implement pagination with the Apollo normalized cache.

These are 3 variations of an Android app that uses the [GitHub GraphQL API](https://docs.github.com/en/graphql) to fetch and display a list of repositories.

- [manual](./manual): manually merge pages together and store the result into the cache using the [`ApolloStore`](https://apollographql.github.io/apollo-kotlin-normalized-cache-incubating/kdoc/normalized-cache-incubating/com.apollographql.cache.normalized/-apollo-store/index.html?query=interface%20ApolloStore) API.
- [pagination-support](./pagination-support): use the normalized cache pagination support (`@typePolicy(connectionFields: ...)` / [`ConnectionMetadataGenerator`](https://apollographql.github.io/apollo-kotlin-normalized-cache-incubating/kdoc/normalized-cache-incubating/com.apollographql.cache.normalized.api/-connection-metadata-generator/index.html?query=class%20ConnectionMetadataGenerator(connectionTypes:%20Set%3CString%3E)%20:%20MetadataGenerator), [`ConnectionRecordMerger`](https://apollographql.github.io/apollo-kotlin-normalized-cache-incubating/kdoc/normalized-cache-incubating/com.apollographql.cache.normalized.api/-connection-record-merger.html?query=val%20ConnectionRecordMerger:%20FieldRecordMerger)) to automatically merge pages together in the cache.
- [pagination-support-with-jetpack-paging](./pagination-support-with-jetpack-paging): demonstrates how to use the normalized cache pagination support along with the [Android Jetpack Paging library](https://developer.android.com/topic/libraries/architecture/paging/v3-overview). 

Please refer to the `README.md` in each sample for more details.
