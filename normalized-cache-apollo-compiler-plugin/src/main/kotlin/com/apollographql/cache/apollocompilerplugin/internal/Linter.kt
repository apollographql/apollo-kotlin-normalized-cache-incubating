@file:OptIn(ApolloExperimental::class)

package com.apollographql.cache.apollocompilerplugin.internal

import com.apollographql.apollo.annotations.ApolloExperimental
import com.apollographql.apollo.ast.GQLInterfaceTypeDefinition
import com.apollographql.apollo.ast.GQLObjectTypeDefinition
import com.apollographql.apollo.ast.GQLTypeDefinition
import com.apollographql.apollo.ast.GQLUnionTypeDefinition
import com.apollographql.apollo.ast.Schema
import com.apollographql.apollo.compiler.ApolloCompilerPluginLogger
import com.apollographql.apollo.compiler.ir.IrField
import com.apollographql.apollo.compiler.ir.IrFragment
import com.apollographql.apollo.compiler.ir.IrFragmentDefinition
import com.apollographql.apollo.compiler.ir.IrListTypeRef
import com.apollographql.apollo.compiler.ir.IrNamedTypeRef
import com.apollographql.apollo.compiler.ir.IrNonNullTypeRef
import com.apollographql.apollo.compiler.ir.IrOperation
import com.apollographql.apollo.compiler.ir.IrOperations
import com.apollographql.apollo.compiler.ir.IrSelection
import com.apollographql.apollo.compiler.ir.IrSelectionSet
import com.apollographql.apollo.compiler.ir.IrTypeRef
import com.apollographql.cache.apollocompilerplugin.CheckLevel

class Linter(
    private val logger: ApolloCompilerPluginLogger,
    private val schema: Schema,
    private val irOperations: IrOperations,
    private val checkMissingTypePolicy: CheckLevel,
    private val suppressMissingTypePolicyForTypesMatching: Regex?,
) {
  fun checkMissingTypePolicy() {
    val fragmentsByName = irOperations.fragments.associateBy { it.name }
    val usedTypes = irOperations.operations.flatMap { it.usedTypes(fragmentsByName) }.toSet()
    usedTypes
        .let {
          if (suppressMissingTypePolicyForTypesMatching == null) {
            it
          } else {
            it.filterNot { typeName -> typeName.matches(suppressMissingTypePolicyForTypesMatching) }
          }
        }
        .map {
          schema.typeDefinitions[it]!!
        }
        .filter { it.isComposite() }
        .forEach { typeDefinition ->
          if (typeDefinition.directives.none { it.name == "typePolicy" }) {
            val message = "Type ${typeDefinition.name} is missing @typePolicy directive"
            when (checkMissingTypePolicy) {
              CheckLevel.ERROR -> throw IllegalStateException(message)
              CheckLevel.WARNING -> logger.warn(message)
              CheckLevel.DISABLED -> {}
            }
          }
        }
  }

  private fun IrOperation.usedTypes(fragments: Map<String, IrFragmentDefinition>): Set<String> {
    val selectionSetsByName = selectionSets.associateBy { it.name }
    return selectionSets.flatMap { it.selections }.flatMap { it.usedTypes(selectionSetsByName, fragments) }.toSet()
  }

  private fun IrSelection.usedTypes(selectionSets: Map<String, IrSelectionSet>, fragments: Map<String, IrFragmentDefinition>): Set<String> {
    return when (this) {
      is IrField -> setOf(type.rawType().name)
      is IrFragment -> if (selectionSetName != null) {
        // Inline fragment
        selectionSets[selectionSetName]!!.selections.flatMap { it.usedTypes(selectionSets, fragments) }.toSet()
      } else {
        // Fragment spread
        fragments[name!!]!!.selectionSets.flatMap { it.selections }.flatMap { it.usedTypes(selectionSets, fragments) }.toSet()
      }
    }
  }

  private fun IrTypeRef.rawType(): IrNamedTypeRef {
    return when (this) {
      is IrNonNullTypeRef -> ofType.rawType()
      is IrListTypeRef -> ofType.rawType()
      is IrNamedTypeRef -> this
    }
  }

  private fun GQLTypeDefinition.isComposite(): Boolean {
    return when (this) {
      is GQLObjectTypeDefinition,
      is GQLUnionTypeDefinition,
      is GQLInterfaceTypeDefinition,
        -> true

      else -> false
    }
  }

}
