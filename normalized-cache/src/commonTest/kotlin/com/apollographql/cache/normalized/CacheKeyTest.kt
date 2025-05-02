package com.apollographql.cache.normalized

import com.apollographql.cache.normalized.api.CacheKey
import kotlin.test.Test
import kotlin.test.assertNotEquals

class CacheKeyTest {
  @Test
  fun noCollisions() {
    assertNotEquals(
        CacheKey("Person", "ann", "acorn").keyToString(),
        CacheKey("Person", "anna", "corn").keyToString(),
    )

    assertNotEquals(
        CacheKey("Type", "a+a", "b").keyToString(),
        CacheKey("Type", "a\\", "a", "b").keyToString(),
    )

  }
}
