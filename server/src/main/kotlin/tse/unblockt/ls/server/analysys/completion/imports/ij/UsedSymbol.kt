// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package tse.unblockt.ls.server.analysys.completion.imports.ij

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMember
import org.jetbrains.kotlin.analysis.api.KaIdeApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.KaScopeKind
import org.jetbrains.kotlin.analysis.api.resolution.*
import org.jetbrains.kotlin.analysis.api.scopes.KaScope
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.symbols.markers.KaNamedSymbol
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.symbol
import org.jetbrains.kotlin.idea.references.KDocReference
import org.jetbrains.kotlin.idea.references.KtReference
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.withClassId
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtCallableReferenceExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.psiUtil.getReceiverExpression

internal class UsedSymbol(val reference: KtReference, val symbol: KaSymbol) {
    fun KaSession.computeImportableFqName(): FqName? {
        return computeImportableName(symbol, resolveDispatchReceiver(reference.element) as? KaImplicitReceiverValue?)
    }

    fun KaSession.isResolvedWithImport(): Boolean {
        val isNotAliased = symbol.name in reference.resolvesByNames

        if (isNotAliased && isAccessibleAsMemberCallable(symbol, reference.element)) return false
        if (isNotAliased && isAccessibleAsMemberClassifier(symbol, reference.element)) return false

        return canBeResolvedViaImport(reference, symbol)
    }

    fun KaSession.toImportableKaSymbol(): ImportableKaSymbol {
        return when (symbol) {
            is KaCallableSymbol -> {
                val dispatcherReceiver = resolveDispatchReceiver(reference.element) as? KaImplicitReceiverValue
                val containingClassSymbol = dispatcherReceiver?.symbol as? KaClassLikeSymbol

                ImportableKaSymbol.run { create(symbol, containingClassSymbol) }
            }

            is KaClassLikeSymbol -> ImportableKaSymbol.run { create(symbol) }

            else -> error("Unexpected symbol type ${symbol::class}")
        }
    }
}

private fun KaSession.resolveDispatchReceiver(element: KtElement): KaReceiverValue? {
    val adjustedElement = element.callableReferenceExpressionForCallableReference() ?: element
    val dispatchReceiver = adjustedElement.resolveToCall()
        ?.singleCallOrNull<KaCallableMemberCall<*, *>>()?.partiallyAppliedSymbol?.dispatchReceiver

    return dispatchReceiver
}

private fun KtElement.callableReferenceExpressionForCallableReference(): KtCallableReferenceExpression? =
    (parent as? KtCallableReferenceExpression)?.takeIf { it.callableReference == this }

@OptIn(KaIdeApi::class)
fun KaSession.computeImportableName(
    target: KaSymbol,
    implicitDispatchReceiver: KaImplicitReceiverValue? // TODO: support other types of dispatcher values
): FqName? {
    if (implicitDispatchReceiver == null) {
        return target.importableFqName
    }

    if (target !is KaCallableSymbol) return null

    val callableId = target.callableId ?: return null
    if (callableId.classId == null) return null

    val implicitReceiver = implicitDispatchReceiver.symbol as? KaClassLikeSymbol ?: return null
    val implicitReceiverClassId = implicitReceiver.classId ?: return null

    val substitutedCallableId = callableId.withClassId(implicitReceiverClassId)

    return substitutedCallableId.asSingleFqName()
}
private fun KaSession.isAccessibleAsMemberCallable(
    symbol: KaSymbol,
    element: KtElement,
): Boolean {
    if (symbol !is KaCallableSymbol || containingDeclarationPatched(symbol) !is KaClassLikeSymbol) return false

    if (symbol is KaEnumEntrySymbol) {
        return isAccessibleAsMemberCallableDeclaration(symbol, element)
    }

    val dispatchReceiver = resolveDispatchReceiver(element) ?: return false

    return isDispatchedCall(element, symbol, dispatchReceiver)
}

internal fun KaSession.containingDeclarationPatched(symbol: KaSymbol): KaDeclarationSymbol? {
    symbol.containingDeclaration?.let { return it }

    val declarationPsi = symbol.psi

    if (declarationPsi is PsiMember) {
        val containingClass = declarationPsi.parent as? PsiClass
        containingClass?.namedClassSymbol?.let { return it }
    }

    return null
}

internal fun KaSession.isAccessibleAsMemberCallableDeclaration(
    symbol: KaCallableSymbol,
    contextPosition: KtElement,
): Boolean {
    if (containingDeclarationPatched(symbol) !is KaClassLikeSymbol) return false

    if (symbol !is KaNamedSymbol) return false

    val nonImportingScopes = nonImportingScopesForPosition(contextPosition).asCompositeScope()

    return nonImportingScopes.callables(symbol.name).any { it == symbol }
}

