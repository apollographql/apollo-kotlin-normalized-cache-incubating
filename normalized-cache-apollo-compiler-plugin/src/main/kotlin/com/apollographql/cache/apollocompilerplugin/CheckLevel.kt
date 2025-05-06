package com.apollographql.cache.apollocompilerplugin

enum class CheckLevel {
  DISABLED,
  WARNING,
  ERROR;

  companion object {
    fun from(value: Any): CheckLevel? {
      return when (value) {
        "disabled" -> DISABLED
        "warning" -> WARNING
        "error" -> ERROR
        else -> null
      }
    }
  }
}
