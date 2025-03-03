// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

package tse.unblockt.ls.server.analysys.index

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import tse.unblockt.ls.server.analysys.index.machines.IndexMachine
import tse.unblockt.ls.server.analysys.index.stub.IndexModel
import kotlin.reflect.KClass

interface LsSourceCodeIndexer {
    companion object {
        fun instance(project: Project): LsSourceCodeIndexer {
            return project.getService(LsSourceCodeIndexer::class.java)
        }
    }

    val builtins: Collection<VirtualFile>

    operator fun <K: Any, V: Any, I: IndexMachine<K, V>> get(clazz: KClass<I>): I

    suspend fun updateIndexes(model: IndexModel)
    suspend fun index(files: Collection<PsiFile>)
}