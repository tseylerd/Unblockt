// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package tse.unblockt.ls.server.analysys.completion.provider.ij

import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.KaScopeKind
import org.jetbrains.kotlin.analysis.api.signatures.KaCallableSignature
import org.jetbrains.kotlin.analysis.api.signatures.KaFunctionSignature
import org.jetbrains.kotlin.analysis.api.signatures.KaVariableSignature
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbolLocation
import org.jetbrains.kotlin.analysis.api.symbols.markers.KaNamedSymbol
import org.jetbrains.kotlin.analysis.api.symbols.typeParameters
import org.jetbrains.kotlin.analysis.api.types.KaFunctionType
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import tse.unblockt.ls.server.analysys.completion.imports.AutoImportAction

internal class ShadowedCallablesFilter {
    data class FilterResult(val excludeFromCompletion: Boolean, val updatedInsertionOptions: CallableInsertionOptions)

    private val processedSignatures: MutableSet<KaCallableSignature<*>> = HashSet()
    private val processedSimplifiedSignatures: MutableMap<SimplifiedSignature, CompletionSymbolOrigin> = HashMap()

    context(KaSession)
    fun excludeFromCompletion(
        callable: KaCallableSignature<*>,
        options: CallableInsertionOptions,
        symbolOrigin: CompletionSymbolOrigin,
        isAlreadyImported: Boolean,
        typeArgumentsAreRequired: Boolean,
    ): FilterResult {
        if (!processedSignatures.add(callable)) return FilterResult(excludeFromCompletion = true, options)

        if ((isAlreadyImported || symbolOrigin is CompletionSymbolOrigin.Scope)
            && options.action != AutoImportAction.DoNothing
        ) {
            val updatedOptions = options.withImportingStrategy(AutoImportAction.DoNothing)
            val excludeFromCompletion = processSignatureConsideringOptions(callable, updatedOptions, symbolOrigin, typeArgumentsAreRequired)
            if (!excludeFromCompletion) {
                return FilterResult(false, updatedOptions)
            }
        }

        return FilterResult(processSignatureConsideringOptions(callable, options, symbolOrigin, typeArgumentsAreRequired), options)
    }

    context(KaSession)
    private fun processSignatureConsideringOptions(
        callable: KaCallableSignature<*>,
        insertionOptions: CallableInsertionOptions,
        symbolOrigin: CompletionSymbolOrigin,
        typeArgumentsAreRequired: Boolean,
    ): Boolean {
        val (importingStrategy, insertionStrategy) = insertionOptions

        val isVariableCall = callable is KaVariableSignature<*> && insertionStrategy is CallableInsertionStrategy.AsCall

        return when (importingStrategy) {
            is AutoImportAction.DoNothing ->
                processSignature(callable, symbolOrigin, considerContainer = false, isVariableCall, typeArgumentsAreRequired)

            is AutoImportAction.AddImport -> {
                val simplifiedSignature = SimplifiedSignature.create(
                    callable,
                    considerContainer = false,
                    isVariableCall,
                    typeArgumentsAreRequired
                ) ?: return false

                when (val shadowingCallableOrigin = processedSimplifiedSignatures[simplifiedSignature]) {
                    null -> {
                        val considerContainer = symbolOrigin is CompletionSymbolOrigin.Index
                        processSignature(callable, symbolOrigin, considerContainer, isVariableCall, typeArgumentsAreRequired)
                    }

                    else -> {
                        if (symbolOrigin !is CompletionSymbolOrigin.Index) return true

                        when ((shadowingCallableOrigin as? CompletionSymbolOrigin.Scope)?.kind) {
                            is KaScopeKind.PackageMemberScope,
                            is KaScopeKind.DefaultSimpleImportingScope,
                            is KaScopeKind.ExplicitStarImportingScope,
                            is KaScopeKind.DefaultStarImportingScope -> {
                                processSignature(callable, symbolOrigin, considerContainer = true, isVariableCall, typeArgumentsAreRequired)
                            }

                            else -> true
                        }
                    }
                }
            }

            is AutoImportAction.UseFullNameAndShorten ->
                processSignature(callable, symbolOrigin, considerContainer = true, isVariableCall, typeArgumentsAreRequired)
        }
    }

    context(KaSession)
    private fun processSignature(
        callable: KaCallableSignature<*>,
        symbolOrigin: CompletionSymbolOrigin,
        considerContainer: Boolean,
        isVariableCall: Boolean,
        typeArgumentsAreRequired: Boolean,
    ): Boolean {
        val simplifiedSignature = SimplifiedSignature.create(
            callable,
            considerContainer,
            isVariableCall,
            typeArgumentsAreRequired
        ) ?: return false
        if (simplifiedSignature in processedSimplifiedSignatures) return true

        processedSimplifiedSignatures[simplifiedSignature] = symbolOrigin
        return false
    }
}

