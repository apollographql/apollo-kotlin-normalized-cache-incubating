import com.apollographql.apollo.api.ApolloResponse
import com.apollographql.apollo.api.CustomScalarAdapters
import com.apollographql.apollo.api.Operation
import com.apollographql.apollo.api.Optional
import com.apollographql.apollo.api.json.MapJsonReader
import com.apollographql.apollo.api.toApolloResponse
import com.apollographql.apollo.testing.internal.runTest
import com.apollographql.cache.normalized.api.TypePolicyCacheKeyGenerator
import com.apollographql.cache.normalized.api.normalize
import com.example.GetCatIncludeVariableWithDefaultQuery
import com.example.SkipFragmentWithDefaultToFalseQuery
import com.example.type.buildCat
import com.example.type.buildDog
import kotlin.test.Test
import kotlin.test.assertNull

class IncludeTest {
  private fun <D : Operation.Data> Operation<D>.parseData(data: Map<String, Any?>): ApolloResponse<D> {
    return MapJsonReader(mapOf("data" to data)).toApolloResponse(this)
  }

  @Test
  fun getCatIncludeVariableWithDefaultQuery() = runTest {
    val operation = GetCatIncludeVariableWithDefaultQuery()

    val data = GetCatIncludeVariableWithDefaultQuery.Data {
      animal = buildCat {
        this["species"] = Optional.Absent
      }
    }

    val normalized = operation.normalize(data, CustomScalarAdapters.Empty, TypePolicyCacheKeyGenerator)
    assertNull((normalized["animal"] as Map<*, *>)["species"])
  }

  @Test
  fun skipFragmentWithDefaultToFalseQuery2() = runTest {
    val operation = SkipFragmentWithDefaultToFalseQuery()

    val data = SkipFragmentWithDefaultToFalseQuery.Data {
      animal = buildDog {
        barf = "ouaf"
      }
    }

    val normalized = operation.normalize(data, CustomScalarAdapters.Empty, TypePolicyCacheKeyGenerator)
    assertNull((normalized["animal"] as Map<*, *>)["barf"])
  }
}
