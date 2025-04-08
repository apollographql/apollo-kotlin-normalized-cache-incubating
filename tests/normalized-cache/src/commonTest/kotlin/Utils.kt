package test

import com.apollographql.cache.normalized.testing.pathToJsonReader
import com.apollographql.cache.normalized.testing.pathToUtf8

@Suppress("DEPRECATION")
fun testFixtureToUtf8(name: String) = pathToUtf8("normalized-cache/testFixtures/$name")

@Suppress("DEPRECATION")
fun testFixtureToJsonReader(name: String) = pathToJsonReader("normalized-cache/testFixtures/$name")
