package com.apollographql.cache.normalized

import com.apollographql.cache.normalized.internal.patternToRegex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

class PatternTest {
  @Test
  fun test() {
    assertEquals(true, matches("%", "hello"))
    assertEquals(true, matches("%", ""))
    assertEquals(true, matches("%", "_(*"))
    assertEquals(true, matches("hello%", "helloStore"))
    assertEquals(true, matches("%hello%", "HohelloStore"))
    assertEquals(true, matches("%he_lo%", "HohelloStore"))
    assertEquals(true, matches("%he_lo%", "Hohe)loStore"))
    assertEquals(true, matches("\\%", "%"))

    assertEquals(false, matches("\\%", "hello"))

    assertFails {
      matches("\\hello", "hello")
    }
  }

  private fun matches(pattern: String, str: String): Boolean {
    return patternToRegex(pattern).matches(str)
  }
}
