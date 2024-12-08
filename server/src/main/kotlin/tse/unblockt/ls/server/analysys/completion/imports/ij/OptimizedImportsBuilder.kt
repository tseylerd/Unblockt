// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

@file:OptIn(KaIdeApi::class)
@file:Suppress("UnstableApiUsage")

package tse.unblockt.ls.server.analysys.completion.imports.ij

import org.jetbrains.kotlin.analysis.api.KaIdeApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.KaScopeKind
import org.jetbrains.kotlin.analysis.api.components.KaScopeWithKind
import org.jetbrains.kotlin.analysis.api.components.KaScopeWithKindImpl
import org.jetbrains.kotlin.analysis.api.scopes.KaScope
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbol
import org.jetbrains.kotlin.config.ApiVersion
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtBlockCodeFragment
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.resolve.ImportPath
import tse.unblockt.ls.server.analysys.completion.imports.CalculatedImports
import tse.unblockt.ls.server.analysys.completion.provider.impl.ij.isImported
import tse.unblockt.ls.server.analysys.psi.languageVersionSettings

internal class OptimizedImportsBuilder(
    private val file: KtFile,
    private val usedReferencesData: UsedReferencesCollector.Result
) {
    private val apiVersion: ApiVersion = file.languageVersionSettings.apiVersion

    fun KaSession.build(): Set<ImportPath>? {
        val importsToGenerate = hashSetOf<ImportPath>()
        val importableSymbols = usedReferencesData.usedSymbols

        val importsWithUnresolvedNames = file.importDirectives
            .filter { it.mayReferToSomeUnresolvedName() || it.isExistedUnresolvedName() }

        importsToGenerate += importsWithUnresolvedNames.mapNotNull { it.importPath }

        val symbolsByParentFqName = HashMap<FqName, MutableSet<ImportableKaSymbol>>()
        for (importableSymbol in importableSymbols) {
            val fqName = importableSymbol.run { computeImportableName() }
            for (name in usedReferencesData.usedDeclarations.getValue(fqName)) {
                val alias = if (name != fqName.shortName()) name else null

                val resultFqName = findCorrespondingKotlinFqName(fqName) ?: fqName
                val explicitImportPath = ImportPath(resultFqName, isAllUnder = false, alias)
                if (explicitImportPath in importsToGenerate) continue

                val parentFqName = resultFqName.parent()
                if (
                    alias == null &&
                    canUseStarImport(importableSymbol, resultFqName) &&
                    isAllowedByRules()
                ) {
                    symbolsByParentFqName.getOrPut(parentFqName) { hashSetOf() }.add(importableSymbol)
                } else {
                    importsToGenerate.add(explicitImportPath)
                }
            }
        }

        val classNamesToCheck = hashSetOf<FqName>()
        for ((parentFqName, symbols) in symbolsByParentFqName) {
            val starImportPath = ImportPath(parentFqName, isAllUnder = true)
            if (starImportPath in importsToGenerate) continue

            val fqNames = symbols.map { importSymbolWithMapping(it) }.toSet()

            val nameCountToUseStar = 5
            val useExplicitImports = !isAllowedByRules() || fqNames.size < nameCountToUseStar

            if (useExplicitImports) {
                fqNames.filter { fqName -> needExplicitImport(fqName) }
                    .mapTo(importsToGenerate) { ImportPath(it, isAllUnder = false) }
            } else {
                symbols.asSequence()
                    .filter { it is KaClassSymbol || it is KaCallableSymbol }
                    .map { importSymbolWithMapping(it) }
                    .filterTo(classNamesToCheck) {
                        true
                    }

                if (fqNames.all { needExplicitImport(it) }) {
                    importsToGenerate.add(starImportPath)
                }
            }
        }

        val importingScopes = buildImportingScopes(file, importsToGenerate.filter { it.isAllUnder })
        val hierarchicalScope = HierarchicalScope.run { createFrom(importingScopes) }

        for (fqName in classNamesToCheck) {
            val foundClassifiers = hierarchicalScope.findClassifiers(fqName.shortName()).firstOrNull()
            val singleFoundClassifier = foundClassifiers?.singleOrNull()


            val singleFoundClassifierFqName = singleFoundClassifier?.let {
                importSymbolWithMapping(it)
            }

            if (singleFoundClassifierFqName != fqName) {
                importsToGenerate.add(ImportPath(fqName, false))

                val parentFqName = fqName.parent()

                val siblingsToImport = symbolsByParentFqName.getValue(parentFqName)
                for (descriptor in siblingsToImport.filter { it.run { computeImportableName() } == fqName }) {
                    siblingsToImport.remove(descriptor)
                }

                if (siblingsToImport.isEmpty()) {
                    importsToGenerate.remove(ImportPath(parentFqName, true))
                }
            }
        }

        val oldImports = file.importDirectives.distinct()
        if (oldImports.size == importsToGenerate.size && oldImports.map { it.importPath }.toList() == importsToGenerate) return null

        return importsToGenerate.toSet()
    }

    private fun KaSession.buildImportingScopes(originalFile: KtFile, imports: Collection<ImportPath>): List<KaScopeWithKind> {
        val fileWithImports = buildFileWithImports(originalFile, imports)

        return fileWithImports.importingScopeContext.scopes.map { originalScope ->
            val fixedPackageScope = if (originalScope.kind is KaScopeKind.PackageMemberScope) {
                findPackageScope(originalFile)?.let { KaScopeWithKindImpl(it, originalScope.kind) }
            } else {
                null
            }

            fixedPackageScope ?: originalScope
        }
    }

    private fun KaSession.findPackageScope(file: KtFile): KaScope? =
        findPackage(file.packageFqName)?.packageScope

    private fun buildFileWithImports(
        originalFile: KtFile,
        importsToGenerate: Collection<ImportPath>
    ): KtFile {
        val imports = buildString {
            for (importPath in importsToGenerate) {
                append("import ")
                append(importPath)
                append("\n")
            }
        }

        val fileWithImports  = KtBlockCodeFragment(originalFile.project, "Dummy_" + originalFile.name, "", imports, originalFile)

        return fileWithImports
    }

    private fun KtImportDirective.mayReferToSomeUnresolvedName() =
        isAllUnder && usedReferencesData.unresolvedNames.isNotEmpty()

    private fun KtImportDirective.isExistedUnresolvedName() = importedName in usedReferencesData.unresolvedNames

    private fun findCorrespondingKotlinFqName(fqName: FqName): FqName? {
        return ImportMapper.findCorrespondingKotlinFqName(fqName, apiVersion)
    }

    private fun KaSession.importSymbolWithMapping(symbol: ImportableKaSymbol): FqName {
        val importableName = symbol.run { computeImportableName() }

        return findCorrespondingKotlinFqName(importableName) ?: importableName
    }

    private fun KaSession.importSymbolWithMapping(symbol: KaSymbol): FqName {
        val importableName = symbol.importableFqName!!

        return findCorrespondingKotlinFqName(importableName) ?: importableName
    }

    private fun KaSession.canUseStarImport(importableSymbol: ImportableKaSymbol, fqName: FqName): Boolean = when {
        fqName.parent().isRoot -> false
        (importableSymbol.run { containingClassSymbol() } as? KaClassSymbol)?.classKind?.isObject == true -> false
        else -> true
    }

    private fun isAllowedByRules(): Boolean {
        return true
    }

    private fun needExplicitImport(fqName: FqName): Boolean = hasAlias(fqName) || !isImportedByDefault(fqName)

    private fun hasAlias(fqName: FqName): Boolean = usedReferencesData.usedDeclarations[fqName].orEmpty().size > 1

    private fun isImportedByDefault(fqName: FqName): Boolean = isImportedWithDefault(ImportPath(fqName, isAllUnder = false), file)

    private fun isImportedWithDefault(importPath: ImportPath, contextFile: KtFile): Boolean {
        val imports = CalculatedImports(contextFile)
        return importPath.isImported(imports.defaultImports, imports.excludedImports)
    }
}

