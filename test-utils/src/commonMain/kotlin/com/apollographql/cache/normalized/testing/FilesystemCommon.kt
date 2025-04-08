@file:JvmName("-FileSystemCommon")

package com.apollographql.cache.normalized.testing

import com.apollographql.apollo.api.json.JsonReader
import com.apollographql.apollo.api.json.jsonReader
import okio.Path
import okio.Path.Companion.toPath
import okio.buffer
import kotlin.jvm.JvmName


private fun String.toTestsPath(): Path {
  @Suppress("DEPRECATION")
  return testsPath.toPath().resolve(this.toPath())
}

/**
 * @param path: the path to the file, from the "tests" directory
 */
fun pathToUtf8(path: String): String {
  @Suppress("DEPRECATION")
  return HostFileSystem.openReadOnly(path.toTestsPath()).source().buffer().readUtf8()
}

/**
 * @param path: the path to the file, from the "tests" directory
 */
fun pathToJsonReader(path: String): JsonReader {
  @Suppress("DEPRECATION")
  return HostFileSystem.openReadOnly(path.toTestsPath()).source().buffer().jsonReader()
}
