// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

@file:Suppress("UnstableApiUsage")

package tse.unblockt.ls.server.analysys.completion.imports.ij

import org.jetbrains.kotlin.analysis.api.KaPlatformInterface
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.platform.projectStructure.KotlinProjectStructureProvider
import org.jetbrains.kotlin.analysis.api.projectStructure.KaNotUnderContentRootModule
import org.jetbrains.kotlin.name.parentOrNull
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.resolve.ImportPath

object ImportsAnalyzer {
    context(KaSession)
    internal fun analyzeImports(file: KtFile): ImportData? {
        if (!canOptimizeImports(file)) return null


        val referenceCollector = UsedReferencesCollector(file)
        val importAnalysis = referenceCollector.run { collectUsedReferences() }

        val unusedImports = computeUnusedImports(file, importAnalysis)
        return ImportData(unusedImports.toList(), importAnalysis)
    }

    private fun computeUnusedImports(file: KtFile, result: UsedReferencesCollector.Result): Set<KtImportDirective> {
        val existingImports = file.importDirectives
        if (existingImports.isEmpty()) return emptySet()

        val explicitlyImportedFqNames = existingImports
            .asSequence()
            .mapNotNull { it.importPath }
            .filter { !it.isAllUnder && !it.hasAlias() }
            .map { it.fqName }
            .toSet()

        val (usedImports, unresolvedNames) = result.usedDeclarations to result.unresolvedNames

        val referencesEntities = usedImports
            .filterNot { (fqName, referencedByNames) ->
                val fromCurrentPackage = fqName.parentOrNull() == file.packageFqName
                val noAliasedImports = referencedByNames.singleOrNull() == fqName.shortName()

                fromCurrentPackage && noAliasedImports
            }

        val requiredStarImports = referencesEntities.keys
            .asSequence()
            .filterNot { it in explicitlyImportedFqNames }
            .mapNotNull { it.parentOrNull() }
            .filterNot { it.isRoot }
            .toSet()

        val unusedImports = mutableSetOf<KtImportDirective>()
        val alreadySeenImports = mutableSetOf<ImportPath>()

        for (import in existingImports) {
            val importPath = import.importPath ?: continue

            val isUsed = when {
                importPath.importedName in unresolvedNames -> true
                !alreadySeenImports.add(importPath) -> false
                importPath.isAllUnder -> unresolvedNames.isNotEmpty() || importPath.fqName in requiredStarImports
                importPath.fqName in referencesEntities -> importPath.importedName in referencesEntities.getValue(importPath.fqName)
                else -> false
            }

            if (!isUsed) {
                unusedImports += import
            }
        }

        return unusedImports
    }

    @OptIn(KaPlatformInterface::class)
    private fun canOptimizeImports(file: KtFile): Boolean {
        val module = KotlinProjectStructureProvider.getModule(file.project, file, useSiteModule = null)
        return module !is KaNotUnderContentRootModule
    }
}

internal data class ImportData(
    val unusedImports: List<KtImportDirective>,
    val usedReferencesData: UsedReferencesCollector.Result,
)
