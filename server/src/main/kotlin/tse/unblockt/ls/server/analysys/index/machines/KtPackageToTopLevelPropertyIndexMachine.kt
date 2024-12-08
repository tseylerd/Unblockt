// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

package tse.unblockt.ls.server.analysys.index.machines

import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtProperty
import tse.unblockt.ls.server.analysys.index.common.IndexFileEntry
import tse.unblockt.ls.server.analysys.index.model.PsiEntry
import tse.unblockt.ls.server.analysys.storage.DB
import tse.unblockt.ls.server.analysys.storage.PersistentStorage

class KtPackageToTopLevelPropertyIndexMachine(project: Project): PsiIndexMachine<String, KtProperty>(
    KtProperty::class,
    config = DB.Store.Config.UNIQUE_KEY_VALUE,
    attributeName = "package_to_top_level_property",
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

    override fun producePairs(entry: IndexFileEntry, elements: List<PsiEntry<KtProperty>>): List<Pair<String, PsiEntry<KtProperty>>> {
        return elements.mapNotNull { el ->
            if (!el.element.isTopLevel) {
                return@mapNotNull null
            }
            val fqPackageName = el.element.containingKtFile.packageFqName
            fqPackageName.asString() to el
        }
    }
}