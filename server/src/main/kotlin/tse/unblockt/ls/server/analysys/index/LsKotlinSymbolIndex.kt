// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

package tse.unblockt.ls.server.analysys.index

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassLikeSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.KaFlexibleType
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtTypeAlias
import tse.unblockt.ls.server.analysys.completion.provider.impl.ij.isKotlinBuiltins
import tse.unblockt.ls.server.analysys.index.machines.KtIdentifierToTypeAliasIndexMachine
import tse.unblockt.ls.server.analysys.index.machines.Namespaces
import tse.unblockt.ls.server.analysys.storage.PersistentStorage

class LsKotlinSymbolIndex(private val project: Project): LsSymbolIndex {
    companion object {
        fun instance(project: Project): LsKotlinSymbolIndex {
            return LsKotlinSymbolIndex(project)
        }
    }
    private val indexer by lazy {
        LsSourceCodeIndexer.instance(project)
    }
    private val storage by lazy {
        PersistentStorage.instance(project)
    }
    private val psiIndex by lazy {
        LsKotlinPsiIndex.instance(project)
    }

    context(KaSession)
    fun getCallableExtensions(
        nameFilter: (Name) -> Boolean,
        receiverTypes: List<KaType>,
        psiFilter: (KtCallableDeclaration) -> Boolean = { true },
    ): List<KaCallableSymbol> {
        val allTypes = receiverTypes.flatMap { allNamesByHierarchy(it) }.takeIf { it.isNotEmpty() }?.toSet() ?: return emptyList()
        return allTypes.flatMap {
            psiIndex.getAllCallablesDeclarationByReceiverType(it)
        }.filter { callable ->
            val nameAsName = callable.nameAsName ?: return@filter false
            nameFilter(nameAsName) && psiFilter(callable) && !callable.isKotlinBuiltins()
        }.mapNotNull {
            it.symbol as? KaCallableSymbol
        }
    }

    context(KaSession)
    override fun getTopLevelClassLikes(
        byName: (Name?) -> Boolean,
        psiFilter: (PsiElement) -> Boolean
    ): Sequence<KaClassLikeSymbol> {
        return psiIndex.getTopLevelClassLikes().filter { el ->
            byName(el.nameAsName)
        }.mapNotNull {
            when (it) {
                is KtClassOrObject -> it.namedClassSymbol
                is KtTypeAlias -> it.symbol
                else -> null
            }
        }
    }

    context(KaSession)
    private fun allNamesByHierarchy(type: KaType): Set<Name> {
        when (type) {
            is KaFlexibleType -> return allNamesByHierarchy(type.lowerBound)
            !is KaClassType -> return emptySet()
            else -> {
                val result = mutableSetOf<Name>()
                val typeName = type.classId.shortClassName.takeUnless { it.isSpecial } ?: return emptySet()
                result += typeName
                result += getTypeAliases(typeName)

                val superTypes = (type.symbol as? KaClassSymbol)?.superTypes ?: return result
                for (superType in superTypes) {
                    result += allNamesByHierarchy(superType)
                }
                return result
            }
        }
    }

    private fun getTypeAliases(typeName: Name): Set<Name> {
        val result = mutableSetOf<Name>()
        val att = indexer[KtIdentifierToTypeAliasIndexMachine::class].attribute
        val all = storage.getSequence(Namespaces.ourKotlinNamespace, att, typeName.identifier).mapNotNull { it.element.nameAsName }
        for (name in all) {
            result.add(name)
            result.addAll(getTypeAliases(name))
        }
        return result
    }
}
