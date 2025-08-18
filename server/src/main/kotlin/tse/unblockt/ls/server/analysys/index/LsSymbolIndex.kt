// Copyright 2024 Dmitrii Tseiler. Licensed under the Apache 2.0 Licence

package tse.unblockt.ls.server.analysys.index

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.symbols.KaClassLikeSymbol
import org.jetbrains.kotlin.name.Name

interface LsSymbolIndex {
    context(KaSession)
    fun getTopLevelClassLikes(byName: (Name?) -> Boolean = { true }, psiFilter: (PsiElement) -> Boolean = { true }): Sequence<KaClassLikeSymbol>
}