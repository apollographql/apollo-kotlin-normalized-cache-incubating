package com.apollographql.cache.normalized

import com.apollographql.apollo.api.ApolloRequest
import com.apollographql.apollo.api.ApolloResponse
import com.apollographql.apollo.api.Operation
import com.apollographql.apollo.exception.CacheMissException
import com.apollographql.apollo.interceptor.ApolloInterceptor
import com.apollographql.apollo.interceptor.ApolloInterceptorChain
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach

class CacheMissLoggingInterceptor(private val log: (String) -> Unit) : ApolloInterceptor {
  override fun <D : Operation.Data> intercept(request: ApolloRequest<D>, chain: ApolloInterceptorChain): Flow<ApolloResponse<D>> {
    return chain.proceed(request).onEach {
      if (it.exception is CacheMissException) {
        log(it.exception!!.message.toString())
      } else
        it.errors.orEmpty().mapNotNull { it.extensions?.get("exception") as? CacheMissException }.forEach {
          log(it.message.toString())
        }
    }
  }
}
