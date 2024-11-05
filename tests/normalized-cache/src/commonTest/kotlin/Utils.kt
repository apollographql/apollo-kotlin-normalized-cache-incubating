package test

import com.apollographql.apollo.testing.pathToJsonReader
import com.apollographql.apollo.testing.pathToUtf8

@Suppress("DEPRECATION")
fun testFixtureToUtf8(name: String) = pathToUtf8("normalized-cache/testFixtures/$name")

@Suppress("DEPRECATION")
fun testFixtureToJsonReader(name: String) = pathToJsonReader("normalized-cache/testFixtures/$name")
