// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

package tse.unblockt.ls.server.fs

import com.intellij.mock.MockFileDocumentManagerImpl
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.util.containers.ContainerUtil
import tse.unblockt.ls.protocol.Uri
import tse.unblockt.ls.server.GlobalServerState
import tse.unblockt.ls.server.analysys.AnalysisEntrypoint
import tse.unblockt.ls.server.analysys.LsSession

class LsFileSystem {
    companion object {
        fun instance(): LsFileSystem {
            return ApplicationManager.getApplication().service()
        }
    }

    init {
        GlobalFileState.subscribe(object : GlobalFileState.GlobalFileStateListener {
            override suspend fun changed(uri: Uri) {
            }

            override suspend fun deleted(uri: Uri) {
                cache.remove(uri.asIJUrl)
            }

            override suspend fun created(uri: Uri) {
            }

        }, ApplicationManager.getApplication())
        GlobalServerState.onShutdown(ApplicationManager.getApplication()) {
            cache.clear()
        }
    }

    private val cache = ContainerUtil.createConcurrentWeakMap<String, VirtualFile>()

    fun getModificationStamp(file: VirtualFile): Long {
        return GlobalFileState.modificationStamp(GlobalFileState.IJUrl(file.url)) ?: file.timeStamp
    }

    fun getVirtualFile(uri: Uri): VirtualFile? {
        return getVirtualFileByUrl(uri.asIJUrl)
    }

    fun getVirtualFileByUrl(url: String): VirtualFile? {
        return cache.computeIfAbsent(url) {
            VirtualFileManager.getInstance().findFileByUrl(url)
        }
    }

    class FileDocumentManager: MockFileDocumentManagerImpl(LsSession.DOCUMENT_KEY, { DocumentImpl(it) }) {
        private val fileSystem by lazy {
            instance()
        }

        override fun getCachedDocument(file: VirtualFile): Document? {
            if (!file.isRegularFile) {
                return super.getCachedDocument(file)
            }
            return getDocumentInternal(file)
        }

        override fun getDocument(file: VirtualFile): Document? {
            if (!file.isRegularFile) {
                return super.getDocument(file)
            }
            return getDocumentInternal(file)
        }

        private fun getDocumentInternal(file: VirtualFile): Document? {
            val userData = file.getUserData(LsSession.DOCUMENT_KEY)
            val virtualFileByUrl = fileSystem.getVirtualFileByUrl(file.url) ?: return null
            val doc = userData ?: virtualFileByUrl.getUserData(LsSession.DOCUMENT_KEY)
            val document = doc ?: super.getDocument(virtualFileByUrl) ?: return null
            virtualFileByUrl.putUserData(LsSession.DOCUMENT_KEY, document)
            file.putUserData(LsSession.DOCUMENT_KEY, document)

            if (!AnalysisEntrypoint.isInitialized) {
                return document
            }
            val asUri = file.asUri
            if (!GlobalFileState.isModified(asUri)) {
                return document
            }
            val content = GlobalFileState.actualContent(asUri) ?: return document
            document.setText(content)
            return document
        }
    }
}