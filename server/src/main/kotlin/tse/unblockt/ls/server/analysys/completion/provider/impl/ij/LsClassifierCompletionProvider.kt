// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

@file:OptIn(KaIdeApi::class, KaExperimentalApi::class)

package tse.unblockt.ls.server.analysys.completion.provider.impl.ij

import com.intellij.psi.PsiClass
import com.intellij.psi.impl.source.tree.java.PsiReferenceExpressionImpl
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaIdeApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.KaScopeKind
import org.jetbrains.kotlin.analysis.api.components.KaScopeKinds
import org.jetbrains.kotlin.analysis.api.components.KaScopeWithKindImpl
import org.jetbrains.kotlin.analysis.api.lifetime.KaLifetimeOwner
import org.jetbrains.kotlin.analysis.api.lifetime.KaLifetimeToken
import org.jetbrains.kotlin.analysis.api.lifetime.withValidityAssertion
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.symbols.markers.KaDeclarationContainerSymbol
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtEnumEntry
import org.jetbrains.kotlin.psi.KtFile
import tse.unblockt.ls.protocol.CompletionItem
import tse.unblockt.ls.server.analysys.completion.LsCompletionRequest
import tse.unblockt.ls.server.analysys.completion.imports.AutoImportAction
import tse.unblockt.ls.server.analysys.completion.provider.CompletionItemsProvider
import tse.unblockt.ls.server.analysys.completion.provider.ij.CompletionSymbolOrigin
import tse.unblockt.ls.server.analysys.completion.util.ClassifierCompletionItem
import tse.unblockt.ls.server.analysys.completion.util.ij.CompletionVisibilityChecker
import tse.unblockt.ls.server.analysys.completion.util.ij.KotlinNameReferencePositionContext
import tse.unblockt.ls.server.analysys.psi.asReference
import tse.unblockt.ls.server.threading.Cancellable

internal open class LsClassifierCompletionProvider : CompletionItemsProvider {

    context(KaSession)
    protected open fun filterClassifiers(classifierSymbol: KaClassifierSymbol): Boolean = true

    context(KaSession, Cancellable)
    override fun provide(request: LsCompletionRequest): Sequence<CompletionItem> {
        val context = request.positionContext as? KotlinNameReferencePositionContext
            ?: return emptySequence()

        val visibilityChecker = CompletionVisibilityChecker(request)
        return sequence {
            val result = when (val receiver = context.explicitReceiver) {
                null -> {
                    val availableClassifiersFromIndex = getAvailableClassifiersFromIndex(
                        request,
                        request.scopeNameFilter,
                        visibilityChecker
                    )
                    completeWithoutReceiver(request, context, visibilityChecker, availableClassifiersFromIndex).distinct()
                }
                else -> completeWithReceiver(request, receiver, visibilityChecker).distinct()
            }

            for (completionItem in result) {
                cancellationPoint()
                yield(completionItem)
            }
        }
    }

    context(KaSession)
    private fun completeWithReceiver(
        request: LsCompletionRequest,
        receiver: KtElement,
        visibilityChecker: CompletionVisibilityChecker,
    ): Sequence<CompletionItem> {
        val symbols = receiver.asReference
            ?.resolveToSymbols()
            ?: return emptySequence()

        return symbols.asSequence()
            .mapNotNull { it.staticScope }
            .flatMap { scopeWithKind ->
                scopeWithKind.scope
                    .classifiers(request.scopeNameFilter)
                    .filter { filterClassifiers(it) }
                    .filter { visibilityChecker.isVisible(it) }
                    .mapNotNull {
                        ClassifierCompletionItem(
                            request, it, AutoImportAction.DoNothing
                        )
                    }
            }
    }

    context(KaSession)
    private fun completeWithoutReceiver(
        request: LsCompletionRequest,
        positionContext: KotlinNameReferencePositionContext,
        visibilityChecker: CompletionVisibilityChecker,
        additionalSymbols: Sequence<KaClassifierSymbol>
    ): Sequence<CompletionItem> {
        val availableFromScope = mutableSetOf<KaClassifierSymbol>()
        val items = getAvailableClassifiersCurrentScope(
            request.originalFile,
            positionContext.nameExpression,
            request.scopeNameFilter,
            visibilityChecker
        ).filter { filterClassifiers(it.symbol) }
            .mapNotNull { symbolWithScopeKind ->
                val classifierSymbol = symbolWithScopeKind.symbol
                availableFromScope += classifierSymbol
                ClassifierCompletionItem(
                    request,
                    classifierSymbol,
                    AutoImportAction.ofClassLike(classifierSymbol)
                )
            }

        if (!request.matcher.isEmpty) {
            val resolveExtensionScope = resolveExtensionScopeWithTopLevelDeclarations
            val declarationsFromExtension = resolveExtensionScope.classifiers(request.scopeNameFilter).filterIsInstance<KaClassLikeSymbol>()
            return items + additionalSymbols.plus(declarationsFromExtension).filter { it !in availableFromScope && filterClassifiers(it) }
                .mapNotNull { classifierSymbol ->
                    ClassifierCompletionItem(request, classifierSymbol, AutoImportAction.ofClassLike(classifierSymbol))
                }
        }
        return items
    }
}

