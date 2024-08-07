package com.apollographql.cache.normalized.api

import com.apollographql.apollo.api.CompiledNamedType
import com.apollographql.apollo.api.InterfaceType
import com.apollographql.apollo.api.ObjectType

/**
 * A provider for fields whose value should be embedded in their [Record], rather than being dereferenced during normalization.
 *
 * An [EmbeddedFieldsProvider] can be used in conjunction with [RecordMerger] and [MetadataGenerator] to access multiple fields and their metadata in a single
 * [Record].
 */
interface EmbeddedFieldsProvider {
  /**
   * Returns the fields that should be embedded, given a [context]`.parentType`.
   */
  fun getEmbeddedFields(context: EmbeddedFieldsContext): List<String>
}

/**
 * A context passed to [EmbeddedFieldsProvider.getEmbeddedFields].
 * @see [EmbeddedFieldsProvider.getEmbeddedFields]
 */
class EmbeddedFieldsContext(
    val parentType: CompiledNamedType,
)

/**
 * An [EmbeddedFieldsProvider] that returns the fields specified by the `@typePolicy(embeddedFields: "...")` directive.
 */
object DefaultEmbeddedFieldsProvider : EmbeddedFieldsProvider {
  override fun getEmbeddedFields(context: EmbeddedFieldsContext): List<String> {
    return context.parentType.embeddedFields
  }
}

private val CompiledNamedType.embeddedFields: List<String>
  get() = when (this) {
    is ObjectType -> embeddedFields
    is InterfaceType -> embeddedFields
    else -> emptyList()
  }

/**
 * A [Relay connection types](https://relay.dev/graphql/connections.htm#sec-Connection-Types) aware [EmbeddedFieldsProvider].
 */
class ConnectionEmbeddedFieldsProvider(
    /**
     * Fields that are a Connection, associated with their parent type.
     */
    connectionFields: Map<String, List<String>>,

    /**
     * The connection type names.
     */
    connectionTypes: Set<String>,
) : EmbeddedFieldsProvider {
  companion object {
    private val connectionFieldsToEmbed = listOf("pageInfo", "edges")
  }

  private val embeddedFields = connectionFields + connectionTypes.associateWith { connectionFieldsToEmbed }

  override fun getEmbeddedFields(context: EmbeddedFieldsContext): List<String> {
    return embeddedFields[context.parentType.name].orEmpty()
  }
}
