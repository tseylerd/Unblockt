// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

package tse.unblockt.ls.server.analysys.index

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement

interface LsJavaPsiIndex {
    companion object {
        fun instance(project: Project): LsJavaPsiIndex = project.getService(LsJavaPsiIndex::class.java)
    }

    val builtIns: Collection<VirtualFile>
        get() = emptyList()

    fun getAllClasses(filter: (PsiElement) -> Boolean = { true }): Sequence<PsiClass>
    fun getTopLevelClasses(): Sequence<PsiClass>
}