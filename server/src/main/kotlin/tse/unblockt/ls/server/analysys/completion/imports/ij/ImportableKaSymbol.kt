// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

@file:OptIn(KaExperimentalApi::class, KaIdeApi::class)

package tse.unblockt.ls.server.analysys.completion.imports.ij

import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaIdeApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassLikeSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbol
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.withClassId

internal sealed interface ImportableKaSymbol {
    fun KaSession.computeImportableName(): FqName
    fun KaSession.containingClassSymbol(): KaClassLikeSymbol?

    companion object {
        fun KaSession.create(symbol: KaClassLikeSymbol): ImportableKaSymbol {
            val classImportableName = computeImportableName(symbol, containingClass = null) ?:
            error("Cannot compute importable name for class symbol ${symbol.render()}")

            return ImportableKaClassLikeSymbol(symbol, classImportableName)
        }

        fun KaSession.create(symbol: KaCallableSymbol, containingClassSymbol: KaClassLikeSymbol?): ImportableKaSymbol {
            val symbolImportableName = computeImportableName(symbol, containingClassSymbol) ?:
            error("Cannot compute importable name for callable symbol ${symbol.render()}")

            return ImportableKaCallableSymbol(symbol, containingClassSymbol, symbolImportableName)
        }
    }
}

internal data class ImportableKaClassLikeSymbol(
    private val symbol: KaClassLikeSymbol,
    private val importableFqName: FqName,
): ImportableKaSymbol {
    override fun KaSession.computeImportableName(): FqName = importableFqName

    override fun KaSession.containingClassSymbol(): KaClassLikeSymbol? {
        return containingDeclarationPatched(symbol) as? KaClassLikeSymbol
    }
}

internal data class ImportableKaCallableSymbol(
    private val symbol: KaCallableSymbol,
    private val containingClassSymbol: KaClassLikeSymbol?,
    private val importableFqName: FqName,
) : ImportableKaSymbol {
    override fun KaSession.computeImportableName(): FqName = importableFqName

    override fun KaSession.containingClassSymbol(): KaClassLikeSymbol? {
        return containingClassSymbol ?: (containingDeclarationPatched(symbol) as? KaClassLikeSymbol)
    }
}

private fun KaSession.computeImportableName(
    target: KaSymbol,
    containingClass: KaClassLikeSymbol?
): FqName? {
    if (containingClass == null) {
        return target.importableFqName
    }

    if (target !is KaCallableSymbol) return null

    val callableId = target.callableId ?: return null
    if (callableId.classId == null) return null

    val receiverClassId = containingClass.classId ?: return null

    val substitutedCallableId = callableId.withClassId(receiverClassId)

    return substitutedCallableId.asSingleFqName()
}