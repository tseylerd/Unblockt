// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

@file:Suppress("UnstableApiUsage")

package tse.unblockt.ls.server.analysys.project.module

import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.impl.VirtualFileEnumeration

class LsLibraryScope(
    binaryRoots: Set<VirtualFile>,
): GlobalSearchScope(), VirtualFileEnumeration, LsScope {
    private val allFiles = binaryRoots.flatMap {
        LsModuleScope.getAllVirtualFilesFromRoot(it)
    }.toSet()

    override val allFilesInModule: Set<VirtualFile>
        get() = allFiles

    override fun contains(p0: VirtualFile): Boolean {
        return allFiles.contains(p0)
    }

    override fun isSearchInModuleContent(p0: Module): Boolean = true
    override fun isSearchInLibraries(): Boolean = true

    override fun getFilesIfCollection(): Collection<VirtualFile> = allFiles

    override fun contains(p0: Int): Boolean {
        throw NotImplementedError()
    }

    override fun asArray(): IntArray {
        throw NotImplementedError()
    }
}