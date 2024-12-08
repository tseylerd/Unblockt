// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

@file:OptIn(KaIdeApi::class, KaExperimentalApi::class)
@file:Suppress("UnstableApiUsage")

package tse.unblockt.ls.server.analysys.completion.provider.impl.ij

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiPrimitiveType
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.parentOfType
import com.intellij.psi.util.parents
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaIdeApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.*
import org.jetbrains.kotlin.analysis.api.lifetime.KaLifetimeOwner
import org.jetbrains.kotlin.analysis.api.lifetime.KaLifetimeToken
import org.jetbrains.kotlin.analysis.api.lifetime.withValidityAssertion
import org.jetbrains.kotlin.analysis.api.scopes.KaScope
import org.jetbrains.kotlin.analysis.api.signatures.KaCallableSignature
import org.jetbrains.kotlin.analysis.api.signatures.KaFunctionSignature
import org.jetbrains.kotlin.analysis.api.signatures.KaVariableSignature
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.types.*
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.idea.references.KtReference
import org.jetbrains.kotlin.load.java.JvmAbi
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.ArrayFqNames
import org.jetbrains.kotlin.resolve.ImportPath
import org.jetbrains.kotlin.serialization.deserialization.METADATA_FILE_EXTENSION
import tse.unblockt.ls.protocol.CompletionItem
import tse.unblockt.ls.server.analysys.completion.LsCompletionRequest
import tse.unblockt.ls.server.analysys.completion.imports.AutoImportAction
import tse.unblockt.ls.server.analysys.completion.provider.CompletionItemsProvider
import tse.unblockt.ls.server.analysys.completion.provider.ij.CallableInsertionOptions
import tse.unblockt.ls.server.analysys.completion.provider.ij.CallableInsertionStrategy
import tse.unblockt.ls.server.analysys.completion.provider.ij.CompletionSymbolOrigin
import tse.unblockt.ls.server.analysys.completion.provider.ij.ShadowedCallablesFilter
import tse.unblockt.ls.server.analysys.completion.util.CallableCompletionItem
import tse.unblockt.ls.server.analysys.completion.util.ij.CompletionVisibilityChecker
import tse.unblockt.ls.server.analysys.completion.util.ij.KotlinNameReferencePositionContext
import tse.unblockt.ls.server.analysys.completion.util.ij.KotlinSimpleNameReferencePositionContext
import tse.unblockt.ls.server.analysys.index.LsKotlinPsiIndex
import tse.unblockt.ls.server.analysys.index.LsKotlinSymbolIndex
import tse.unblockt.ls.server.analysys.psi.asReference
import tse.unblockt.ls.server.analysys.psi.languageVersionSettings
import tse.unblockt.ls.server.threading.Cancellable

internal open class LsCallableCompletionProvider(project: Project) : CompletionItemsProvider {
    private val psiIndex = LsKotlinPsiIndex.instance(project)

    context(KaSession)
    private fun getInsertionStrategy(signature: KaCallableSignature<*>): CallableInsertionStrategy {
        return when (signature) {
            is KaFunctionSignature<*> -> CallableInsertionStrategy.AsCall
            else -> CallableInsertionStrategy.AsIdentifier
        }
    }

    context(KaSession)
    private fun getInsertionStrategyForExtensionFunction(
        signature: KaCallableSignature<*>,
        applicabilityResult: KaExtensionApplicabilityResult?
    ): CallableInsertionStrategy? = when (applicabilityResult) {
        is KaExtensionApplicabilityResult.ApplicableAsExtensionCallable -> getInsertionStrategy(signature)
        is KaExtensionApplicabilityResult.ApplicableAsFunctionalVariableCall -> CallableInsertionStrategy.AsCall
        else -> null
    }

    context(KaSession)
    private fun getOptions(
        request: LsCompletionRequest,
        signature: KaCallableSignature<*>,
        isImportDefinitelyNotRequired: Boolean = false
    ): CallableInsertionOptions {
        val action = if (isImportDefinitelyNotRequired) {
            AutoImportAction.DoNothing
        } else {
            AutoImportAction.ofCallable(request, signature.symbol)
        }
        return CallableInsertionOptions(
            action,
            getInsertionStrategy(signature)
        )
    }

    context(KaSession)
    private fun getExtensionOptions(
        request: LsCompletionRequest,
        signature: KaCallableSignature<*>,
        applicability: KaExtensionApplicabilityResult?
    ): CallableInsertionOptions? {
        val insertionStrategy = getInsertionStrategyForExtensionFunction(signature, applicability) ?: return null
        val isFunctionalVariableCall = applicability is KaExtensionApplicabilityResult.ApplicableAsFunctionalVariableCall
        val importStrategy = AutoImportAction.ofCallable(request, signature.symbol, isFunctionalVariableCall)
        return CallableInsertionOptions(importStrategy, insertionStrategy)
    }