private fun KaSession.nonImportingScopesForPosition(element: KtElement): List<KaScope> {
    val scopeContext = element.containingKtFile.scopeContext(element)

    val implicitReceiverScopeIndices = scopeContext.implicitReceivers.map { it.scopeIndexInTower }.toSet()

    val nonImportingScopes = scopeContext.scopes
        .asSequence()
        .filterNot { it.kind is KaScopeKind.ImportingScope }
        .filterNot { it.kind.indexInTower in implicitReceiverScopeIndices }
        .map { it.scope }
        .toList()

    return nonImportingScopes
}

private fun KaSession.isDispatchedCall(
    element: KtElement,
    symbol: KaCallableSymbol,
    dispatchReceiver: KaReceiverValue,
): Boolean {
    return when (dispatchReceiver) {
        is KaExplicitReceiverValue -> true

        is KaSmartCastedReceiverValue -> isDispatchedCall(element, symbol, dispatchReceiver.original)

        is KaImplicitReceiverValue -> !isStaticallyImportedReceiver(element, symbol, dispatchReceiver)
    }
}

private fun KaSession.isStaticallyImportedReceiver(
    element: KtElement,
    symbol: KaCallableSymbol,
    implicitDispatchReceiver: KaImplicitReceiverValue,
): Boolean {
    val receiverTypeSymbol = implicitDispatchReceiver.type.symbol ?: return false
    val receiverIsObject = receiverTypeSymbol is KaClassSymbol && receiverTypeSymbol.classKind.isObject

    if (!receiverIsObject) return false

    return if (symbol.isJavaStaticDeclaration()) {
        !isAccessibleAsMemberCallableDeclaration(symbol, element)
    } else {
        !typeIsPresentAsImplicitReceiver(implicitDispatchReceiver.type, element)
    }
}

internal fun KaCallableSymbol.isJavaStaticDeclaration(): Boolean =
    when (this) {
        is KaNamedFunctionSymbol -> isStatic
        is KaPropertySymbol -> isStatic
        is KaJavaFieldSymbol -> isStatic
        else -> false
    }

internal fun KaSession.typeIsPresentAsImplicitReceiver(
    type: KaType,
    contextPosition: KtElement,
): Boolean {
    val containingFile = contextPosition.containingKtFile
    val implicitReceivers = containingFile.scopeContext(contextPosition).implicitReceivers

    return implicitReceivers.any { it.type.semanticallyEquals(type) }
}

internal fun KaSession.isAccessibleAsMemberClassifier(symbol: KaSymbol, element: KtElement): Boolean {
    if (symbol !is KaClassLikeSymbol || containingDeclarationPatched(symbol) !is KaClassLikeSymbol) return false

    val name = symbol.name ?: return false

    val nonImportingScopes = nonImportingScopesForPosition(element).asCompositeScope()

    val foundClasses = nonImportingScopes.classifiers(name)
    val foundClass = foundClasses.firstOrNull()

    return symbol == foundClass
}

private fun KaSession.canBeResolvedViaImport(reference: KtReference, target: KaSymbol): Boolean {
    if (reference is KDocReference) {
        return false
    }

    if (target is KaCallableSymbol && target.isExtension) {
        return true
    }

    val referenceExpression = reference.element as? KtNameReferenceExpression

    val explicitReceiver = referenceExpression?.getReceiverExpression()
        ?: referenceExpression?.callableReferenceExpressionForCallableReference()?.receiverExpression

    if (explicitReceiver != null) {
        val extensionReceiver = resolveExtensionReceiverForFunctionalTypeVariable(referenceExpression, target)
        return extensionReceiver?.expression == explicitReceiver
    }

    return true
}

private fun KaSession.resolveExtensionReceiverForFunctionalTypeVariable(
    referenceExpression: KtNameReferenceExpression?,
    target: KaSymbol,
): KaExplicitReceiverValue? {
    val parentCall = referenceExpression?.parent as? KtCallExpression
    val isFunctionalTypeVariable = target is KaPropertySymbol && target.returnType.let { it.isFunctionType || it.isSuspendFunctionType }

    if (parentCall == null || !isFunctionalTypeVariable) {
        return null
    }

    val parentCallInfo = parentCall.resolveToCall()?.singleCallOrNull<KaSimpleFunctionCall>() ?: return null
    if (!parentCallInfo.isImplicitInvoke) return null

    return parentCallInfo.partiallyAppliedSymbol.extensionReceiver as? KaExplicitReceiverValue
}