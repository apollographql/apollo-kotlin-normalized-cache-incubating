package pagination

import com.apollographql.apollo.api.Optional
import com.apollographql.apollo.api.json.ApolloJsonElement
import com.apollographql.apollo.testing.internal.runTest
import com.apollographql.cache.normalized.ApolloStore
import com.apollographql.cache.normalized.api.FieldPolicyCacheResolver
import com.apollographql.cache.normalized.api.FieldRecordMerger
import com.apollographql.cache.normalized.api.MetadataGenerator
import com.apollographql.cache.normalized.api.MetadataGeneratorContext
import com.apollographql.cache.normalized.api.NormalizedCacheFactory
import com.apollographql.cache.normalized.api.TypePolicyCacheKeyGenerator
import com.apollographql.cache.normalized.memory.MemoryCacheFactory
import com.apollographql.cache.normalized.sql.SqlNormalizedCacheFactory
import pagination.offsetBasedWithPage.UsersQuery
import pagination.offsetBasedWithPage.type.buildUser
import pagination.offsetBasedWithPage.type.buildUserPage
import kotlin.math.max
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertEquals

class OffsetBasedWithPagePaginationTest {
  @Test
  fun memoryCache() {
    test(MemoryCacheFactory())
  }

  @Test
  fun sqlCache() {
    test(SqlNormalizedCacheFactory())
  }

  @Test
  fun chainedCache() {
    test(MemoryCacheFactory().chain(SqlNormalizedCacheFactory()))
  }

  private fun test(cacheFactory: NormalizedCacheFactory) = runTest {
    val apolloStore = ApolloStore(
        normalizedCacheFactory = cacheFactory,
        cacheKeyGenerator = TypePolicyCacheKeyGenerator,
        metadataGenerator = OffsetPaginationMetadataGenerator("UserPage"),
        cacheResolver = FieldPolicyCacheResolver,
        recordMerger = FieldRecordMerger(OffsetPaginationFieldMerger())
    )
    apolloStore.clearAll()

    // First page
    val query1 = UsersQuery(offset = Optional.Present(42), limit = Optional.Present(2))
    val data1 = UsersQuery.Data {
      users = buildUserPage {
        users = listOf(
            buildUser { id = "42" },
            buildUser { id = "43" },
        )
      }
    }
    apolloStore.writeOperation(query1, data1)
    var dataFromStore = apolloStore.readOperation(query1).data
    assertEquals(data1, dataFromStore)

    // Page after
    val query2 = UsersQuery(offset = Optional.Present(44), limit = Optional.Present(2))
    val data2 = UsersQuery.Data {
      users = buildUserPage {
        users = listOf(
            buildUser { id = "44" },
            buildUser { id = "45" },
        )
      }
    }
    apolloStore.writeOperation(query2, data2)
    dataFromStore = apolloStore.readOperation(query1).data
    var expectedData = UsersQuery.Data {
      users = buildUserPage {
        users = listOf(
            buildUser { id = "42" },
            buildUser { id = "43" },
            buildUser { id = "44" },
            buildUser { id = "45" },
        )
      }
    }
    assertEquals(expectedData, dataFromStore)

    // Page in the middle
    val query3 = UsersQuery(offset = Optional.Present(44), limit = Optional.Present(3))
    val data3 = UsersQuery.Data {
      users = buildUserPage {
        users = listOf(
            buildUser { id = "44" },
            buildUser { id = "45" },
            buildUser { id = "46" },
        )
      }
    }
    apolloStore.writeOperation(query3, data3)
    dataFromStore = apolloStore.readOperation(query1).data
    expectedData = UsersQuery.Data {
      users = buildUserPage {
        users = listOf(
            buildUser { id = "42" },
            buildUser { id = "43" },
            buildUser { id = "44" },
            buildUser { id = "45" },
            buildUser { id = "46" },
        )
      }
    }
    assertEquals(expectedData, dataFromStore)

    // Page before
    val query4 = UsersQuery(offset = Optional.Present(40), limit = Optional.Present(2))
    val data4 = UsersQuery.Data {
      users = buildUserPage {
        users = listOf(
            buildUser { id = "40" },
            buildUser { id = "41" },
        )
      }
    }
    apolloStore.writeOperation(query4, data4)
    dataFromStore = apolloStore.readOperation(query1).data
    expectedData = UsersQuery.Data {
      users = buildUserPage {
        users = listOf(
            buildUser { id = "40" },
            buildUser { id = "41" },
            buildUser { id = "42" },
            buildUser { id = "43" },
            buildUser { id = "44" },
            buildUser { id = "45" },
            buildUser { id = "46" },
        )
      }
    }
    assertEquals(expectedData, dataFromStore)

    // Non-contiguous page (should reset)
    val query5 = UsersQuery(offset = Optional.Present(50), limit = Optional.Present(2))
    val data5 = UsersQuery.Data {
      users = buildUserPage {
        users = listOf(
            buildUser { id = "50" },
            buildUser { id = "51" },
        )
      }
    }
    apolloStore.writeOperation(query5, data5)
    dataFromStore = apolloStore.readOperation(query1).data
    assertEquals(data5, dataFromStore)

    // Empty page (should keep previous result)
    val query6 = UsersQuery(offset = Optional.Present(52), limit = Optional.Present(2))
    val data6 = UsersQuery.Data {
      users = buildUserPage {
        users = emptyList()
      }
    }
    apolloStore.writeOperation(query6, data6)
    dataFromStore = apolloStore.readOperation(query1).data
    assertEquals(data5, dataFromStore)
  }

