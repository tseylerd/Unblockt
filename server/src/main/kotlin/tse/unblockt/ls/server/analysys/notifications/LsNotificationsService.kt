// Copyright 2024 Dmitrii Tseiler. Licensed under the Apache 2.0 Licence

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
    suspend fun handleDocumentClosed(uri: Uri)
}