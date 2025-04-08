package test.circular

import circular.GetUserQuery
import com.apollographql.cache.normalized.ApolloStore
import com.apollographql.cache.normalized.memory.MemoryCacheFactory
import com.apollographql.cache.normalized.testing.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class CircularCacheReadTest {
  @Test
  fun circularReferenceDoesNotStackOverflow() = runTest {
    val store = ApolloStore(MemoryCacheFactory())

    val operation = GetUserQuery()

    /**
     * Create a record that references itself. It should not create a stack overflow
     */
    val data = GetUserQuery.Data(
        GetUserQuery.User(
            "42",
            GetUserQuery.Friend(
                "42",
                "User"
            ),
            "User",
        )
    )

    store.writeOperation(operation, data)
    val result = store.readOperation(operation).data!!
    assertEquals("42", result.user.friend.id)
  }
}
