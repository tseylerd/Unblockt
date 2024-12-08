// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

package tse.unblockt.ls.server.analysys.notifications

import tse.unblockt.ls.protocol.TextDocumentContentChangeEvent
import tse.unblockt.ls.protocol.Uri

interface LsNotificationsService {
    suspend fun handleDocumentChanged(uri: Uri, changes: List<TextDocumentContentChangeEvent>)
    suspend fun handleFileChanged(uri: Uri)
    suspend fun handleDocumentSaved(uri: Uri)
    suspend fun handleFileRenamed(old: Uri, actual: Uri)
    suspend fun handleFileCreated(uri: Uri)
    suspend fun handleFileDeleted(uri: Uri)
    suspend fun handleDocumentOpened(uri: Uri, text: String)
}