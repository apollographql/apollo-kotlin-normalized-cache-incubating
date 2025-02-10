package test.circular

import circular.GetUserQuery
import com.apollographql.apollo.api.CustomScalarAdapters
import com.apollographql.apollo.testing.internal.runTest
import com.apollographql.cache.normalized.ApolloStore
import com.apollographql.cache.normalized.memory.MemoryCacheFactory
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
    val result = store.readOperation(operation, customScalarAdapters = CustomScalarAdapters.Empty).data!!
    assertEquals("42", result.user.friend.id)
  }
}
