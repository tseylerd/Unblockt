// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

package tse.unblockt.ls.server.analysys.index

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.impl.jar.CoreJarFileSystem
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.kotlin.cli.jvm.modules.CoreJrtFileSystem
import tse.unblockt.ls.protocol.Uri
import tse.unblockt.ls.server.analysys.LsListeners
import tse.unblockt.ls.server.analysys.files.isKotlin
import tse.unblockt.ls.server.fs.LsFileSystem
import tse.unblockt.ls.server.fs.asIJUrl

class LsPsiCache(project: Project) {
    companion object {
        fun instance(project: Project): LsPsiCache {
            return project.service()
        }
    }

    init {
        LsListeners.instance(project).listen(object : LsListeners.FileStateListener {
            override suspend fun deleted(uri: Uri) {
                if (!uri.isKotlin) {
                    return
                }
                cache.remove(uri.asIJUrl)
            }

            override suspend fun renamed(old: Uri, actual: Uri) {
                if (!old.isKotlin) {
                    return
                }
                cache.remove(old.asIJUrl)
            }
        })
    }

    private val cache = ContainerUtil.createConcurrentSoftKeySoftValueMap<String, PsiFile?>()
    private val psiManager = PsiManager.getInstance(project)
    private val fileManager = LsFileSystem.instance()
    private val stubCache = LsStubCache.instance(project)

    operator fun get(url: String): PsiFile? {
        return cache.computeIfAbsent(url) {
            val vFile = fileManager.getVirtualFileByUrl(url) ?: return@computeIfAbsent null
            psiManager.findFile(vFile)
        }
    }

    operator fun get(virtualFile: VirtualFile): PsiFile? {
        if (virtualFile.fileSystem is CoreJarFileSystem || virtualFile.fileSystem is CoreJrtFileSystem) {
            return stubCache[virtualFile]?.psi ?: psiManager.findFile(virtualFile)
        }
        return cache.computeIfAbsent(virtualFile.url) {
            psiManager.findFile(virtualFile)
        }
    }
}