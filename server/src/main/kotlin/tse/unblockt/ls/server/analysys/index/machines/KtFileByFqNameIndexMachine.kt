// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

package tse.unblockt.ls.server.analysys.index.machines

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClassOwner
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtFile
import tse.unblockt.ls.server.analysys.index.common.IndexFileEntry
import tse.unblockt.ls.server.analysys.storage.PersistentStorage

class KtFileByFqNameIndexMachine(project: Project) : BaseFileByFqNameIndexMachine(project) {
    override val namespace: PersistentStorage.Namespace
        get() = Namespaces.ourKotlinNamespace

    override fun index(entry: IndexFileEntry): List<Pair<String, IndexFileEntry>> {
        if (!entry.isKotlin) {
            return emptyList()
        }

        val packageName = when (entry.psiFile) {
            is KtFile -> entry.psiFile.packageFqName
            is PsiClassOwner -> FqName(entry.psiFile.packageName)
            else -> null
        } ?: return emptyList()
        return listOf(packageName.asString() to entry)
    }
}