    context(KaSession, Cancellable)
    override fun provide(request: LsCompletionRequest): Sequence<CompletionItem> {
        val context = request.positionContext as? KotlinNameReferencePositionContext ?: return emptySequence()
        return sequence {
            val result = with(context) {
                val visibilityChecker = CompletionVisibilityChecker(request)
                val scopesContext = request.originalFile.scopeContext(nameExpression)

                val extensionChecker = if (context is KotlinSimpleNameReferencePositionContext) {
                    createExtensionCandidateChecker(
                        request.originalFile,
                        context.nameExpression,
                        context.explicitReceiver
                    )
                } else null

                val receiver = explicitReceiver

                val callablesWithMetadata: Sequence<CallableWithMetadataForCompletion> = when {
                    receiver != null -> collectDotCompletion(
                        request,
                        scopesContext,
                        receiver,
                        extensionChecker,
                        visibilityChecker
                    )

                    else -> completeWithoutReceiver(request, scopesContext, extensionChecker, visibilityChecker)
                }
                    .filterIfInsideAnnotationEntryArgument(context.position, context.nameExpression.expectedType)
                    .filterOutShadowedCallables(request, context.nameExpression.expectedType)
                    .filterNot(isUninitializedCallable(request, context.position))

                callablesWithMetadata.map {
                    CallableCompletionItem(request, it.signature, it.options)
                }.distinct()
            }
            for (completionItem in result) {
                cancellationPoint()
                yield(completionItem)
            }
        }
    }

    context(KaSession)
    private fun completeWithoutReceiver(
        request: LsCompletionRequest,
        scopeContext: KaScopeContext,
        extensionChecker: KaCompletionExtensionCandidateChecker?,
        visibilityChecker: CompletionVisibilityChecker,
    ): Sequence<CallableWithMetadataForCompletion> = sequence {
        val implicitReceivers = scopeContext.implicitReceivers
        val implicitReceiversTypes = implicitReceivers.map { it.type }

        val availableLocalAndMemberNonExtensions = collectLocalAndMemberNonExtensionsFromScopeContext(
            scopeContext,
            visibilityChecker,
            request.scopeNameFilter,
            request,
        ) { !it.isExpect }
        val extensionsWhichCanBeCalled = collectSuitableExtensions(scopeContext, extensionChecker, visibilityChecker, request)
        val availableStaticAndTopLevelNonExtensions = collectStaticAndTopLevelNonExtensionsFromScopeContext(
            scopeContext,
            visibilityChecker,
            request.scopeNameFilter,
            request,
        ) { !it.isExpect }

        availableLocalAndMemberNonExtensions.forEach { yield(createCallableWithMetadata(request, it.signature, it.scopeKind)) }

        extensionsWhichCanBeCalled.forEach { (signatureWithScopeKind, insertionOptions) ->
            val signature = signatureWithScopeKind.signature
            yield(createCallableWithMetadata(request, signature, signatureWithScopeKind.scopeKind, options = insertionOptions))
        }
        availableStaticAndTopLevelNonExtensions.forEach { yield(createCallableWithMetadata(request, it.signature, it.scopeKind)) }

        if (!request.matcher.isEmpty) {
            val topLevelCallablesFromIndex = getTopLevelCallableSymbolsByNameFilter(analysisScope, request.scopeNameFilter) {
                !visibilityChecker.isDefinitelyInvisibleByPsi(it) && it.canBeAnalysed()
            }

            topLevelCallablesFromIndex
                .filter { !it.isExpect }
                .filter { visibilityChecker.isVisible(it) }
                .forEach { yield(createCallableWithMetadata(request, it.asSignature(), CompletionSymbolOrigin.Index)) }
        }

        collectExtensionsFromIndexAndResolveExtensionScope(
            request,
            implicitReceiversTypes,
            extensionChecker,
            visibilityChecker,
        ).forEach { applicableExtension ->
            val signature = applicableExtension.signature
            yield(createCallableWithMetadata(request, signature, CompletionSymbolOrigin.Index, applicableExtension.insertionOptions))
        }
    }

    context(KaSession)
    protected open fun collectDotCompletion(
        request: LsCompletionRequest,
        scopeContext: KaScopeContext,
        explicitReceiver: KtElement,
        extensionChecker: KaCompletionExtensionCandidateChecker?,
        visibilityChecker: CompletionVisibilityChecker,
    ): Sequence<CallableWithMetadataForCompletion> {
        explicitReceiver as KtExpression

        val symbol = explicitReceiver.asReference?.resolveToExpandedSymbol()
        return when {
            symbol is KaPackageSymbol -> collectDotCompletionForPackageReceiver(request, symbol, visibilityChecker)

            else -> sequence {
                if (symbol is KaNamedClassSymbol && symbol.hasImportantStaticMemberScope) {
                    yieldAll(collectDotCompletionFromStaticScope(request, symbol, visibilityChecker))
                }

                if (symbol !is KaNamedClassSymbol || symbol.canBeUsedAsReceiver) {
                    yieldAll(
                        collectDotCompletionForCallableReceiver(
                            request,
                            scopeContext,
                            explicitReceiver,
                            extensionChecker,
                            visibilityChecker,
                        )
                    )
                }
            }
        }
    }

    private val KaNamedClassSymbol.hasImportantStaticMemberScope: Boolean
        get() = classKind == KaClassKind.ENUM_CLASS ||
                origin.isJavaSourceOrLibrary()

    private val KaNamedClassSymbol.canBeUsedAsReceiver: Boolean
        get() = classKind.isObject || companionObject != null

    context(KaSession)
    private fun collectDotCompletionForPackageReceiver(
        request: LsCompletionRequest,
        packageSymbol: KaPackageSymbol,
        visibilityChecker: CompletionVisibilityChecker
    ): Sequence<CallableWithMetadataForCompletion> {
        val packageScope = packageSymbol.packageScope
        val packageScopeKind = KaScopeKinds.PackageMemberScope(CompletionSymbolOrigin.SCOPE_OUTSIDE_TOWER_INDEX)

        return packageScope
            .callables(request.scopeNameFilter)
            .filterNot { it.isExtension }
            .filter { visibilityChecker.isVisible(it) }
            .map { callable ->
                val callableSignature = callable.asSignature()
                val options = CallableInsertionOptions(AutoImportAction.DoNothing, getInsertionStrategy(callableSignature))
                createCallableWithMetadata(request, callableSignature, packageScopeKind, options = options)
            }
    }

