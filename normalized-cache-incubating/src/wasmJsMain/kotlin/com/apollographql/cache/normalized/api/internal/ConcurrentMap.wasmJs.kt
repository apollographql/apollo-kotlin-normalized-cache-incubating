package com.apollographql.cache.normalized.api.internal

internal actual fun <K, V> ConcurrentMap(): MutableMap<K, V> = mutableMapOf()
