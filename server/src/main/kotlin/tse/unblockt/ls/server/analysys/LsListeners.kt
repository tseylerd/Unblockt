// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

package tse.unblockt.ls.server.analysys

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import tse.unblockt.ls.protocol.Uri

class LsListeners {
    companion object{
        fun instance(project: Project): LsListeners {
            return project.service()
        }
    }

    private val fileChangedList = mutableListOf<FileStateListener>()

    val fileChangedListener = object : FileStateListener {
        override suspend fun changed(uri: Uri, content: String) {
            for (fileChangeListener in fileChangedList) {
                fileChangeListener.changed(uri, content)
            }
        }

        override suspend fun changed(uri: Uri) {
            for (fileChangeListener in fileChangedList) {
                fileChangeListener.changed(uri)
            }
        }

        override suspend fun opened(uri: Uri, content: String) {
            for (fileStateListener in fileChangedList) {
                fileStateListener.opened(uri, content)
            }
        }

        override suspend fun closed(uri: Uri) {
            for (fileStateListener in fileChangedList) {
                fileStateListener.closed(uri)
            }
        }

        override suspend fun saved(uri: Uri) {
            for (fileChangeListener in fileChangedList) {
                fileChangeListener.saved(uri)
            }
        }

        override suspend fun created(uri: Uri) {
            for (fileChangeListener in fileChangedList) {
                fileChangeListener.created(uri)
            }
        }

        override suspend fun deleted(uri: Uri) {
            for (fileChangeListener in fileChangedList) {
                fileChangeListener.deleted(uri)
            }
        }

        override suspend fun renamed(old: Uri, actual: Uri) {
            for (fileChangeListener in fileChangedList) {
                fileChangeListener.renamed(old, actual)
            }
        }
    }

    fun listen(fileChanged: FileStateListener) {
        fileChangedList += fileChanged
    }

    interface FileStateListener {
        suspend fun opened(uri: Uri, content: String) {}
        suspend fun closed(uri: Uri) {}

        suspend fun changed(uri: Uri, content: String) {}
        suspend fun changed(uri: Uri) {}
        suspend fun deleted(uri: Uri) {}
        suspend fun created(uri: Uri) {}
        suspend fun saved(uri: Uri) {}
        suspend fun renamed(old: Uri, actual: Uri) {}
    }
}