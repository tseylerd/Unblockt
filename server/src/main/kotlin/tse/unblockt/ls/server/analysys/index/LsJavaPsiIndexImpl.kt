// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

package tse.unblockt.ls.server.analysys.index

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import tse.unblockt.ls.server.analysys.index.machines.JavaClassIdToClassIndexMachine
import tse.unblockt.ls.server.analysys.index.machines.JavaPackageToTopLevelClassIndexMachine
import tse.unblockt.ls.server.analysys.index.machines.Namespaces
import tse.unblockt.ls.server.analysys.storage.PersistentStorage

class LsJavaPsiIndexImpl(private val project: Project) : LsJavaPsiIndex {
    private val indexer by lazy {
        LsSourceCodeIndexer.instance(project)
    }
    private val storage by lazy {
        PersistentStorage.instance(project)
    }

    override fun getAllClasses(filter: (PsiElement) -> Boolean): Sequence<PsiClass> {
        return storage.getSequenceOfValues(Namespaces.ourJavaNamespace, indexer[JavaClassIdToClassIndexMachine::class].attribute)
            .map {
                it.element
            }.filter(filter)
    }

    override fun getTopLevelClasses(): Sequence<PsiClass> {
        val machine = indexer[JavaPackageToTopLevelClassIndexMachine::class]
        return storage.getSequenceOfValues(Namespaces.ourJavaNamespace, machine.attribute).map { it.element }
    }
}