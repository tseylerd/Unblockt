// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

package tse.unblockt.ls.server.analysys.index.machines

import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.psi.KtClassLikeDeclaration
import org.jetbrains.kotlin.psi.KtEnumEntry
import org.jetbrains.kotlin.psi.KtFile
import tse.unblockt.ls.server.analysys.index.common.IndexFileEntry
import tse.unblockt.ls.server.analysys.index.model.PsiEntry
import tse.unblockt.ls.server.analysys.storage.DB
import tse.unblockt.ls.server.analysys.storage.PersistentStorage

class KtClassIdToClassIndexMachine(project: Project): PsiIndexMachine<String, KtClassLikeDeclaration>(
    KtClassLikeDeclaration::class,
    config = DB.Store.Config.UNIQUE_KEY_VALUE,
    attributeName = "class_id_to_class",
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

    override fun producePairs(entry: IndexFileEntry, elements: List<PsiEntry<KtClassLikeDeclaration>>): List<Pair<String, PsiEntry<KtClassLikeDeclaration>>> {
        return elements.filter { it.element !is KtEnumEntry }.mapNotNull { el ->
            val classId = el.element.getClassId()
            val fqClassName = classId ?: return@mapNotNull null
            fqClassName.asString() to el
        }
    }
}