private sealed class SimplifiedSignature {
    abstract val name: Name

    abstract val containerFqName: FqName?

    companion object {
        context(KaSession)
        fun create(
            callableSignature: KaCallableSignature<*>,
            considerContainer: Boolean,
            isVariableCall: Boolean,
            typeArgumentsAreRequired: Boolean
        ): SimplifiedSignature? {
            val symbol = callableSignature.symbol
            if (symbol !is KaNamedSymbol) return null

            val containerFqName = if (considerContainer) symbol.getContainerFqName() else null

            @OptIn(KaExperimentalApi::class)
            return when (callableSignature) {
                is KaVariableSignature<*> -> createSimplifiedSignature(callableSignature, isVariableCall, containerFqName)
                is KaFunctionSignature<*> -> FunctionLikeSimplifiedSignature(
                    symbol.name,
                    containerFqName,
                    requiredTypeArgumentsCount = if (typeArgumentsAreRequired) callableSignature.symbol.typeParameters.size else 0,
                    lazy(LazyThreadSafetyMode.NONE) { callableSignature.valueParameters.map { it.returnType } },
                    callableSignature.valueParameters.mapIndexedNotNull { index, parameter -> index.takeIf { parameter.symbol.isVararg } },
                    this@KaSession,
                )
            }
        }

        context(KaSession)
        private fun createSimplifiedSignature(
            signature: KaVariableSignature<*>,
            isFunctionalVariableCall: Boolean,
            containerFqName: FqName?,
        ): SimplifiedSignature = when {
            isFunctionalVariableCall -> {
                FunctionLikeSimplifiedSignature(
                    signature.name,
                    containerFqName,
                    requiredTypeArgumentsCount = 0,
                    lazy(LazyThreadSafetyMode.NONE) {
                        val functionalType = signature.returnType as? KaFunctionType ?: error("Unexpected ${signature.returnType::class}")
                        functionalType.parameterTypes
                    },
                    varargValueParameterIndices = emptyList(),
                    this@KaSession,
                )
            }

            else -> VariableLikeSimplifiedSignature(signature.name, containerFqName)
        }

        context(KaSession)
        private fun KaCallableSymbol.getContainerFqName(): FqName? {
            val callableId = callableId ?: return null
            return when (location) {
                KaSymbolLocation.TOP_LEVEL -> callableId.packageName.takeIf { !it.isRoot }
                KaSymbolLocation.CLASS -> {
                    val classId = callableId.classId ?: return null
                    val classKind = findClass(classId)?.classKind

                    classId.asSingleFqName().takeIf { classKind?.isObject == true }
                }

                else -> null
            }
        }
    }
}


private data class VariableLikeSimplifiedSignature(
    override val name: Name,
    override val containerFqName: FqName?,
) : SimplifiedSignature()

private class FunctionLikeSimplifiedSignature(
    override val name: Name,
    override val containerFqName: FqName?,
    private val requiredTypeArgumentsCount: Int,
    private val valueParameterTypes: Lazy<List<KaType>>,
    private val varargValueParameterIndices: List<Int>,
    private val analysisSession: KaSession,
) : SimplifiedSignature() {
    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + containerFqName.hashCode()
        result = 31 * result + requiredTypeArgumentsCount.hashCode()
        result = 31 * result + varargValueParameterIndices.hashCode()
        return result
    }

    override fun equals(other: Any?): Boolean = this === other ||
            other is FunctionLikeSimplifiedSignature &&
            other.name == name &&
            other.containerFqName == containerFqName &&
            other.requiredTypeArgumentsCount == requiredTypeArgumentsCount &&
            other.varargValueParameterIndices == varargValueParameterIndices &&
            areValueParameterTypesEqualTo(other)

    /**
     * We need to use semantic type equality instead of the default structural equality of [KaType] to check if two signatures overlap.
     */
    private fun areValueParameterTypesEqualTo(other: FunctionLikeSimplifiedSignature): Boolean {
        val types1 = other.valueParameterTypes.value
        val types2 = valueParameterTypes.value
        if (types1.size != types2.size) return false

        with(analysisSession) {
            for (i in types1.indices) {
                if (!types1[i].semanticallyEquals(types2[i])) return false
            }
            return true
        }
    }
}