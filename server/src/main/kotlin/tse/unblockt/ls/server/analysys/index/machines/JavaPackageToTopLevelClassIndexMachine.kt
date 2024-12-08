// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

package tse.unblockt.ls.server.analysys.index.machines

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.util.PsiUtil
import org.jetbrains.kotlin.psi.psiUtil.isTopLevelKtOrJavaMember
import tse.unblockt.ls.server.analysys.index.common.IndexFileEntry
import tse.unblockt.ls.server.analysys.index.model.PsiEntry
import tse.unblockt.ls.server.analysys.storage.DB
import tse.unblockt.ls.server.analysys.storage.PersistentStorage

class JavaPackageToTopLevelClassIndexMachine(project: Project): PsiIndexMachine<String, PsiClass>(
    PsiClass::class,
    config = DB.Store.Config.UNIQUE_KEY_VALUE,
    attributeName = "package_to_top_level_class",
    project = project
) {
    override fun keyToString(key: String): String {
        return key
    }

    override fun stringToKey(string: String): String {
        return string
    }

    override val namespace: PersistentStorage.Namespace
        get() = Namespaces.ourJavaNamespace

    override fun support(entry: IndexFileEntry): Boolean {
        return !entry.isKotlin
    }

    override fun producePairs(entry: IndexFileEntry, elements: List<PsiEntry<PsiClass>>): List<Pair<String, PsiEntry<PsiClass>>> {
        return elements.filter { el ->
            el.element.isTopLevelKtOrJavaMember()
        }.mapNotNull { el ->
            val packageName = PsiUtil.getPackageName(el.element) ?: return@mapNotNull null
            packageName to el
        }
    }
}