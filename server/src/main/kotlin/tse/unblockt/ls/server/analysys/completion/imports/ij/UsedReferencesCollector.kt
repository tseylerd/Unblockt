// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

@file:Suppress("UnstableApiUsage")

package tse.unblockt.ls.server.analysys.completion.imports.ij

import com.intellij.openapi.progress.ProgressIndicatorProvider
import com.intellij.psi.ContributedReferenceHost
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.resolution.calls
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.symbol
import org.jetbrains.kotlin.idea.references.KDocReference
import org.jetbrains.kotlin.idea.references.KtInvokeFunctionReference
import org.jetbrains.kotlin.idea.references.KtReference
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*

internal class UsedReferencesCollector(private val file: KtFile) {

    data class Result(
        val usedDeclarations: Map<FqName, Set<Name>>,
        val unresolvedNames: Set<Name>,
        val usedSymbols: Set<ImportableKaSymbol>,
    )

    private val unresolvedNames: HashSet<Name> = hashSetOf()
    private val usedDeclarations: HashMap<FqName, MutableSet<Name>> = hashMapOf()
    private val importableSymbols: HashSet<ImportableKaSymbol> = hashSetOf()

    private val aliases: Map<FqName, List<Name>> = collectImportAliases(file)

    fun KaSession.collectUsedReferences(): Result {
        file.accept(object : KtVisitorVoid() {
            override fun visitElement(element: PsiElement) {
                ProgressIndicatorProvider.checkCanceled()
                element.acceptChildren(this)
            }

            override fun visitImportList(importList: KtImportList) {}

            override fun visitPackageDirective(directive: KtPackageDirective) {}

            override fun visitKtElement(element: KtElement) {
                super.visitKtElement(element)

                collectReferencesFrom(element)
            }
        })

        val importableSymbolPointers = importableSymbols
            .map { it }
            .toSet()

        return Result(usedDeclarations, unresolvedNames, importableSymbolPointers)
    }

    private fun KaSession.collectReferencesFrom(element: KtElement) {
        if (element is ContributedReferenceHost) return

        if (element is KtLabelReferenceExpression) return

        val references = element.references
            .filterIsInstance<KtReference>()

        if (references.isEmpty()) return

        for (reference in references) {
            ProgressIndicatorProvider.checkCanceled()

            val isResolved = reference.run { isResolved() }

            val names = reference.run { resolvesByNames() }
            if (!isResolved) {
                unresolvedNames += names
                continue
            }

            val symbols = reference.run { resolveToImportableSymbols() }

            for (symbol in symbols) {
                if (!symbol.run { isResolvedWithImport() }) continue

                val importableName = symbol.run { computeImportableFqName() } ?: continue

                if (importableName.parent() == file.packageFqName && importableName !in aliases) continue

                ProgressIndicatorProvider.checkCanceled()

                val newNames = (aliases[importableName].orEmpty() + importableName.shortName()).intersect(names.toSet())
                usedDeclarations.getOrPut(importableName) { hashSetOf() } += newNames

                importableSymbols += symbol.run { toImportableKaSymbol() }
            }
        }
    }

    context(KaSession)
    private fun KtReference.resolveToImportableSymbols(): Collection<UsedSymbol> {
        return resolveToSymbols().mapNotNull { toImportableSymbol(it, this) }.map { UsedSymbol(this, it) }
    }

    private fun KaSession.toImportableSymbol(
        target: KaSymbol,
        reference: KtReference,
        containingFile: KtFile = reference.element.containingKtFile,
    ): KaSymbol? = when {
        target is KaReceiverParameterSymbol -> null

        reference.isImplicitReferenceToCompanion() -> {
            (target as? KaNamedClassSymbol)?.containingSymbol
        }

        target is KaConstructorSymbol -> {
            val targetClass = target.containingSymbol as? KaClassLikeSymbol

            val typeAlias = targetClass?.let { resolveTypeAliasedConstructorReference(reference, it, containingFile) }

            val notInnerTargetClass = targetClass?.takeUnless { it is KaNamedClassSymbol && it.isInner }

            typeAlias ?: notInnerTargetClass
        }

        target is KaSamConstructorSymbol -> {
            val targetClass = findSamClassFor(target)

            targetClass?.let { resolveTypeAliasedConstructorReference(reference, it, containingFile) } ?: targetClass
        }

        else -> target
    }

    private fun KaSession.findSamClassFor(samConstructorSymbol: KaSamConstructorSymbol): KaClassSymbol? {
        val samCallableId = samConstructorSymbol.callableId ?: return null
        if (samCallableId.isLocal) return null

        val samClassId = ClassId.fromString(samCallableId.toString())

        return findClass(samClassId)
    }

    private fun KaSession.typeAliasIsAvailable(name: Name, containingFile: KtFile): Boolean {
        val importingScope = containingFile.importingScopeContext
        val foundClassifiers = importingScope.compositeScope().classifiers(name)

        return foundClassifiers.any { it is KaTypeAliasSymbol }
    }

    private fun KaSession.resolveTypeAliasedConstructorReference(
        reference: KtReference,
        expandedClassSymbol: KaClassLikeSymbol,
        containingFile: KtFile,
    ): KaClassLikeSymbol? {
        val originalReferenceName = reference.resolvesByNames.singleOrNull() ?: return null

        if (!typeAliasIsAvailable(originalReferenceName, containingFile)) return null

        val referencedType = resolveReferencedType(reference) ?: return null
        if (referencedType.symbol != expandedClassSymbol) return null

        val typealiasType = referencedType.abbreviation ?: return null

        return typealiasType.symbol
    }

    private fun KaSession.resolveReferencedType(reference: KtReference): KaType? {
        val originalReferenceName = reference.resolvesByNames.singleOrNull() ?: return null

        val psiFactory = KtPsiFactory.contextual(reference.element)
        val psiType = psiFactory.createTypeCodeFragment(originalReferenceName.asString(), context = reference.element).getContentElement()

        return psiType?.type
    }

    context(KaSession)
    private fun KtReference.resolvesByNames(): Collection<Name> {
        if (this is KDocReference && !isResolved()) {
            return emptyList()
        }

        return resolvesByNames
    }

    context(KaSession)
    private fun KtReference.isResolved(): Boolean {
        if (this is KtInvokeFunctionReference) {
            val callInfo = element.resolveToCall() ?: return false

            return callInfo.calls.isNotEmpty()
        }

        val resolvedSymbols = resolveToSymbols()

        return resolvedSymbols.isNotEmpty()
    }
}

private fun collectImportAliases(file: KtFile): Map<FqName, List<Name>> = if (file.hasImportAlias()) {
    file.importDirectives
        .asSequence()
        .filter { !it.isAllUnder && it.alias != null }
        .mapNotNull { it.importPath }
        .groupBy(keySelector = { it.fqName }, valueTransform = { it.importedName as Name })
} else {
    emptyMap()
}
