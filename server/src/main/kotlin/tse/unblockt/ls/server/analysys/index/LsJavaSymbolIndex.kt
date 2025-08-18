// Copyright 2024 Dmitrii Tseiler. Licensed under the Apache 2.0 Licence

package tse.unblockt.ls.server.analysys.index

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.symbols.KaClassLikeSymbol
import org.jetbrains.kotlin.name.Name

class LsJavaSymbolIndex(private val project: Project): LsSymbolIndex {
    companion object {
        fun instance(project: Project): LsJavaSymbolIndex {
            return LsJavaSymbolIndex(project)
        }
    }

    private val psiIndex: LsJavaPsiIndex by lazy {
        LsJavaPsiIndex.instance(project)
    }

    context(KaSession)
    override fun getTopLevelClassLikes(
        byName: (Name?) -> Boolean,
        psiFilter: (PsiElement) -> Boolean
    ): Sequence<KaClassLikeSymbol> {
        return psiIndex.getTopLevelClasses().filter { psi: PsiClass ->
            if (!psiFilter(psi)) return@filter false

            val name = (psi as PsiNamedElement).name
            byName(if (name == null || !Name.isValidIdentifier(name)) null else Name.identifier(name))
        }.mapNotNull {
            it.namedClassSymbol
        }
    }
}