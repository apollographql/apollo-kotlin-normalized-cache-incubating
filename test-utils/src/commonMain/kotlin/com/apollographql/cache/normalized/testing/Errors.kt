package com.apollographql.cache.normalized.testing

import com.apollographql.apollo.api.Error
import com.apollographql.apollo.api.Error.Location
import kotlin.test.assertContentEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Helps using assertEquals on Errors.
 */
private data class ComparableError(
    val message: String,
    val locations: List<Location>?,
    val path: List<Any>?,
)

fun assertErrorsEquals(expected: Iterable<Error>?, actual: Iterable<Error>?) =
  assertContentEquals(expected?.map {
    ComparableError(
        message = it.message,
        locations = it.locations,
        path = it.path,
    )
  }, actual?.map {
    ComparableError(
        message = it.message,
        locations = it.locations,
        path = it.path,
    )
  })

fun assertErrorsEquals(expected: Error?, actual: Error?) {
  if (expected == null) {
    assertNull(actual)
    return
  }
  assertNotNull(actual)
  assertErrorsEquals(listOf(expected), listOf(actual))
}
