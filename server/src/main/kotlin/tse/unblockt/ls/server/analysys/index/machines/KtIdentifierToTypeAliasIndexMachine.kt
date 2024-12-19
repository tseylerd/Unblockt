// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

package tse.unblockt.ls.server.analysys.index.machines

import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtTypeAlias
import tse.unblockt.ls.server.analysys.index.common.IndexFileEntry
import tse.unblockt.ls.server.analysys.index.model.PsiEntry
import tse.unblockt.ls.server.analysys.storage.PersistentStorage

class KtIdentifierToTypeAliasIndexMachine(project: Project): PsiIndexMachine<String, KtTypeAlias>(
    KtTypeAlias::class,
    attributeName = "expansion_short_name_to_type_alias",
    project
) {
    override fun keyToString(key: String): String {
        return key
    }

    override fun stringToKey(string: String): String {
        return string
    }

    override val namespace: PersistentStorage.Namespace
        get() = Namespaces.ourKotlinNamespace

    override fun support(entry: IndexFileEntry): Boolean {
        return entry.isKotlin && entry.psiFile is KtFile
    }

    override fun producePairs(entry: IndexFileEntry, elements: List<PsiEntry<KtTypeAlias>>): List<Pair<String, PsiEntry<KtTypeAlias>>> {
        return elements.mapNotNull { el ->
            val aliasedType = el.element.getTypeReference()?.name ?: return@mapNotNull null
            aliasedType to el
        }
    }
}