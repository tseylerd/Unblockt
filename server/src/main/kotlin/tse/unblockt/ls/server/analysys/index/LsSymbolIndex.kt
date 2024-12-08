// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

package tse.unblockt.ls.server.analysys.index

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.symbols.KaClassLikeSymbol
import org.jetbrains.kotlin.name.Name

interface LsSymbolIndex {
    context(KaSession)
    fun getTopLevelClassLikes(byName: (Name?) -> Boolean = { true }, psiFilter: (PsiElement) -> Boolean = { true }): Sequence<KaClassLikeSymbol>
}