    context(KaSession)
    private fun collectDotCompletionForCallableReceiver(
        request: LsCompletionRequest,
        scopeContext: KaScopeContext,
        explicitReceiver: KtExpression,
        extensionChecker: KaCompletionExtensionCandidateChecker?,
        visibilityChecker: CompletionVisibilityChecker
    ): Sequence<CallableWithMetadataForCompletion> = sequence {
        val receiverType = explicitReceiver.expressionType.takeUnless { it is KaErrorType } ?: return@sequence
        val callablesWithMetadata = collectDotCompletionForCallableReceiver(
            request,
            listOf(receiverType),
            visibilityChecker,
            scopeContext,
            extensionChecker,
        )
        yieldAll(callablesWithMetadata)

        val smartCastInfo = explicitReceiver.smartCastInfo
        if (smartCastInfo?.isStable == false) {
            val callablesWithMetadataFromUnstableSmartCast = collectDotCompletionForCallableReceiver(
                request,
                listOf(smartCastInfo.smartCastType),
                visibilityChecker,
                scopeContext,
                extensionChecker,
                smartCastInfo.smartCastType.takeIf { it.approximateToSuperPublicDenotable(true) == null }
            )
            yieldAll(callablesWithMetadataFromUnstableSmartCast)
        }
    }

    context(KaSession)
    private fun collectDotCompletionForCallableReceiver(
        request: LsCompletionRequest,
        typesOfPossibleReceiver: List<KaType>,
        visibilityChecker: CompletionVisibilityChecker,
        scopeContext: KaScopeContext,
        extensionChecker: KaCompletionExtensionCandidateChecker?,
        explicitReceiverTypeHint: KaType? = null,
    ): Sequence<CallableWithMetadataForCompletion> = sequence {
        val nonExtensionMembers = typesOfPossibleReceiver.flatMap { typeOfPossibleReceiver ->
            collectNonExtensionsForType(
                typeOfPossibleReceiver,
                visibilityChecker,
                request.scopeNameFilter,
                request,
            ) { !it.isExpect }
        }
        val extensionNonMembers = collectSuitableExtensions(
            scopeContext,
            extensionChecker,
            visibilityChecker,
            request,
            typesOfPossibleReceiver,
        )

        nonExtensionMembers.forEach { signatureWithScopeKind ->
            val callableWithMetadata = createCallableWithMetadata(
                request,
                signatureWithScopeKind.signature,
                signatureWithScopeKind.scopeKind,
                isImportDefinitelyNotRequired = true,
                explicitReceiverTypeHint = explicitReceiverTypeHint
            )
            yield(callableWithMetadata)
        }

        extensionNonMembers.forEach { (signatureWithScopeKind, insertionOptions) ->
            val signature = signatureWithScopeKind.signature
            val scopeKind = signatureWithScopeKind.scopeKind
            yield(
                createCallableWithMetadata(
                    request,
                    signature,
                    scopeKind,
                    isImportDefinitelyNotRequired = false,
                    insertionOptions,
                    explicitReceiverTypeHint
                )
            )
        }

        val extensions = collectExtensionsFromIndexAndResolveExtensionScope(
            request,
            typesOfPossibleReceiver,
            extensionChecker,
            visibilityChecker,
        )
        extensions
            .filter { !it.signature.symbol.isExpect }
            .forEach { applicableExtension ->
                val callableWithMetadata = createCallableWithMetadata(
                    request,
                    applicableExtension.signature,
                    CompletionSymbolOrigin.Index,
                    applicableExtension.insertionOptions,
                    explicitReceiverTypeHint = explicitReceiverTypeHint
                )
                yield(callableWithMetadata)
            }
    }

    context(KaSession)
    private fun collectDotCompletionFromStaticScope(
        request: LsCompletionRequest,
        symbol: KaNamedClassSymbol,
        visibilityChecker: CompletionVisibilityChecker
    ): Sequence<CallableWithMetadataForCompletion> {
        val staticScope = symbol.staticScope(false)
        val staticScopeKind = KaScopeKinds.StaticMemberScope(CompletionSymbolOrigin.SCOPE_OUTSIDE_TOWER_INDEX)

        val nonExtensions = collectNonExtensionsFromScope(
            request,
            staticScope,
            visibilityChecker,
            request.scopeNameFilter,
        ) { !it.isExpect }

        return nonExtensions.map { member ->
            val options = CallableInsertionOptions(AutoImportAction.DoNothing, getInsertionStrategy(member))
            createCallableWithMetadata(request, member, staticScopeKind, options = options)
        }
    }

