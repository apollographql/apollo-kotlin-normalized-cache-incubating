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
    val cacheManager = CacheManager(MemoryCacheFactory())

    // Write a book at the "promo" path
    val promoOperation = GetPromoBookQuery()
    val promoData = GetPromoBookQuery.Data(GetPromoBookQuery.PromoBook("Promo", "42", "Book"))
    cacheManager.writeOperation(promoOperation, promoData)

    // Overwrite the book title through the "other" path
    val otherOperation = GetOtherBookQuery()
    val otherData = GetOtherBookQuery.Data(GetOtherBookQuery.OtherBook("42", "Other", "Book"))
    cacheManager.writeOperation(otherOperation, otherData)

    // Get the "promo" book again, the title must be updated
    val data = cacheManager.readOperation(promoOperation).data!!

    assertEquals("Other", data.promoBook?.title)
  }

  @Test
  fun fallbackIdIsWorking() = runTest {
    val cacheManager = CacheManager(MemoryCacheFactory())

    // Write a library at the "promo" path
    val promoOperation = GetPromoLibraryQuery()
    val promoData = GetPromoLibraryQuery.Data(GetPromoLibraryQuery.PromoLibrary("PromoAddress", "3", "Library"))
    cacheManager.writeOperation(promoOperation, promoData)

    // Overwrite the library address through the "other" path
    val otherOperation = GetOtherLibraryQuery()
    val otherData = GetOtherLibraryQuery.Data(GetOtherLibraryQuery.OtherLibrary("3", "OtherAddress", "Library"))
    cacheManager.writeOperation(otherOperation, otherData)

    // Get the "promo" library again, the address must be updated
    val data = cacheManager.readOperation(promoOperation).data!!

    assertEquals("OtherAddress", data.promoLibrary?.address)
  }

  @Test
  fun fieldPolicyIsWorking() = runTest {
    val cacheManager = CacheManager(MemoryCacheFactory())

    val bookQuery1 = GetPromoBookQuery()
    val bookData1 = GetPromoBookQuery.Data(GetPromoBookQuery.PromoBook("Promo", "42", "Book"))
    cacheManager.writeOperation(bookQuery1, bookData1)

    val bookQuery2 = GetBookQuery("42")
    val bookData2 = cacheManager.readOperation(bookQuery2).data!!

    assertEquals("Promo", bookData2.book?.title)

    val authorQuery1 = GetPromoAuthorQuery()
    val authorData1 = GetPromoAuthorQuery.Data(
        GetPromoAuthorQuery.PromoAuthor(
            "Pierre",
            "Bordage",
            "Author"
        )
    )

    cacheManager.writeOperation(authorQuery1, authorData1)

    val authorQuery2 = GetAuthorQuery("Pierre", "Bordage")
    val authorData2 = cacheManager.readOperation(authorQuery2).data!!

    assertEquals("Pierre", authorData2.author?.firstName)
    assertEquals("Bordage", authorData2.author?.lastName)
  }

  @Test
  fun fieldPolicyWithLists() = runTest {
    val cacheManager = CacheManager(MemoryCacheFactory())
    cacheManager.writeOperation(GetPromoBookQuery(), GetPromoBookQuery.Data(GetPromoBookQuery.PromoBook(title = "Promo", isbn = "42", __typename = "Book")))
    cacheManager.writeOperation(GetOtherBookQuery(), GetOtherBookQuery.Data(GetOtherBookQuery.OtherBook(isbn = "43", title = "Other Book", __typename = "Book")))

    val booksQuery = GetBooksQuery(listOf("42", "43"))
    val booksCacheResponse = cacheManager.readOperation(booksQuery)
    val booksData = booksCacheResponse.data!!
    assertEquals(2, booksData.books.size)
    assertEquals(GetBooksQuery.Book("Promo", "42", "Book"), booksData.books[0])
    assertEquals(GetBooksQuery.Book("Other Book", "43", "Book"), booksData.books[1])
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
    val cacheManager = CacheManager(MemoryCacheFactory(), cacheResolver = cacheResolver)

    val promoOperation = GetPromoBookQuery()
    cacheManager.writeOperation(promoOperation, GetPromoBookQuery.Data(GetPromoBookQuery.PromoBook("Title1", "1", "Book")))
    cacheManager.writeOperation(promoOperation, GetPromoBookQuery.Data(GetPromoBookQuery.PromoBook("Title2", "2", "Book")))
    cacheManager.writeOperation(promoOperation, GetPromoBookQuery.Data(GetPromoBookQuery.PromoBook("Title3", "3", "Book")))
    cacheManager.writeOperation(promoOperation, GetPromoBookQuery.Data(GetPromoBookQuery.PromoBook("Title4", "4", "Book")))

    var operation = GetBooksQuery(listOf("4", "1"))
    var data = cacheManager.readOperation(operation).data!!

    assertEquals("Title4", data.books.get(0).title)
    assertEquals("Title1", data.books.get(1).title)

    operation = GetBooksQuery(listOf("3"))
    data = cacheManager.readOperation(operation).data!!

    assertEquals("Title3", data.books.get(0).title)
  }
}
