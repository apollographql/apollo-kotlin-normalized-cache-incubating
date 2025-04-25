package test.declarativecache

import com.apollographql.cache.normalized.CacheManager
import com.apollographql.cache.normalized.api.CacheKey
import com.apollographql.cache.normalized.api.CacheResolver
import com.apollographql.cache.normalized.api.FieldPolicyCacheResolver
import com.apollographql.cache.normalized.api.ResolverContext
import com.apollographql.cache.normalized.memory.MemoryCacheFactory
import com.apollographql.cache.normalized.testing.runTest
import declarativecache.GetAuthorQuery
import declarativecache.GetBookQuery
import declarativecache.GetBooksQuery
import declarativecache.GetOtherBookQuery
import declarativecache.GetOtherLibraryQuery
import declarativecache.GetPromoAuthorQuery
import declarativecache.GetPromoBookQuery
import declarativecache.GetPromoLibraryQuery
import kotlin.test.Test
import kotlin.test.assertEquals

class DeclarativeCacheTest {

  @Test
  fun typePolicyIsWorking() = runTest {
    val store = CacheManager(MemoryCacheFactory())

    // Write a book at the "promo" path
    val promoOperation = GetPromoBookQuery()
    val promoData = GetPromoBookQuery.Data(GetPromoBookQuery.PromoBook("Promo", "42", "Book"))
    store.writeOperation(promoOperation, promoData)

    // Overwrite the book title through the "other" path
    val otherOperation = GetOtherBookQuery()
    val otherData = GetOtherBookQuery.Data(GetOtherBookQuery.OtherBook("42", "Other", "Book"))
    store.writeOperation(otherOperation, otherData)

    // Get the "promo" book again, the title must be updated
    val data = store.readOperation(promoOperation).data!!

    assertEquals("Other", data.promoBook?.title)
  }

  @Test
  fun fallbackIdIsWorking() = runTest {
    val store = CacheManager(MemoryCacheFactory())

    // Write a library at the "promo" path
    val promoOperation = GetPromoLibraryQuery()
    val promoData = GetPromoLibraryQuery.Data(GetPromoLibraryQuery.PromoLibrary("PromoAddress", "3", "Library"))
    store.writeOperation(promoOperation, promoData)

    // Overwrite the library address through the "other" path
    val otherOperation = GetOtherLibraryQuery()
    val otherData = GetOtherLibraryQuery.Data(GetOtherLibraryQuery.OtherLibrary("3", "OtherAddress", "Library"))
    store.writeOperation(otherOperation, otherData)

    // Get the "promo" library again, the address must be updated
    val data = store.readOperation(promoOperation).data!!

    assertEquals("OtherAddress", data.promoLibrary?.address)
  }

  @Test
  fun fieldPolicyIsWorking() = runTest {
    val store = CacheManager(MemoryCacheFactory())

    val bookQuery1 = GetPromoBookQuery()
    val bookData1 = GetPromoBookQuery.Data(GetPromoBookQuery.PromoBook("Promo", "42", "Book"))
    store.writeOperation(bookQuery1, bookData1)

    val bookQuery2 = GetBookQuery("42")
    val bookData2 = store.readOperation(bookQuery2).data!!

    assertEquals("Promo", bookData2.book?.title)

    val authorQuery1 = GetPromoAuthorQuery()
    val authorData1 = GetPromoAuthorQuery.Data(
        GetPromoAuthorQuery.PromoAuthor(
            "Pierre",
            "Bordage",
            "Author"
        )
    )

    store.writeOperation(authorQuery1, authorData1)

    val authorQuery2 = GetAuthorQuery("Pierre", "Bordage")
    val authorData2 = store.readOperation(authorQuery2).data!!

    assertEquals("Pierre", authorData2.author?.firstName)
    assertEquals("Bordage", authorData2.author?.lastName)
  }

  @Test
  fun canResolveListProgrammatically() = runTest {
    val cacheResolver = object : CacheResolver {
      override fun resolveField(context: ResolverContext): Any? {
        val fieldName = context.field
        if (fieldName.name == "books") {
          @Suppress("UNCHECKED_CAST")
          val isbns = fieldName.argumentValue("isbns", context.variables).getOrThrow() as? List<String>
          if (isbns != null) {
            return isbns.map { CacheKey(fieldName.type.rawType().name, listOf(it)) }
          }
        }

        return FieldPolicyCacheResolver.resolveField(context)
      }
    }
    val store = CacheManager(MemoryCacheFactory(), cacheResolver = cacheResolver)

    val promoOperation = GetPromoBookQuery()
    store.writeOperation(promoOperation, GetPromoBookQuery.Data(GetPromoBookQuery.PromoBook("Title1", "1", "Book")))
    store.writeOperation(promoOperation, GetPromoBookQuery.Data(GetPromoBookQuery.PromoBook("Title2", "2", "Book")))
    store.writeOperation(promoOperation, GetPromoBookQuery.Data(GetPromoBookQuery.PromoBook("Title3", "3", "Book")))
    store.writeOperation(promoOperation, GetPromoBookQuery.Data(GetPromoBookQuery.PromoBook("Title4", "4", "Book")))

    var operation = GetBooksQuery(listOf("4", "1"))
    var data = store.readOperation(operation).data!!

    assertEquals("Title4", data.books.get(0).title)
    assertEquals("Title1", data.books.get(1).title)

    operation = GetBooksQuery(listOf("3"))
    data = store.readOperation(operation).data!!

    assertEquals("Title3", data.books.get(0).title)
  }
}