    context(KaSession)
    private fun collectExtensionsFromIndexAndResolveExtensionScope(
        request: LsCompletionRequest,
        receiverTypes: List<KaType>,
        extensionChecker: KaCompletionExtensionCandidateChecker?,
        visibilityChecker: CompletionVisibilityChecker,
    ): Collection<ApplicableExtension> {
        if (receiverTypes.isEmpty()) return emptyList()
        val nonNullableReceiverTypes = receiverTypes.map { it.withNullability(KaTypeNullability.NON_NULLABLE) }

        val extensionsFromIndex = LsKotlinSymbolIndex.instance(request.project).getCallableExtensions(
            request.scopeNameFilter,
            receiverTypes,
        ) { !visibilityChecker.isDefinitelyInvisibleByPsi(it) && it.canBeAnalysed() } + resolveExtensionScopeWithTopLevelDeclarations.callables(request.scopeNameFilter).filter { symbol ->
            if (!symbol.isExtension) return@filter false
            val symbolReceiverType = symbol.receiverType ?: return@filter false

            nonNullableReceiverTypes.any { it isPossiblySubTypeOf symbolReceiverType }
        }


        return extensionsFromIndex
            .filter { !it.isExpect }
            .filter { visibilityChecker.isVisible(it) }
            .mapNotNull { checkApplicabilityAndSubstitute(request, it, extensionChecker) }
            .let { sortExtensions(it.toList(), receiverTypes) }
    }

    context(KaSession)
    private fun sortExtensions(
        extensions: Collection<ApplicableExtension>,
        receiversFromContext: List<KaType>
    ): Collection<ApplicableExtension> {
        if (extensions.isEmpty()) return emptyList()

        val indexOfReceiverFromContext = mutableMapOf<ReceiverId, Int>()
        val indexInClassHierarchy = mutableMapOf<ReceiverId, Int>()

        for ((receiverFromContextIndex, receiverFromContextType) in receiversFromContext.withIndex()) {
            val selfWithSuperTypes = listOf(receiverFromContextType) + receiverFromContextType.allSupertypes
            for ((superTypeIndex, superType) in selfWithSuperTypes.withIndex()) {
                val receiverId = ReceiverId.create(superType) ?: continue

                indexOfReceiverFromContext.putIfAbsent(receiverId, receiverFromContextIndex)
                indexInClassHierarchy.putIfAbsent(receiverId, superTypeIndex)
            }
        }

        return extensions
            .map { applicableExtension ->
                val signature = applicableExtension.signature
                val insertionStrategy = applicableExtension.insertionOptions.insertionStrategy
                val receiverType = when {
                    signature is KaVariableSignature<*> && insertionStrategy is CallableInsertionStrategy.AsCall ->
                        (signature.returnType as? KaFunctionType)?.receiverType

                    else -> signature.receiverType
                }
                val receiverId = receiverType?.let { ReceiverId.create(it) }
                applicableExtension to receiverId
            }
            .sortedWith(compareBy(
                { (_, receiverId) -> indexOfReceiverFromContext[receiverId] ?: Int.MAX_VALUE },
                { (_, receiverId) -> indexInClassHierarchy[receiverId] ?: Int.MAX_VALUE },
                { (applicableExtension, _) -> applicableExtension.signature is KaVariableSignature<*> }
            ))
            .map { (applicableExtension, _) -> applicableExtension }
    }

    context(KaSession)
    private fun collectSuitableExtensions(
        scopeContext: KaScopeContext,
        extensionChecker: KaCompletionExtensionCandidateChecker?,
        visibilityChecker: CompletionVisibilityChecker,
        request: LsCompletionRequest,
        explicitReceiverTypes: List<KaType>? = null,
    ): Sequence<Pair<KtCallableSignatureWithContainingScopeKind, CallableInsertionOptions>> {
        val receiversTypes = explicitReceiverTypes ?: scopeContext.implicitReceivers.map { it.type }

        return scopeContext.scopes.asSequence().flatMap { scopeWithKind ->
            collectSuitableExtensions(scopeWithKind.scope, receiversTypes, extensionChecker, visibilityChecker, request)
                .map { KtCallableSignatureWithContainingScopeKind(it.signature, scopeWithKind.kind) to it.insertionOptions }
        }
    }

    private sealed class ReceiverId {
        private data class ClassIdForNonLocal(val classId: ClassId) : ReceiverId()
        private data class NameForLocal(val name: Name) : ReceiverId()

        companion object {
            context(KaSession)
            fun create(type: KaType): ReceiverId? {
                val expandedClassSymbol = type.expandedSymbol ?: return null
                val name = expandedClassSymbol.name ?: return null

                return when (val classId = expandedClassSymbol.classId) {
                    null -> NameForLocal(name)
                    else -> ClassIdForNonLocal(classId)
                }
            }
        }
    }

    context(KaSession)
    private fun collectSuitableExtensions(
        scope: KaScope,
        receiverTypes: List<KaType>,
        hasSuitableExtensionReceiver: KaCompletionExtensionCandidateChecker?,
        visibilityChecker: CompletionVisibilityChecker,
        request: LsCompletionRequest,
    ): Collection<ApplicableExtension> =
        scope.callables(request.scopeNameFilter)
            .filter { it.canBeUsedAsExtension() }
            .filter { visibilityChecker.isVisible(it) }
            .filter { !it.isExpect }
            .mapNotNull { callable -> checkApplicabilityAndSubstitute(request, callable, hasSuitableExtensionReceiver) }
            .let { sortExtensions(it.toList(), receiverTypes) }

    context(KaSession)
    private fun KaCallableSymbol.canBeUsedAsExtension(): Boolean =
        isExtension || this is KaVariableSymbol && (returnType as? KaFunctionType)?.hasReceiver == true