context(KaSession)
internal val KaSymbol.staticScope
    get() = when (this) {
        is KaDeclarationContainerSymbol -> KaScopeWithKindImpl(
            backingScope = if (this is KaNamedClassSymbol && classKind.isObject) memberScope else staticMemberScope,
            backingKind = KaScopeKinds.StaticMemberScope(CompletionSymbolOrigin.SCOPE_OUTSIDE_TOWER_INDEX),
        )

        is KaPackageSymbol -> KaScopeWithKindImpl(
            backingScope = packageScope,
            backingKind = KaScopeKinds.PackageMemberScope(CompletionSymbolOrigin.SCOPE_OUTSIDE_TOWER_INDEX),
        )

        else -> null
    }

context(KaSession)
internal fun getAvailableClassifiersCurrentScope(
    originalKtFile: KtFile,
    position: KtElement,
    scopeNameFilter: (Name) -> Boolean,
    visibilityChecker: CompletionVisibilityChecker
): Sequence<KaClassifierSymbolWithContainingScopeKind> =
    originalKtFile.scopeContext(position).scopes.asSequence().flatMap { scopeWithKind ->
        scopeWithKind.scope.classifiers(scopeNameFilter)
            .filter { visibilityChecker.isVisible(it) }
            .map { KaClassifierSymbolWithContainingScopeKind(it, scopeWithKind.kind) }
    }

context(KaSession)
internal fun getAvailableClassifiersFromIndex(
    request: LsCompletionRequest,
    scopeNameFilter: (Name) -> Boolean,
    visibilityChecker: CompletionVisibilityChecker
): Sequence<KaClassifierSymbol> {
    val kotlinDeclarations = completeKotlinClasses(request, scopeNameFilter, visibilityChecker)
    val javaDeclarations = completeJavaClasses(request, scopeNameFilter)
    return kotlinDeclarations.filter {
        visibilityChecker.isVisible(it)
    } + javaDeclarations.filterIsInstance<KaClassSymbol>().filter {
        visibilityChecker.isVisible(it)
    }
}

context(KaSession)
private fun completeKotlinClasses(
    request: LsCompletionRequest,
    scopeNameFilter: (Name) -> Boolean,
    visibilityChecker: CompletionVisibilityChecker,
): Sequence<KaClassLikeSymbol> = request.indexes.kotlinSymbol.getTopLevelClassLikes({ nameMaybe ->
    nameMaybe != null && scopeNameFilter(nameMaybe)
}, psiFilter = { ktClass ->
    if (ktClass !is KtClassOrObject) return@getTopLevelClassLikes false
    if (ktClass is KtEnumEntry) return@getTopLevelClassLikes false
    if (ktClass.getClassId() == null) return@getTopLevelClassLikes true
    !visibilityChecker.isDefinitelyInvisibleByPsi(ktClass)
})

context(KaSession)
private fun completeJavaClasses(
    request: LsCompletionRequest,
    scopeNameFilter: (Name) -> Boolean
): Sequence<KaClassLikeSymbol> {
    return request.indexes.javaSymbol.getTopLevelClassLikes({ n -> n != null && scopeNameFilter(n) }) { psiClass ->
        if (psiClass !is PsiClass) {
            return@getTopLevelClassLikes false
        }
        val className = psiClass.name
        if (className?.firstOrNull()?.isLowerCase() == true) {
            return@getTopLevelClassLikes false
        }

        if (PsiReferenceExpressionImpl.seemsScrambled(psiClass) || isInternal(psiClass)) {
            return@getTopLevelClassLikes false
        }
        true
    }.filter {
        it is KaNamedClassSymbol
    }
}

private fun isInternal(clazz: PsiClass): Boolean {
    val name = clazz.name
    return name != null && name.startsWith("$")
}

internal data class KaClassifierSymbolWithContainingScopeKind(
    private val _symbol: KaClassifierSymbol,
    val scopeKind: KaScopeKind
) : KaLifetimeOwner {
    override val token: KaLifetimeToken
        get() = _symbol.token
    val symbol: KaClassifierSymbol get() = withValidityAssertion { _symbol }
}