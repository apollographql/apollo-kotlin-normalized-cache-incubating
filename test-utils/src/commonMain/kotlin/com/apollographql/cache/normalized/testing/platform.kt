@file:Suppress("DEPRECATION")

package com.apollographql.cache.normalized.testing

enum class Platform {
  Jvm,
  Native,
  Js,
  WasmJs
}

expect fun platform(): Platform
