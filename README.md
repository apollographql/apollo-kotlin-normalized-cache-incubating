<div align="center">

<p>
	<a href="https://www.apollographql.com/"><img src="https://raw.githubusercontent.com/apollographql/apollo-client-devtools/a7147d7db5e29b28224821bf238ba8e3a2fdf904/assets/apollo-wordmark.svg" height="100" alt="Apollo Client"></a>
</p>

[![Discourse](https://img.shields.io/discourse/topics?label=Discourse&server=https%3A%2F%2Fcommunity.apollographql.com&logo=discourse&color=467B95&style=flat-square)](http://community.apollographql.com/new-topic?category=Help&tags=mobile,client)
[![Slack](https://img.shields.io/static/v1?label=kotlinlang&message=apollo-kotlin&color=A97BFF&logo=slack&style=flat-square)](https://app.slack.com/client/T09229ZC6/C01A6KM1SBZ)

[![Maven Central](https://img.shields.io/maven-central/v/com.apollographql.cache/normalized-cache-incubating?style=flat-square)](https://central.sonatype.com/namespace/com.apollographql.cache)
[![OSS Snapshots](https://img.shields.io/nexus/s/com.apollographql.cache/normalized-cache-incubating?server=https%3A%2F%2Fs01.oss.sonatype.org&label=oss-snapshots&style=flat-square)](https://s01.oss.sonatype.org/content/repositories/snapshots/com/apollographql/cache/)

</div>

## 🚀 Apollo Kotlin Normalized Cache Incubating

This repository serves as a place to experiment with [Apollo Kotlin](https://github.com/apollographql/apollo-kotlin)'s normalized cache without impacting the main stable repository.

## 📚 Documentation

See the project website for documentation:<br/>
[https://apollographql.github.io/apollo-kotlin-normalized-cache-incubating/](https://apollographql.github.io/apollo-kotlin-normalized-cache-incubating/)

The Kdoc API reference can be found at:<br/>
[https://apollographql.github.io/apollo-kotlin-normalized-cache-incubating/kdoc](https://apollographql.github.io/apollo-kotlin-normalized-cache-incubating/kdoc)

## ⚠️ Disclaimer

The incubating version of the normalized cache is not stable and is subject to change. It is recommended to experiment with it in
non-critical projects/modules, or behind a feature flag.

In particular,

- there are no guarantees about the format of the cached data, so you should assume that it may be lost when upgrading
- performance and size may not be optimal