    context(KaSession)
    private fun checkApplicabilityAndSubstitute(
        request: LsCompletionRequest,
        callableSymbol: KaCallableSymbol,
        extensionChecker: KaCompletionExtensionCandidateChecker?
    ): ApplicableExtension? {
        val (signature, applicabilityResult) = if (extensionChecker != null) {
            val result = extensionChecker.computeApplicability(callableSymbol) as? KaExtensionApplicabilityResult.Applicable ?: return null
            val signature = callableSymbol.substitute(result.substitutor)

            signature to result
        } else {
            callableSymbol.asSignature() to null
        }

        val insertionOptions = getExtensionOptions(request, signature, applicabilityResult) ?: return null
        return ApplicableExtension(signature, insertionOptions)
    }

    context(KaSession)
    private fun createCallableWithMetadata(
        request: LsCompletionRequest,
        signature: KaCallableSignature<*>,
        scopeKind: KaScopeKind,
        isImportDefinitelyNotRequired: Boolean = false,
        options: CallableInsertionOptions = getOptions(request, signature, isImportDefinitelyNotRequired),
        explicitReceiverTypeHint: KaType? = null,
    ): CallableWithMetadataForCompletion {
        val symbolOrigin = CompletionSymbolOrigin.Scope(scopeKind)
        return CallableWithMetadataForCompletion(signature, explicitReceiverTypeHint, options, symbolOrigin)
    }

    context(KaSession)
    private fun createCallableWithMetadata(
        request: LsCompletionRequest,
        signature: KaCallableSignature<*>,
        symbolOrigin: CompletionSymbolOrigin,
        options: CallableInsertionOptions = getOptions(request, signature),
        explicitReceiverTypeHint: KaType? = null,
    ) = CallableWithMetadataForCompletion(signature, explicitReceiverTypeHint, options, symbolOrigin)

    private fun isUninitializedCallable(
        request: LsCompletionRequest,
        position: PsiElement,
    ): (CallableWithMetadataForCompletion) -> Boolean {
        val uninitializedCallablesForPosition = buildSet<KtCallableDeclaration> {
            for (parent in position.parents(withSelf = false)) {
                when (val grandParent = parent.parent) {
                    is KtParameter -> {
                        if (grandParent.defaultValue == parent) {
                            val originalOrSelf = getOriginalDeclarationOrSelf(
                                declaration = grandParent,
                                originalKtFile = request.originalFile,
                            )
                            generateSequence(originalOrSelf) { PsiTreeUtil.getNextSiblingOfType(it, KtParameter::class.java) }
                                .forEach(::add)
                        }
                    }

                    is KtProperty -> {
                        if (grandParent.initializer == parent) {
                            val declaration = getOriginalDeclarationOrSelf(
                                declaration = grandParent,
                                originalKtFile = request.originalFile,
                            )
                            add(declaration)
                        }
                    }
                }

                if (parent is KtDeclaration) break
            }
        }

        return { callable: CallableWithMetadataForCompletion ->
            callable.signature.symbol.psi in uninitializedCallablesForPosition
        }
    }

    context(KaSession)
    private fun Sequence<CallableWithMetadataForCompletion>.filterOutShadowedCallables(
        request: LsCompletionRequest,
        expectedType: KaType?,
    ): Sequence<CallableWithMetadataForCompletion> =
        sequence {
            val shadowedCallablesFilter = ShadowedCallablesFilter()

            for (callableWithMetadata in this@filterOutShadowedCallables) {
                val callableFqName = callableWithMetadata.signature.callableId?.asSingleFqName()
                val isAlreadyImported = callableFqName?.isAlreadyImported(request.imports.defaultImports, request.imports.excludedImports) == true
                val typeArgumentsAreRequired = (callableWithMetadata.signature.symbol as? KaFunctionSymbol)?.let {
                    functionCanBeCalledWithoutExplicitTypeArguments(it, expectedType)
                } == false

                val (excludeFromCompletion, updatedOptions) = shadowedCallablesFilter.excludeFromCompletion(
                    callableWithMetadata.signature,
                    callableWithMetadata.options,
                    callableWithMetadata.symbolOrigin,
                    isAlreadyImported,
                    typeArgumentsAreRequired,
                )
                if (excludeFromCompletion) continue

                if (updatedOptions != callableWithMetadata.options) {
                    yield(callableWithMetadata.copy(options = updatedOptions))
                } else {
                    yield(callableWithMetadata)
                }
            }
        }

    context(KaSession)
    private fun Sequence<CallableWithMetadataForCompletion>.filterIfInsideAnnotationEntryArgument(
        position: PsiElement,
        expectedType: KaType?,
    ): Sequence<CallableWithMetadataForCompletion> {
        if (!position.isInsideAnnotationEntryArgumentList()) return this

        return filter { callableWithMetadata ->
            val symbol = callableWithMetadata.signature.symbol

            if (symbol.hasConstEvaluationAnnotation()) return@filter true

            when (symbol) {
                is KaJavaFieldSymbol -> symbol.isStatic && symbol.isVal && symbol.hasPrimitiveOrStringReturnType()
                is KaKotlinPropertySymbol -> symbol.isConst
                is KaEnumEntrySymbol -> true
                is KaNamedFunctionSymbol -> {
                    val isArrayOfCall = symbol.callableId?.asSingleFqName() in ArrayFqNames.ARRAY_CALL_FQ_NAMES

                    isArrayOfCall && expectedType?.let { symbol.returnType.isPossiblySubTypeOf(it) } != false
                }

                else -> false
            }
        }
    }

    context(KaSession)
    private fun KaJavaFieldSymbol.hasPrimitiveOrStringReturnType(): Boolean =
        (psi as? PsiField)?.type is PsiPrimitiveType || returnType.isStringType

