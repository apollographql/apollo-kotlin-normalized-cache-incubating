package com.apollographql.cache.normalized.api.internal

import java.util.concurrent.ConcurrentHashMap

internal actual fun <K, V> ConcurrentMap(): MutableMap<K, V> = ConcurrentHashMap()
