package com.apollographql.cache.normalized.api

import com.apollographql.apollo.api.CompiledField
import com.apollographql.apollo.api.Executable
import com.apollographql.apollo.api.MutableExecutionOptions
import com.apollographql.apollo.exception.CacheMissException
import com.apollographql.apollo.mpp.currentTimeMillis
import com.apollographql.cache.normalized.maxStale
import com.apollographql.cache.normalized.storeExpirationDate
import com.apollographql.cache.normalized.storeReceiveDate
import kotlin.jvm.JvmSuppressWildcards

/**
 * Controls how fields are resolved from the cache.
 */
interface CacheResolver {
  /**
   * Resolves a field from the cache. Called when reading from the cache, usually before a network request.
   * - takes a GraphQL field and operation variables as input and generates data for this field
   * - this data can be a CacheKey for objects but it can also be any other data if needed. In that respect,
   * it's closer to a resolver as might be found in a GraphQL server
   * - used before a network request
   * - used when reading the cache
   *
   * It can be used to map field arguments to [CacheKey]:
   *
   * ```
   * {
   *   user(id: "1"}) {
   *     id
   *     firstName
   *     lastName
   *   }
   * }
   * ```
   *
   * ```
   * override fun resolveField(context: ResolverContext): Any? {
   *   val id = context.field.resolveArgument("id", context.variables)?.toString()
   *   if (id != null) {
   *     return CacheKey(id)
   *   }
   *
   *   return super.resolveField(context)
   * }
   * ```
   *
   * The simple example above isn't very representative as most of the time `@fieldPolicy` can express simple argument mappings in a more
   * concise way but still demonstrates how [resolveField] works.
   *
   * [resolveField] can also be generalized to return any value:
   *
   * ```
   * override fun resolveField(context: ResolverContext): Any? {
   *   if (context.field.name == "name") {
   *     // Every "name" field will return "JohnDoe" now!
   *     return "JohnDoe"
   *   }
   *
   *   return super.resolveField(context)
   * }
   * ```
   *
   * See also `@fieldPolicy`
   * See also [CacheKeyGenerator]
   *
   * @param context the field to resolve and associated information to resolve it
   *
   * @return a value that can go in a [Record]. No type checking is done. It is the responsibility of implementations to return the correct
   * type
   */
  fun resolveField(context: ResolverContext): Any?
}

class ResolverContext(
    /**
     * The field to resolve
     */
    val field: CompiledField,

    /**
     * The variables of the current operation
     */
    val variables: Executable.Variables,

    /**
     * The parent object as a map. It can contain the same values as [Record]. Especially, nested objects will be represented
     * by [CacheKey]
     */
    val parent: Map<String, @JvmSuppressWildcards Any?>,

    /**
     * The key of the parent. Mainly used for debugging
     */
    val parentKey: String,

    /**
     * The type of the parent
     */
    val parentType: String,

    /**
     * The cache headers used to pass arbitrary information to the resolver
     */
    val cacheHeaders: CacheHeaders,

    /**
     * The [FieldKeyGenerator] to use to generate field keys
     */
    val fieldKeyGenerator: FieldKeyGenerator,
)

/**
 * A cache resolver that uses the parent to resolve fields.
 */
object DefaultCacheResolver : CacheResolver {
  override fun resolveField(context: ResolverContext): Any? {
    val fieldKey = context.fieldKeyGenerator.getFieldKey(FieldKeyContext(context.parentType, context.field, context.variables))
    if (!context.parent.containsKey(fieldKey)) {
      throw CacheMissException(context.parentKey, fieldKey)
    }

    return context.parent[fieldKey]
  }
}

/**
 * A cache resolver that raises a cache miss if the field's received date is older than its max age
 * (configurable via [maxAgeProvider]) or its expiration date has passed.
 *
 * Received dates are stored by calling `storeReceiveDate(true)` on your `ApolloClient`.
 *
 * Expiration dates are stored by calling `storeExpirationDate(true)` on your `ApolloClient`.
 *
 * A maximum staleness can be configured via the [ApolloCacheHeaders.MAX_STALE] cache header.
 *
 * @see MutableExecutionOptions.storeReceiveDate
 * @see MutableExecutionOptions.storeExpirationDate
 * @see MutableExecutionOptions.maxStale
 */
class ExpirationCacheResolver(
    private val maxAgeProvider: MaxAgeProvider,
) : CacheResolver {
  override fun resolveField(context: ResolverContext): Any? {
    val resolvedField = FieldPolicyCacheResolver.resolveField(context)
    if (context.parent is Record) {
      val field = context.field
      val maxStale = context.cacheHeaders.headerValue(ApolloCacheHeaders.MAX_STALE)?.toLongOrNull() ?: 0L
      val currentDate = currentTimeMillis() / 1000

      // Consider the field's max age (client side)
      val fieldMaxAge = maxAgeProvider.getMaxAge(MaxAgeContext(field = field, parentType = context.parentType))?.inWholeSeconds
      if (fieldMaxAge != null) {
        val fieldReceivedDate = context.parent.receivedDate(field.name)
        if (fieldReceivedDate != null) {
          val fieldAge = currentDate - fieldReceivedDate
          val stale = fieldAge - fieldMaxAge
          if (stale >= maxStale) {
            throw CacheMissException(
                context.parentKey,
                context.fieldKeyGenerator.getFieldKey(FieldKeyContext(context.parentType, context.field, context.variables)),
                true
            )
          }
        }
      }

      // Consider the field's expiration date (server side)
      val fieldExpirationDate = context.parent.expirationDate(field.name)
      if (fieldExpirationDate != null) {
        val stale = currentDate - fieldExpirationDate
        if (stale >= maxStale) {
          throw CacheMissException(
              context.parentKey,
              context.fieldKeyGenerator.getFieldKey(FieldKeyContext(context.parentType, context.field, context.variables)),
              true
          )
        }
      }
    }

    return resolvedField
  }
}

/**
 * A cache resolver that uses `@fieldPolicy` annotations to resolve fields and delegates to [DefaultCacheResolver] otherwise
 */
object FieldPolicyCacheResolver : CacheResolver {
  override fun resolveField(context: ResolverContext): Any? {
    val keyArgsValues = context.field.argumentValues(context.variables) { it.definition.isKey }.values.map { it.toString() }

    if (keyArgsValues.isNotEmpty()) {
      return CacheKey(context.field.type.rawType().name, keyArgsValues)
    }

    return DefaultCacheResolver.resolveField(context)
  }
}
