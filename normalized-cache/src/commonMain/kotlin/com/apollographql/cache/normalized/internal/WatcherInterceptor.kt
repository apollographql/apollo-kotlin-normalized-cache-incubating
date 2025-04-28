package com.apollographql.cache.normalized.internal

import com.apollographql.apollo.api.ApolloRequest
import com.apollographql.apollo.api.ApolloResponse
import com.apollographql.apollo.api.CustomScalarAdapters
import com.apollographql.apollo.api.Operation
import com.apollographql.apollo.api.Query
import com.apollographql.apollo.exception.DefaultApolloException
import com.apollographql.apollo.interceptor.ApolloInterceptor
import com.apollographql.apollo.interceptor.ApolloInterceptorChain
import com.apollographql.cache.normalized.CacheManager
import com.apollographql.cache.normalized.api.CacheKey
import com.apollographql.cache.normalized.api.dependentKeys
import com.apollographql.cache.normalized.api.withErrors
import com.apollographql.cache.normalized.watchContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onSubscription

internal val WatcherSentinel = DefaultApolloException("The watcher has started")

internal class WatcherInterceptor(val cacheManager: CacheManager) : ApolloInterceptor {
  override fun <D : Operation.Data> intercept(request: ApolloRequest<D>, chain: ApolloInterceptorChain): Flow<ApolloResponse<D>> {
    val watchContext = request.watchContext ?: return chain.proceed(request)

    check(request.operation is Query) {
      "It's impossible to watch a mutation or subscription"
    }

    val customScalarAdapters = request.executionContext[CustomScalarAdapters]!!

    @Suppress("UNCHECKED_CAST")
    var watchedKeys: Set<String>? =
      watchContext.data?.let { data ->
        val dataWithErrors = (data as D).withErrors(
            executable = request.operation,
            errors = null,
            customScalarAdapters = customScalarAdapters,
        )
        cacheManager.normalize(
            executable = request.operation,
            dataWithErrors = dataWithErrors,
            rootKey = CacheKey.QUERY_ROOT,
            customScalarAdapters = customScalarAdapters,
        ).values.dependentKeys()
      }

    return (cacheManager.changedKeys as SharedFlow<Any>)
        .onSubscription {
          emit(Unit)
        }
        .filter { changedKeys ->
          changedKeys !is Set<*> ||
              changedKeys === CacheManager.ALL_KEYS ||
              watchedKeys == null ||
              changedKeys.intersect(watchedKeys!!).isNotEmpty()
        }.map {
          if (it == Unit) {
            flowOf(ApolloResponse.Builder(request.operation, request.requestUuid).exception(WatcherSentinel).build())
          } else {
            chain.proceed(request)
                .onEach { response ->
                  if (response.data != null) {
                    val dataWithErrors = response.data!!.withErrors(
                        executable = request.operation,
                        errors = response.errors,
                        customScalarAdapters = customScalarAdapters,
                    )
                    watchedKeys = cacheManager.normalize(
                        executable = request.operation,
                        dataWithErrors = dataWithErrors,
                        rootKey = CacheKey.QUERY_ROOT,
                        customScalarAdapters = customScalarAdapters,
                    ).values.dependentKeys()
                  }
                }
          }
        }
        .flattenConcatPolyfill()
  }
}

/**
 * A copy/paste of the kotlinx.coroutines version until it becomes stable
 *
 * This is taken from 1.5.2 and replacing `unsafeFlow {}` with `flow {}`
 */
private fun <T> Flow<Flow<T>>.flattenConcatPolyfill(): Flow<T> = flow {
  collect { value -> emitAll(value) }
}
