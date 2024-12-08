// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

package tse.unblockt.ls.server.analysys.index.machines

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import org.jetbrains.kotlin.analysis.utils.classId
import tse.unblockt.ls.server.analysys.index.common.IndexFileEntry
import tse.unblockt.ls.server.analysys.index.model.PsiEntry
import tse.unblockt.ls.server.analysys.storage.DB
import tse.unblockt.ls.server.analysys.storage.PersistentStorage

class JavaClassIdToClassIndexMachine(project: Project): PsiIndexMachine<String, PsiClass>(
    PsiClass::class,
    config = DB.Store.Config.UNIQUE_KEY_VALUE,
    attributeName = "class_id_to_top_class",
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
        return elements.mapNotNull { el ->
            val classId = el.element.classId ?: return@mapNotNull null
            classId.asString() to el
        }
    }
}