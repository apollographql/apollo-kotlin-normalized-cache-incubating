package test

import com.apollographql.apollo.api.CompiledField
import com.apollographql.cache.normalized.api.MaxAge
import com.apollographql.cache.normalized.api.MaxAgeContext
import com.apollographql.cache.normalized.api.SchemaCoordinatesMaxAgeProvider
import declarative.cache.Cache
import programmatic.GetBookTitleQuery
import programmatic.GetCachedBookCachedTitleQuery
import programmatic.GetCachedBookTitleQuery
import programmatic.GetNodesQuery
import programmatic.GetProductQuery
import programmatic.GetProductsQuery
import programmatic.GetReaderBookTitleQuery
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

class SchemaCoordinatesMaxAgeProviderTest {
  @Test
  fun programmaticApolloServerExample() {
    // Taken from https://www.apollographql.com/docs/apollo-server/performance/caching/#example-maxage-calculations
    val provider = SchemaCoordinatesMaxAgeProvider(
        maxAges = mapOf(
            "Query.cachedBook" to MaxAge.Duration(60.seconds),
            "Query.reader" to MaxAge.Duration(40.seconds),
            "Book.cachedTitle" to MaxAge.Duration(30.seconds),
            "Reader.book" to MaxAge.Inherit,
        ),
        defaultMaxAge = 0.seconds,
    )

    var maxAge = provider.getMaxAge(
        MaxAgeContext(GetBookTitleQuery().rootField().path("book", "cachedTitle"))
    )
    assertEquals(0.seconds, maxAge)

    maxAge = provider.getMaxAge(
        MaxAgeContext(GetCachedBookTitleQuery().rootField().path("cachedBook", "title"))
    )
    assertEquals(60.seconds, maxAge)

    maxAge = provider.getMaxAge(
        MaxAgeContext(GetCachedBookCachedTitleQuery().rootField().path("cachedBook", "cachedTitle"))
    )
    assertEquals(30.seconds, maxAge)

    maxAge = provider.getMaxAge(
        MaxAgeContext(GetReaderBookTitleQuery().rootField().path("reader", "book", "title"))
    )
    assertEquals(40.seconds, maxAge)
  }

  @Test
  fun declarativeApolloServerExample() {
    val provider = SchemaCoordinatesMaxAgeProvider(
        maxAges = Cache.maxAges,
        defaultMaxAge = 0.seconds,
    )

    var maxAge = provider.getMaxAge(
        MaxAgeContext(declarative.GetBookTitleQuery().rootField().path("book", "cachedTitle"))
    )
    assertEquals(0.seconds, maxAge)

    maxAge = provider.getMaxAge(
        MaxAgeContext(declarative.GetCachedBookTitleQuery().rootField().path("cachedBook", "title"))
    )
    assertEquals(60.seconds, maxAge)

    maxAge = provider.getMaxAge(
        MaxAgeContext(declarative.GetCachedBookCachedTitleQuery().rootField().path("cachedBook", "cachedTitle"))
    )
    assertEquals(30.seconds, maxAge)

    maxAge = provider.getMaxAge(
        MaxAgeContext(declarative.GetReaderBookTitleQuery().rootField().path("reader", "book", "title"))
    )
    assertEquals(40.seconds, maxAge)
  }

  @Test
  fun interfaceAndObject() {
    val provider = SchemaCoordinatesMaxAgeProvider(
        maxAges = mapOf(
            "Product" to MaxAge.Duration(60.seconds),
            "Node" to MaxAge.Duration(30.seconds),
        ),
        defaultMaxAge = 0.seconds,
    )
    var maxAge = provider.getMaxAge(
        MaxAgeContext(GetProductQuery().rootField().path("product", "id"))
    )
    // Product implements Node but it's irrelevant, the type of Query.product is Product so that's what's used
    assertEquals(60.seconds, maxAge)

    maxAge = provider.getMaxAge(
        MaxAgeContext(GetNodesQuery().rootField().path("node", "id"))
    )
    assertEquals(30.seconds, maxAge)
  }

  @Test
  fun fallbackValue() {
    val provider1 = SchemaCoordinatesMaxAgeProvider(
        maxAges = mapOf<String, MaxAge>(),
        defaultMaxAge = 12.seconds,
    )
    var maxAge = provider1.getMaxAge(
        MaxAgeContext(GetProductsQuery().rootField().path("currentUserId"))
    )
    // root fields have the default maxAge
    assertEquals(12.seconds, maxAge)

    maxAge = provider1.getMaxAge(
        MaxAgeContext(GetProductsQuery().rootField().path("products"))
    )
    // root fields have the default maxAge
    assertEquals(12.seconds, maxAge)

    maxAge = provider1.getMaxAge(
        MaxAgeContext(GetProductsQuery().rootField().path("products", "id"))
    )
    // non root fields that return a leaf type inherit the maxAge of their parent field
    assertEquals(12.seconds, maxAge)

    val provider2 = SchemaCoordinatesMaxAgeProvider(
        maxAges = mapOf(
            "Product" to MaxAge.Duration(60.seconds),
        ),
        defaultMaxAge = 12.seconds,
    )
    // non root fields that return a leaf type inherit the maxAge of their parent field
    maxAge = provider2.getMaxAge(
        MaxAgeContext(GetProductsQuery().rootField().path("products", "id"))
    )
    assertEquals(60.seconds, maxAge)

    // fields that return a composite type have the default maxAge
    maxAge = provider2.getMaxAge(
        MaxAgeContext(GetProductsQuery().rootField().path("products", "colors"))
    )
    assertEquals(12.seconds, maxAge)
  }
}

private fun CompiledField.field(name: String): CompiledField =
  selections.firstOrNull { (it as CompiledField).name == name } as CompiledField

fun CompiledField.path(vararg path: String): List<CompiledField> =
  path.fold(listOf(this)) { acc, name -> acc + acc.last().field(name) }
