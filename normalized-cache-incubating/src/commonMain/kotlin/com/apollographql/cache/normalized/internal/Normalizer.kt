package com.apollographql.cache.normalized.internal

import com.apollographql.apollo.api.CompiledField
import com.apollographql.apollo.api.CompiledFragment
import com.apollographql.apollo.api.CompiledListType
import com.apollographql.apollo.api.CompiledNamedType
import com.apollographql.apollo.api.CompiledNotNullType
import com.apollographql.apollo.api.CompiledSelection
import com.apollographql.apollo.api.CompiledType
import com.apollographql.apollo.api.CustomScalarAdapters
import com.apollographql.apollo.api.Error
import com.apollographql.apollo.api.Executable
import com.apollographql.apollo.api.isComposite
import com.apollographql.apollo.api.variables
import com.apollographql.cache.normalized.api.CacheKey
import com.apollographql.cache.normalized.api.CacheKeyGenerator
import com.apollographql.cache.normalized.api.CacheKeyGeneratorContext
import com.apollographql.cache.normalized.api.DataWithErrors
import com.apollographql.cache.normalized.api.DefaultEmbeddedFieldsProvider
import com.apollographql.cache.normalized.api.DefaultFieldKeyGenerator
import com.apollographql.cache.normalized.api.EmbeddedFieldsContext
import com.apollographql.cache.normalized.api.EmbeddedFieldsProvider
import com.apollographql.cache.normalized.api.EmptyMetadataGenerator
import com.apollographql.cache.normalized.api.FieldKeyContext
import com.apollographql.cache.normalized.api.FieldKeyGenerator
import com.apollographql.cache.normalized.api.MetadataGenerator
import com.apollographql.cache.normalized.api.MetadataGeneratorContext
import com.apollographql.cache.normalized.api.Record
import com.apollographql.cache.normalized.api.TypePolicyCacheKeyGenerator
import com.apollographql.cache.normalized.api.append
import com.apollographql.cache.normalized.api.isRootKey
import com.apollographql.cache.normalized.api.withErrors

/**
 * A [Normalizer] takes a [Map]<String, Any?> and turns them into a flat list of [Record]
 * The key of each [Record] is given by [cacheKeyGenerator] or defaults to using the path
 */
