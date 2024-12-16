// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

package tse.unblockt.ls.server.analysys.index

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.stubs.PsiFileStub
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.kotlin.analysis.decompiler.stub.file.ClsKotlinBinaryClassCache
import tse.unblockt.ls.server.analysys.index.stub.LanguageMachinery

class LsStubCache(project: Project) {
    companion object {
        fun instance(project: Project): LsStubCache {
            return project.service()
        }
    }

    private val cache = ContainerUtil.createConcurrentWeakMap<VirtualFile, PsiFileStub<*>?>()
    private val kotlinMachinery = LanguageMachinery.Kotlin.instance(project)
    private val javaMachinery = LanguageMachinery.Java.instance(project)
    private val binaryClassCache = ClsKotlinBinaryClassCache()

    operator fun get(virtualFile: VirtualFile, isBuiltIns: Boolean): PsiFileStub<*>? {
        return cache.computeIfAbsent(virtualFile) {
            when {
                isBuiltIns -> kotlinMachinery.createBuiltInsStub(virtualFile)
                else -> kotlinMachinery.build(virtualFile, binaryClassCache) ?: javaMachinery.build(virtualFile, binaryClassCache)
            }
        }
    }

    operator fun get(virtualFile: VirtualFile): PsiFileStub<*>? {
        return cache[virtualFile]
    }
}