    context(KaSession)
    private fun KaCallableSymbol.hasConstEvaluationAnnotation(): Boolean =
        annotations.any { it.classId == StandardClassIds.Annotations.IntrinsicConstEvaluation }

    context(KaSession)
    private fun KaNamedClassSymbol.staticScope(withCompanionScope: Boolean = true): KaScope = buildList {
        if (withCompanionScope) {

            val e = companionObject?.memberScope
            if (e != null) {
                add(e)
            }
        }
        add(staticMemberScope)
    }.asCompositeScope()

    context(KaSession)
    private fun KtReference.resolveToExpandedSymbol(): KaSymbol? = when (val symbol = resolveToSymbol()) {
        is KaTypeAliasSymbol -> symbol.expandedType.expandedSymbol
        else -> symbol
    }

    context(KaSession)
    private fun getTopLevelCallableSymbolsByNameFilter(scope: GlobalSearchScope,
                                               nameFilter: (Name) -> Boolean,
                                               psiFilter: (KtCallableDeclaration) -> Boolean = { true }): Sequence<KaCallableSymbol> {
        val values = psiIndex.getTopLevelCallableDeclarations(scope) { fqName ->
            nameFilter(getShortName(fqName))
        }.filter { psi ->
            if (psi is KtParameter && psi.isFunctionTypeParameter) {
                return@filter false
            }
            psiFilter(psi) && !psi.isKotlinBuiltins() && psi.receiverTypeReference == null
        }.mapNotNull { it.symbol as? KaCallableSymbol }
        return sequence {
            for (value in values) {
                yield(value)
            }
            yieldAll(resolveExtensionScopeWithTopLevelDeclarations.callables(nameFilter).filter { !it.isExtension })
        }
    }
}

private fun getShortName(fqName: String) = Name.identifier(fqName.substringAfterLast('.'))

context(KaSession)
infix fun KaType.isPossiblySubTypeOf(superType: KaType): Boolean {
    if (this is KaTypeParameterType) return this.hasCommonSubtypeWith(superType)

    if (superType is KaTypeParameterType) return superType.symbol.upperBounds.all { this isPossiblySubTypeOf it }

    val superTypeWithReplacedTypeArguments = superType.expandedSymbol?.let { symbol ->
        buildClassTypeWithStarProjections(symbol, superType.nullability)
    }
    return superTypeWithReplacedTypeArguments != null && isSubtypeOf(superTypeWithReplacedTypeArguments)
}

context(KaSession)
private fun buildClassTypeWithStarProjections(symbol: KaClassSymbol, nullability: KaTypeNullability): KaType =
    buildClassType(symbol) {
        repeat(symbol.typeParameters.size) {
            argument(buildStarTypeProjection())
        }
    }.withNullability(nullability)

context(KaSession)
internal fun collectNonExtensionsForType(
    type: KaType,
    visibilityChecker: CompletionVisibilityChecker,
    scopeNameFilter: (Name) -> Boolean,
    request: LsCompletionRequest,
    indexInTower: Int? = null,
    symbolFilter: (KaCallableSymbol) -> Boolean,
): Sequence<KtCallableSignatureWithContainingScopeKind> {
    val typeScope = type.scope ?: return emptySequence()

    val callables = typeScope.getCallableSignatures(scopeNameFilter.getAndSetAware())
        .applyIf(!request.allowSyntheticJavaProperties) { filter { it.symbol !is KaSyntheticJavaPropertySymbol } }
        .applyIf(request.allowSyntheticJavaProperties) {
            filterOutJavaGettersAndSetters(type, visibilityChecker, scopeNameFilter, symbolFilter)
        }

    val innerClasses = typeScope.getClassifierSymbols(scopeNameFilter).filterIsInstance<KaNamedClassSymbol>().filter { it.isInner }
    val innerClassesConstructors = innerClasses.flatMap { it.declaredMemberScope.constructors }.map { it.asSignature() }

    val nonExtensionsFromType = (callables + innerClassesConstructors).filterNonExtensions(visibilityChecker, symbolFilter)

    val scopeIndex = indexInTower ?: CompletionSymbolOrigin.SCOPE_OUTSIDE_TOWER_INDEX

    return nonExtensionsFromType
        .map { KtCallableSignatureWithContainingScopeKind(it, KaScopeKinds.TypeScope(scopeIndex)) }
        .applyIf(request.file.excludeEnumEntries) { filterNot { isEnumEntriesProperty(it.signature.symbol) } }
}

private fun <T: Any> T.applyIf(condition: Boolean, converter: T.() -> T): T {
    return if (condition) {
        converter()
    } else {
        this
    }
}

context(KaSession)
private fun collectLocalAndMemberNonExtensionsFromScopeContext(
    scopeContext: KaScopeContext,
    visibilityChecker: CompletionVisibilityChecker,
    scopeNameFilter: (Name) -> Boolean,
    request: LsCompletionRequest,
    symbolFilter: (KaCallableSymbol) -> Boolean,
): Sequence<KtCallableSignatureWithContainingScopeKind> = sequence {
    val indexedImplicitReceivers = scopeContext.implicitReceivers.associateBy { it.scopeIndexInTower }
    val scopes = scopeContext.scopes.filter { it.kind is KaScopeKind.LocalScope || it.kind is KaScopeKind.TypeScope }

    for (scopeWithKind in scopes) {
        val kind = scopeWithKind.kind
        val isImplicitReceiverScope = kind is KaScopeKind.TypeScope && kind.indexInTower in indexedImplicitReceivers

        val nonExtensions = if (isImplicitReceiverScope) {
            val implicitReceiver = indexedImplicitReceivers.getValue(kind.indexInTower)
            collectNonExtensionsForType(
                implicitReceiver.type,
                visibilityChecker,
                scopeNameFilter,
                request,
                implicitReceiver.scopeIndexInTower,
                symbolFilter,
            )
        } else {
            collectNonExtensionsFromScope(request, scopeWithKind.scope, visibilityChecker, scopeNameFilter, symbolFilter).map {
                KtCallableSignatureWithContainingScopeKind(it, kind)
            }
        }
        yieldAll(nonExtensions)
    }
}

