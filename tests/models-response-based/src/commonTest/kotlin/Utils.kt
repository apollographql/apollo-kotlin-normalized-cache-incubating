import com.apollographql.cache.normalized.testing.pathToUtf8

@Suppress("DEPRECATION")
fun testFixtureToUtf8(name: String) = pathToUtf8("models-fixtures/json/$name")
