// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

@file:Suppress("UnstableApiUsage")

package tse.unblockt.ls.server.analysys.project.module

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.impl.VirtualFileEnumeration
import tse.unblockt.ls.protocol.Uri
import tse.unblockt.ls.server.analysys.LsListeners
import tse.unblockt.ls.server.fs.LsFileSystem
import tse.unblockt.ls.server.fs.asIJUrl
import tse.unblockt.ls.server.fs.asPath
import java.nio.file.Path

class LsModuleScope(
    project: Project,
    initialRoots: Set<VirtualFile>,
    private val pathRoots: Set<Path>
): GlobalSearchScope(), VirtualFileEnumeration, LsScope {
    companion object {
        fun getAllVirtualFilesFromRoot(
            root: VirtualFile,
        ): Collection<VirtualFile> {
            val files = mutableSetOf<VirtualFile>()
            files.add(root)
            VfsUtilCore.iterateChildrenRecursively(
                root,
                { true },
                { virtualFile ->
                    files.add(virtualFile)
                    true
                }
            )
            return files
        }
    }

    private val fileSystem = LsFileSystem.instance()
    private val allFiles = initialRoots.flatMap {
        getAllVirtualFilesFromRoot(it)
    }.toMutableSet()

    internal val roots = initialRoots.toMutableSet()

    override val allFilesInModule: Set<VirtualFile>
        get() = allFiles

    init {
        LsListeners.instance(project).listen(object : LsListeners.FileStateListener {
            override suspend fun created(uri: Uri) {
                val vFile = fileSystem.getVirtualFile(uri) ?: return
                val asPath = uri.asPath()
                if (VfsUtilCore.isUnder(vFile, roots) || pathRoots.any { asPath.startsWith(it) }) {
                    allFiles += vFile
                }
            }

            override suspend fun renamed(old: Uri, actual: Uri) {
                val oldUrl = old.asIJUrl
                val vFile = allFiles.find { it.url == oldUrl }
                if (vFile != null) {
                    allFiles -= vFile
                }

                val actualFile = fileSystem.getVirtualFile(actual) ?: return
                if (VfsUtilCore.isUnder(actualFile, roots)) {
                    allFiles += actualFile
                }
            }

            override suspend fun deleted(uri: Uri) {
                val vFile = fileSystem.getVirtualFile(uri) ?: return
                if (contains(vFile)) {
                    allFiles.remove(vFile)
                }
            }
        })
    }

    override fun contains(p0: VirtualFile): Boolean {
        return p0 in allFiles
    }

    override fun isSearchInModuleContent(p0: Module): Boolean {
        return true
    }

    override fun isSearchInLibraries(): Boolean = false

    override fun getFilesIfCollection(): Collection<VirtualFile> = allFiles

    override fun contains(p0: Int): Boolean {
        throw NotImplementedError()
    }

    override fun asArray(): IntArray {
        throw NotImplementedError()
    }
}