package com.apollographql.cache.normalized.api.internal

internal expect fun <K, V> ConcurrentMap(): MutableMap<K, V>