internal class Normalizer(
    private val variables: Executable.Variables,
    private val rootKey: CacheKey,
    private val cacheKeyGenerator: CacheKeyGenerator,
    private val metadataGenerator: MetadataGenerator,
    private val fieldKeyGenerator: FieldKeyGenerator,
    private val embeddedFieldsProvider: EmbeddedFieldsProvider,
) {
  private val records = mutableMapOf<CacheKey, Record>()

  fun normalize(
      map: DataWithErrors,
      selections: List<CompiledSelection>,
      parentType: CompiledNamedType,
  ): Map<CacheKey, Record> {
    buildRecord(map, rootKey, selections, parentType)

    return records
  }

  private class FieldInfo(
      val fieldValue: Any?,
      val metadata: Map<String, Any?>,
  )

  /**
   *
   *
   * @param obj the json node representing the object
   * @param key the key for this record
   * @param selections the selections queried on this object
   * @return the CacheKey if this object has a CacheKey or the new Map if the object was embedded
   */
  private fun buildFields(
      obj: DataWithErrors,
      key: CacheKey,
      selections: List<CompiledSelection>,
      parentType: CompiledNamedType,
  ): Map<String, FieldInfo> {

    val typename = obj["__typename"] as? String
    val allFields = collectFields(selections, parentType.name, typename)

    val fields = obj.entries.mapNotNull { entry ->
      val compiledFields = allFields.filter { it.responseName == entry.key }
      if (compiledFields.isEmpty()) {
        // If we come here, `obj` contains more data than the CompiledSelections can understand
        // This happened previously (see https://github.com/apollographql/apollo-kotlin/pull/3636)
        // It also happens if there's an always false @include directive (see https://github.com/apollographql/apollo-kotlin/issues/4772)
        // For all cache purposes, this is not part of the response and we therefore do not include this in the response
        return@mapNotNull null
      }
      val includedFields = compiledFields.filter {
        !it.shouldSkip(variableValues = variables.valueMap)
      }
      if (includedFields.isEmpty()) {
        // If the field is absent, we don't want to serialize "null" to the cache, do not include this field in the record.
        return@mapNotNull null
      }
      val mergedField = includedFields.first().newBuilder()
          .selections(includedFields.flatMap { it.selections })
          .condition(emptyList())
          .build()

      val fieldKey = fieldKeyGenerator.getFieldKey(FieldKeyContext(parentType.name, mergedField, variables))

      val base = if (key.isRootKey()) {
        // If we're at the root level, skip `QUERY_ROOT` altogether to save a few bytes
        null
      } else {
        key
      }
      val value = replaceObjects(
          value = entry.value,
          field = mergedField,
          type_ = mergedField.type,
          path = base?.append(fieldKey) ?: CacheKey(fieldKey),
          embeddedFields = embeddedFieldsProvider.getEmbeddedFields(EmbeddedFieldsContext(parentType)),
      )
      val metadata = if (entry.value is Error) {
        emptyMap()
      } else {
        metadataGenerator.metadataForObject(entry.value, MetadataGeneratorContext(field = mergedField, variables))
      }
      fieldKey to FieldInfo(value, metadata)
    }.toMap()

    return fields
  }

  /**
   * @param obj the json node representing the object
   * @param cacheKey the key for this record
   * @param selections the selections queried on this object
   * @return the CacheKey if this object has a CacheKey or the new Map if the object was embedded
   */
  private fun buildRecord(
      obj: DataWithErrors,
      cacheKey: CacheKey,
      selections: List<CompiledSelection>,
      parentType: CompiledNamedType,
  ): CacheKey {
    val fields = buildFields(obj, cacheKey, selections, parentType)
    val fieldValues = fields.mapValues { it.value.fieldValue }
    val metadata = fields.mapValues { it.value.metadata }.filterValues { it.isNotEmpty() }
    val record = Record(
        key = cacheKey,
        fields = fieldValues,
        mutationId = null,
        metadata = metadata,
    )

    val existingRecord = records[cacheKey]

    val mergedRecord = if (existingRecord != null) {
      /**
       * A query might contain the same object twice, we don't want to lose some fields when that happens
       */
      existingRecord.mergeWith(record).first
    } else {
      record
    }
    records[cacheKey] = mergedRecord

    return cacheKey
  }

  /**
   * Replace all objects in [value] with [CacheKey] and if [value] is an object itself, returns it as a [CacheKey]
   *
   * This function builds the list of records as a side effect
   *
   * @param value a json value from the response. Can be [com.apollographql.apollo.api.json.ApolloJsonElement] or [Error]
   * @param field the field currently being normalized
   * @param type_ the type currently being normalized. It can be different from `field.type` for lists.
   * @param embeddedFields the embedded fields of the parent
   */
  private fun replaceObjects(
      value: Any?,
      field: CompiledField,
      type_: CompiledType,
      path: CacheKey,
      embeddedFields: List<String>,
  ): Any? {
    /**
     * Remove the NotNull decoration if needed
     */
    val type = if (type_ is CompiledNotNullType) {
      check(value != null)
      type_.ofType
    } else {
      if (value == null) {
        return null
      }
      type_
    }

    return when {
      // Keep errors as-is
      value is Error -> value

      type is CompiledListType -> {
        check(value is List<*>)
        value.mapIndexed { index, item ->
          replaceObjects(item, field, type.ofType, path.append(index.toString()), embeddedFields)
        }
      }
      // Check for [isComposite] as we don't want to build a record for json scalars
      type is CompiledNamedType && type.isComposite() -> {
        check(value is Map<*, *>)
        @Suppress("UNCHECKED_CAST")
        var key = cacheKeyGenerator.cacheKeyForObject(
            value as Map<String, Any?>,
            CacheKeyGeneratorContext(field, variables),
        )

        if (key == null) {
          key = path
        }
        if (embeddedFields.contains(field.name)) {
          buildFields(value, key, field.selections, field.type.rawType())
              .mapValues { it.value.fieldValue }
        } else {
          buildRecord(value, key, field.selections, field.type.rawType())
        }
      }

      else -> {
        // scalar
        value
      }
    }
  }

  private class CollectState {
    val fields = mutableListOf<CompiledField>()
  }

  private fun collectFields(selections: List<CompiledSelection>, parentType: String, typename: String?, state: CollectState) {
    selections.forEach {
      when (it) {
        is CompiledField -> {
          state.fields.add(it)
        }

        is CompiledFragment -> {
          if (typename in it.possibleTypes || it.typeCondition == parentType) {
            collectFields(it.selections, parentType, typename, state)
          }
        }
      }
    }
  }

  /**
   * @param typename the typename of the object. It might be null if the `__typename` field wasn't queried. If
   * that's the case, we will collect less fields than we should and records will miss some values leading to more
   * cache miss
   */
  private fun collectFields(selections: List<CompiledSelection>, parentType: String, typename: String?): List<CompiledField> {
    val state = CollectState()
    collectFields(selections, parentType, typename, state)
    return state.fields
  }
}

/**
 * Normalizes this executable data to a map of [Record] keyed by [Record.key].
 */
fun <D : Executable.Data> D.normalized(
    executable: Executable<D>,
    rootKey: CacheKey = CacheKey.rootKey(),
    customScalarAdapters: CustomScalarAdapters = CustomScalarAdapters.Empty,
    cacheKeyGenerator: CacheKeyGenerator = TypePolicyCacheKeyGenerator,
    metadataGenerator: MetadataGenerator = EmptyMetadataGenerator,
    fieldKeyGenerator: FieldKeyGenerator = DefaultFieldKeyGenerator,
    embeddedFieldsProvider: EmbeddedFieldsProvider = DefaultEmbeddedFieldsProvider,
): Map<CacheKey, Record> {
  val dataWithErrors = this.withErrors(executable, null, customScalarAdapters)
  return dataWithErrors.normalized(executable, rootKey, customScalarAdapters, cacheKeyGenerator, metadataGenerator, fieldKeyGenerator, embeddedFieldsProvider)
}

/**
 * Normalizes this executable data to a map of [Record] keyed by [Record.key].
 */
fun <D : Executable.Data> DataWithErrors.normalized(
    executable: Executable<D>,
    rootKey: CacheKey = CacheKey.rootKey(),
    customScalarAdapters: CustomScalarAdapters = CustomScalarAdapters.Empty,
    cacheKeyGenerator: CacheKeyGenerator = TypePolicyCacheKeyGenerator,
    metadataGenerator: MetadataGenerator = EmptyMetadataGenerator,
    fieldKeyGenerator: FieldKeyGenerator = DefaultFieldKeyGenerator,
    embeddedFieldsProvider: EmbeddedFieldsProvider = DefaultEmbeddedFieldsProvider,
): Map<CacheKey, Record> {
  val variables = executable.variables(customScalarAdapters, withDefaultValues = true)
  return Normalizer(variables, rootKey, cacheKeyGenerator, metadataGenerator, fieldKeyGenerator, embeddedFieldsProvider)
      .normalize(this, executable.rootField().selections, executable.rootField().type.rawType())
}