context(KaSession)
internal fun collectStaticAndTopLevelNonExtensionsFromScopeContext(
    scopeContext: KaScopeContext,
    visibilityChecker: CompletionVisibilityChecker,
    scopeNameFilter: (Name) -> Boolean,
    request: LsCompletionRequest,
    symbolFilter: (KaCallableSymbol) -> Boolean,
): Sequence<KtCallableSignatureWithContainingScopeKind> = scopeContext.scopes.asSequence()
    .filterNot { it.kind is KaScopeKind.LocalScope || it.kind is KaScopeKind.TypeScope }
    .flatMap { scopeWithKind ->
        collectNonExtensionsFromScope(request, scopeWithKind.scope, visibilityChecker, scopeNameFilter, symbolFilter)
            .map { KtCallableSignatureWithContainingScopeKind(it, scopeWithKind.kind) }
    }

context(KaSession)
private fun collectNonExtensionsFromScope(
    request: LsCompletionRequest,
    scope: KaScope,
    visibilityChecker: CompletionVisibilityChecker,
    scopeNameFilter: (Name) -> Boolean,
    symbolFilter: (KaCallableSymbol) -> Boolean,
): Sequence<KaCallableSignature<*>> {
    val filterNonExtensions = scope.callables(scopeNameFilter.getAndSetAware())
        .map { it.asSignature() }
        .filterNonExtensions(visibilityChecker, symbolFilter)
    return if (request.file.excludeEnumEntries) {
        filterNonExtensions.filterNot { isEnumEntriesProperty(it.symbol) }
    } else {
        filterNonExtensions
    }
}

internal fun <T : KtDeclaration> getOriginalDeclarationOrSelf(declaration: T, originalKtFile: KtFile): T {
    if (declaration is KtParameter || KtPsiUtil.isLocal(declaration)) {
        return declaration
    }

    return getOriginalElementOfSelf(declaration, originalKtFile)
}

internal fun <T : KtElement> getOriginalElementOfSelf(element: T, originalKtFile: KtFile): T {
    return try {
        PsiTreeUtil.findSameElementInCopy(element, originalKtFile)
    } catch (_: IllegalStateException) {
        element
    }
}

context(KaSession)
internal fun Sequence<KaCallableSignature<*>>.filterOutJavaGettersAndSetters(
    type: KaType,
    visibilityChecker: CompletionVisibilityChecker,
    scopeNameFilter: (Name) -> Boolean,
    symbolFilter: (KaCallableSymbol) -> Boolean
): Sequence<KaCallableSignature<*>> {
    val syntheticJavaPropertiesTypeScope = type.syntheticJavaPropertiesScope ?: return this
    val syntheticProperties = syntheticJavaPropertiesTypeScope.getCallableSignatures(scopeNameFilter.getAndSetAware())
        .filterNonExtensions(visibilityChecker, symbolFilter)
        .filterIsInstance<KaCallableSignature<KaSyntheticJavaPropertySymbol>>()
    val javaGetterAndUnitSetterSymbols = syntheticProperties.flatMapTo(mutableSetOf()) { it.symbol.getterAndUnitSetter }

    return filter { it.symbol !in javaGetterAndUnitSetterSymbols }
}

internal fun ((Name) -> Boolean).getAndSetAware(): (Name) -> Boolean = { name ->
    listOfNotNull(name, name.toJavaGetterName(), name.toJavaSetterName()).any(this)
}

internal fun FqName.isAlreadyImported(defaultImports: Iterable<ImportPath>, excludedImports: Iterable<FqName>): Boolean {
    val importPath = ImportPath(this, isAllUnder = false)
    return importPath.isImported(defaultImports, excludedImports)
}

context(KaSession)
fun functionCanBeCalledWithoutExplicitTypeArguments(
    symbol: KaFunctionSymbol,
    expectedType: KaType?
): Boolean {
    if (symbol.typeParameters.isEmpty()) return true

    val typeParametersToInfer = symbol.typeParameters.toSet()
    val potentiallyInferredTypeParameters = mutableSetOf<KaTypeParameterSymbol>()

    fun collectPotentiallyInferredTypes(type: KaType, onlyCollectReturnTypeOfFunctionalType: Boolean) {
        when (type) {
            is KaTypeParameterType -> {
                val typeParameterSymbol = type.symbol
                if (typeParameterSymbol !in typeParametersToInfer || typeParameterSymbol in potentiallyInferredTypeParameters) return

                potentiallyInferredTypeParameters.add(type.symbol)
                type.symbol.upperBounds
                    .filterIsInstance<KaClassType>()
                    .filter { it.typeArguments.isNotEmpty() }
                    .forEach { collectPotentiallyInferredTypes(it, onlyCollectReturnTypeOfFunctionalType = false) }
            }

            is KaFunctionType -> {
                val typesToProcess = if (onlyCollectReturnTypeOfFunctionalType) {
                    listOf(type.returnType)
                } else {
                    listOfNotNull(type.receiverType) + type.returnType + type.parameterTypes
                }
                typesToProcess.forEach { collectPotentiallyInferredTypes(it, onlyCollectReturnTypeOfFunctionalType) }
            }

            is KaUsualClassType -> {
                val typeArguments = type.typeArguments.mapNotNull { it.type }
                typeArguments.forEach { collectPotentiallyInferredTypes(it, onlyCollectReturnTypeOfFunctionalType) }
            }

            else -> {}
        }
    }


    symbol.receiverParameter?.returnType?.let { collectPotentiallyInferredTypes(it, onlyCollectReturnTypeOfFunctionalType = true) }
    symbol.valueParameters.forEach { collectPotentiallyInferredTypes(it.returnType, onlyCollectReturnTypeOfFunctionalType = true) }

    if (expectedType != null && symbol.returnType isPossiblySubTypeOf expectedType) {
        collectPotentiallyInferredTypes(symbol.returnType, onlyCollectReturnTypeOfFunctionalType = false)
    }

    return potentiallyInferredTypeParameters.containsAll(symbol.typeParameters)
}

