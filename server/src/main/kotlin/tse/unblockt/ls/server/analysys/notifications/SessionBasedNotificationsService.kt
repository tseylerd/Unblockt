// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

package tse.unblockt.ls.server.analysys.notifications

import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.project.Project
import org.apache.logging.log4j.kotlin.logger
import tse.unblockt.ls.protocol.TextDocumentContentChangeEvent
import tse.unblockt.ls.protocol.Uri
import tse.unblockt.ls.server.analysys.LsListeners
import tse.unblockt.ls.server.analysys.files.Offsets
import tse.unblockt.ls.server.analysys.files.isFolder
import tse.unblockt.ls.server.analysys.files.isSupportedByLanguageServer
import tse.unblockt.ls.server.fs.LsFileManager
import tse.unblockt.ls.server.fs.asPath
import tse.unblockt.ls.server.fs.uri
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.*

class SessionBasedNotificationsService(private val project: Project, private val filesManager: LsFileManager): LsNotificationsService {
    override suspend fun handleDocumentChanged(uri: Uri, changes: List<TextDocumentContentChangeEvent>) {
        if (!uri.isSupportedByLanguageServer) {
            return
        }
        val document = filesManager.getDocument(uri)
        if (document == null) {
            logger.warn("Failed to find document for $uri")
            return
        }
        val newContent = apply(document, changes)
        val instance = LsListeners.instance(project)
        instance.fileChangedListener.changed(uri, newContent)
    }

    override suspend fun handleFileChanged(uri: Uri) {
        if (!uri.isSupportedByLanguageServer) {
            return
        }
        val document = filesManager.getDocument(uri)
        if (document == null) {
            logger.warn("Failed to find document for $uri")
            return
        }
        LsListeners.instance(project).fileChangedListener.changed(uri)
    }

    override suspend fun handleDocumentOpened(uri: Uri, text: String) {
        if (!uri.isSupportedByLanguageServer) {
            return
        }
        LsListeners.instance(project).fileChangedListener.opened(uri, text)
    }

    override suspend fun handleDocumentClosed(uri: Uri) {
        if (!uri.isSupportedByLanguageServer) {
            return
        }

        LsListeners.instance(project).fileChangedListener.closed(uri)
    }

    override suspend fun handleFileDeleted(uri: Uri) {
        val asPath = uri.asPath()
        if (!asPath.isDirectory() && uri.isSupportedByLanguageServer) {
            LsListeners.instance(project).fileChangedListener.deleted(uri)
            return
        }
        val allSupportedFiles = allSupportedFiles(asPath)
        for (file in allSupportedFiles) {
            LsListeners.instance(project).fileChangedListener.deleted(file.uri)
        }
    }

    override suspend fun handleDocumentSaved(uri: Uri) {
        if (!uri.isSupportedByLanguageServer) {
            return
        }

        LsListeners.instance(project).fileChangedListener.saved(uri)
    }

    override suspend fun handleFileRenamed(old: Uri, actual: Uri) {
        val path = actual.asPath()
        val instance = LsListeners.instance(project)
        if (Files.isDirectory(path)) {
            val allEntries = allSupportedFiles(path)
            val oldPath = old.asPath()
            for (entry in allEntries) {
                val relativePath = entry.relativeTo(path)
                val was = oldPath.resolve(relativePath)
                instance.fileChangedListener.renamed(was.uri, entry.uri)
            }
        } else {
            if (actual.isSupportedByLanguageServer) {
                instance.fileChangedListener.renamed(old, actual)
            }
        }
    }

    override suspend fun handleFileCreated(uri: Uri) {
        if (uri.isFolder || uri.isSupportedByLanguageServer) {
            LsListeners.instance(project).fileChangedListener.created(uri)
        }
    }

    @OptIn(ExperimentalPathApi::class)
    private fun allSupportedFiles(path: Path) = path.walk(PathWalkOption.INCLUDE_DIRECTORIES).filter {
        it.isDirectory() || it.isSupportedByLanguageServer
    }

    private fun apply(document: Document, events: List<TextDocumentContentChangeEvent>): String {
        val copy = DocumentImpl(document.text, true)
        for (event in events) {
            val tr = Offsets.rangeToTextRange(event.range, copy)
            copy.replaceString(tr.startOffset, tr.endOffset, event.text)
        }
        return copy.text
    }
}