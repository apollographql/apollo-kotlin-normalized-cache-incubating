package com.apollographql.cache.normalized.internal

internal actual fun <K, V> ConcurrentMap(): MutableMap<K, V> = mutableMapOf()
