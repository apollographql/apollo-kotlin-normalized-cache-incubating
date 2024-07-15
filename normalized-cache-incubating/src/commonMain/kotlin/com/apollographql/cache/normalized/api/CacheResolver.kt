package com.apollographql.cache.normalized.api

import com.apollographql.apollo.api.CompiledField
import com.apollographql.apollo.api.Executable
import com.apollographql.apollo.exception.CacheMissException
import com.apollographql.apollo.mpp.currentTimeMillis
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
 * A cache resolver that uses the cache date as a receive date and expires after a fixed max age
 */
class ReceiveDateCacheResolver(private val maxAge: Int) : CacheResolver {
  override fun resolveField(context: ResolverContext): Any? {
    val parent = context.parent
    val parentKey = context.parentKey

    val fieldKey = context.fieldKeyGenerator.getFieldKey(FieldKeyContext(context.parentType, context.field, context.variables))
    if (!parent.containsKey(fieldKey)) {
      throw CacheMissException(parentKey, fieldKey)
    }

    if (parent is Record) {
      val lastUpdated = parent.dates?.get(fieldKey)
      if (lastUpdated != null) {
        val maxStale = context.cacheHeaders.headerValue(ApolloCacheHeaders.MAX_STALE)?.toLongOrNull() ?: 0L
        if (maxStale < Long.MAX_VALUE) {
          val age = currentTimeMillis() / 1000 - lastUpdated
          if (maxAge + maxStale - age < 0) {
            throw CacheMissException(parentKey, fieldKey, true)
          }
        }
      }
    }

    return parent[fieldKey]
  }
}

/**
 * A cache resolver that uses the cache date as an expiration date and expires past it
 */
class ExpireDateCacheResolver : CacheResolver {
  override fun resolveField(context: ResolverContext): Any? {
    val parent = context.parent
    val parentKey = context.parentKey

    val fieldKey = context.fieldKeyGenerator.getFieldKey(FieldKeyContext(context.parentType, context.field, context.variables))
    if (!parent.containsKey(fieldKey)) {
      throw CacheMissException(parentKey, fieldKey)
    }

    if (parent is Record) {
      val expires = parent.dates?.get(fieldKey)
      if (expires != null) {
        if (currentTimeMillis() / 1000 - expires >= 0) {
          throw CacheMissException(parentKey, fieldKey, true)
        }
      }
    }

    return parent[fieldKey]
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