  private class OffsetPaginationMetadataGenerator(private val typeName: String) : MetadataGenerator {
    override fun metadataForObject(obj: ApolloJsonElement, context: MetadataGeneratorContext): Map<String, ApolloJsonElement> {
      if (context.field.type.rawType().name == typeName) {
        return mapOf("offset" to context.argumentValue("offset"))
      }
      return emptyMap()
    }
  }

  private class OffsetPaginationFieldMerger : FieldRecordMerger.FieldMerger {
    override fun mergeFields(existing: FieldRecordMerger.FieldInfo, incoming: FieldRecordMerger.FieldInfo): FieldRecordMerger.FieldInfo {
      val existingOffset = existing.metadata["offset"] as? Int
      val incomingOffset = incoming.metadata["offset"] as? Int
      return if (existingOffset == null || incomingOffset == null) {
        incoming
      } else {
        val existingValue = existing.value as Map<*, *>
        val existingList = existingValue["users"] as List<*>
        val incomingList = (incoming.value as Map<*, *>)["users"] as List<*>
        val (mergedList, mergedOffset) = mergeLists(existingList, incomingList, existingOffset, incomingOffset)
        val mergedFieldValue = existingValue.toMutableMap()
        mergedFieldValue["users"] = mergedList
        FieldRecordMerger.FieldInfo(
            value = mergedFieldValue,
            metadata = mapOf("offset" to mergedOffset)
        )
      }
    }

    private fun <T> mergeLists(existing: List<T>, incoming: List<T>, existingOffset: Int, incomingOffset: Int): Pair<List<T>, Int> {
      if (incomingOffset > existingOffset + existing.size) {
        // Incoming list's first item is further than immediately after the existing list's last item: can't merge. Handle it as a reset.
        return incoming to incomingOffset
      }

      if (incomingOffset + incoming.size < existingOffset) {
        // Incoming list's last item is further than immediately before the existing list's first item: can't merge. Handle it as a reset.
        return incoming to incomingOffset
      }

      val merged = mutableListOf<T>()
      val startOffset = min(existingOffset, incomingOffset)
      val endOffset = max(existingOffset + existing.size, incomingOffset + incoming.size)
      val incomingRange = incomingOffset until incomingOffset + incoming.size
      for (i in startOffset until endOffset) {
        if (i in incomingRange) {
          merged.add(incoming[i - incomingOffset])
        } else {
          merged.add(existing[i - existingOffset])
        }
      }
      return merged to startOffset
    }
  }
}
