// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

package tse.unblockt.ls.server.analysys.index.machines

import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.psi.KtClassLikeDeclaration
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.psiUtil.isTopLevelKtOrJavaMember
import tse.unblockt.ls.server.analysys.index.common.IndexFileEntry
import tse.unblockt.ls.server.analysys.index.model.PsiEntry
import tse.unblockt.ls.server.analysys.storage.PersistentStorage

class KtPackageToTopLevelClassIndexMachine(project: Project): PsiIndexMachine<String, KtClassLikeDeclaration>(
    KtClassLikeDeclaration::class,
    attributeName = "package_to_top_level_class",
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
        return elements.filter { el ->
            el.element.isTopLevelKtOrJavaMember()
        }.map { el ->
            val packageName = el.element.containingKtFile.packageFqName.asString()
            packageName to el
        }
    }
}