private fun FqName.isImported(importPath: ImportPath, skipAliasedImports: Boolean = true): Boolean {
    return when {
        skipAliasedImports && importPath.hasAlias() -> false
        importPath.isAllUnder && !isRoot -> importPath.fqName == this.parent()
        else -> importPath.fqName == this
    }
}

fun ImportPath.isImported(alreadyImported: ImportPath): Boolean {
    return if (isAllUnder || hasAlias()) this == alreadyImported else fqName.isImported(alreadyImported)
}

fun ImportPath.isImported(imports: Iterable<ImportPath>): Boolean = imports.any { isImported(it) }

fun ImportPath.isImported(imports: Iterable<ImportPath>, excludedFqNames: Iterable<FqName>): Boolean {
    return isImported(imports) && (isAllUnder || this.fqName !in excludedFqNames)
}

internal fun Name.toJavaGetterName(): Name? = identifierOrNullIfSpecial?.let { Name.identifier(JvmAbi.getterName(it)) }
internal fun Name.toJavaSetterName(): Name? = identifierOrNullIfSpecial?.let { Name.identifier(JvmAbi.setterName(it)) }

context(KaSession)
internal val KaSyntheticJavaPropertySymbol.getterAndUnitSetter: List<KaCallableSymbol>
    get() = listOfNotNull(javaGetterSymbol, javaSetterSymbol?.takeIf { it.returnType.isUnitType })

context(KaSession)
internal fun isEnumEntriesProperty(symbol: KaCallableSymbol): Boolean {
    return symbol is KaPropertySymbol &&
            symbol.isStatic &&
            symbol.callableId?.callableName == StandardNames.ENUM_ENTRIES &&
            (symbol.containingDeclaration as? KaClassSymbol)?.classKind == KaClassKind.ENUM_CLASS
}

internal fun KtCallableDeclaration.isKotlinBuiltins(): Boolean {
    val file = containingKtFile
    val virtualFile = file.virtualFile
    if (virtualFile.extension == METADATA_FILE_EXTENSION) return true
    if (this !is KtNamedFunction) return false
    return file.packageFqName.asString().replace(".", "/") + "/" + virtualFile.nameWithoutExtension in ktBuiltins
}

context(KaSession)
internal fun Sequence<KaCallableSignature<*>>.filterNonExtensions(
    visibilityChecker: CompletionVisibilityChecker,
    symbolFilter: (KaCallableSymbol) -> Boolean,
): Sequence<KaCallableSignature<*>> = this
    .filterNot { it.symbol.isExtension }
    .filter { symbolFilter(it.symbol) }
    .filter { visibilityChecker.isVisible(it.symbol) }

internal fun KaSymbolOrigin.isJavaSourceOrLibrary(): Boolean = this == KaSymbolOrigin.JAVA_SOURCE || this == KaSymbolOrigin.JAVA_LIBRARY
internal fun PsiElement.isInsideAnnotationEntryArgumentList(): Boolean = parentOfType<KtValueArgumentList>()?.parent is KtAnnotationEntry

private val ktBuiltins = setOf("kotlin/ArrayIntrinsicsKt", "kotlin/internal/ProgressionUtilKt")
private val KtFile.excludeEnumEntries: Boolean
    get() = !languageVersionSettings.supportsFeature(LanguageFeature.EnumEntries)

internal data class KtCallableSignatureWithContainingScopeKind(
    private val _signature: KaCallableSignature<*>,
    val scopeKind: KaScopeKind
) : KaLifetimeOwner {
    override val token: KaLifetimeToken
        get() = _signature.token
    val signature: KaCallableSignature<*> get() = withValidityAssertion { _signature }
}

internal data class ApplicableExtension(
    private val _signature: KaCallableSignature<*>,
    val insertionOptions: CallableInsertionOptions,
) : KaLifetimeOwner {
    override val token: KaLifetimeToken get() = _signature.token
    val signature: KaCallableSignature<*> = withValidityAssertion { _signature }
}

internal data class CallableWithMetadataForCompletion(
    private val _signature: KaCallableSignature<*>,
    private val _explicitReceiverTypeHint: KaType?,
    val options: CallableInsertionOptions,
    val symbolOrigin: CompletionSymbolOrigin,
) : KaLifetimeOwner {
    override val token: KaLifetimeToken
        get() = _signature.token
    val signature: KaCallableSignature<*> get() = withValidityAssertion { _signature }
}