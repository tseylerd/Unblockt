// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

package tse.unblockt.ls.server.analysys.service

import com.intellij.openapi.editor.Document
import com.intellij.psi.PsiFile
import tse.unblockt.ls.protocol.*
import tse.unblockt.ls.server.GlobalServerState
import tse.unblockt.ls.server.analysys.AnalysisEntrypoint
import tse.unblockt.ls.server.analysys.completion.LsCompletionMachine
import tse.unblockt.ls.server.analysys.declaration.LsGoToDeclarationProvider
import tse.unblockt.ls.server.analysys.higlighting.LsHighlightingProvider
import tse.unblockt.ls.server.analysys.notifications.LsNotificationsService
import tse.unblockt.ls.server.analysys.parameters.NoSessionParameterHintService
import tse.unblockt.ls.server.analysys.parameters.ParameterHintsService
import tse.unblockt.ls.server.analysys.project.LsProjectService
import tse.unblockt.ls.server.analysys.project.NoSessionProjectService
import tse.unblockt.ls.server.analysys.project.build.LsBuildService
import tse.unblockt.ls.server.client.ClientLog
import tse.unblockt.ls.server.fs.LsFileManager
import tse.unblockt.ls.server.project.ProjectImportError
import tse.unblockt.ls.server.threading.Cancellable
import tse.unblockt.ls.server.util.ServiceInformation
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

internal class NoSessionServices(
    root: Path,
    storagePath: Path,
    globalStoragePath: Path,
    private val error: ProjectImportError
): LsServices {
    init {
        GlobalServerState.onInitialized(this) {
            tse.unblockt.ls.server.client.error(ClientLog.GRADLE, error.description)
        }
    }

    override val filesManager: LsFileManager = NoSessionFileManager()
    override val goToDeclarationProvider: LsGoToDeclarationProvider = NoSessionGTDProvider()
    override val completionMachine: LsCompletionMachine = NoSessionCompletionMachine()
    override val highlightingProvider: LsHighlightingProvider = NoSessionHighlightingProvider()
    override val notificationsService: NoSessionNotificationsService = NoSessionNotificationsService()
    override val buildService: LsBuildService = NoSessionBuildService(root, notificationsService)
    override val parameterHintsService: ParameterHintsService = NoSessionParameterHintService()
    override val projectService: LsProjectService = NoSessionProjectService()

    override val health: HealthStatusInformation
        get() = HealthStatusInformation(
            text = memoryMessage(),
            message = error.description,
            status = HealthStatus.ERROR
        )

    override val serviceInformation: ServiceInformation = ServiceInformation(storagePath, globalStoragePath)

    override suspend fun cleanup() {
    }

    override fun dispose() {
    }

    private class NoSessionFileManager: LsFileManager {
        override fun getPsiFile(uri: Uri): PsiFile? = null
        override fun getDocument(uri: Uri): Document? = null
    }

    private class NoSessionGTDProvider: LsGoToDeclarationProvider {
        override fun resolve(uri: Uri, location: Position): Location? = null
    }

    private class NoSessionCompletionMachine: LsCompletionMachine {
        context(Cancellable)
        override suspend fun launchCompletion(params: CompletionParams): CompletionList = CompletionList(false, emptyList())
        override fun resolve(item: CompletionItem): CompletionItem = item
    }

    private class NoSessionHighlightingProvider: LsHighlightingProvider {
        context(Cancellable)
        override fun diagnostics(uri: Uri): DocumentDiagnosticReport? = null
        context(Cancellable)
        override fun tokens(uri: Uri): SemanticTokens? = null
        context(Cancellable)
        override fun tokensRange(uri: Uri, range: Range): SemanticTokens? = null
    }

    private class NoSessionBuildService(private val root: Path, private val notificationsService: NoSessionNotificationsService): LsBuildService {
        override val health: HealthStatusInformation
            get() = throw UnsupportedOperationException()

        override suspend fun reload() {
            AnalysisEntrypoint.init(root, AnalysisEntrypoint.services.serviceInformation.storagePath, AnalysisEntrypoint.services.serviceInformation.globalStoragePath)

            for ((key, value) in notificationsService.openedMap) {
                AnalysisEntrypoint.services.notificationsService.handleDocumentOpened(key, value)
            }

            for ((key, values) in notificationsService.changesMap) {
                AnalysisEntrypoint.services.notificationsService.handleDocumentChanged(key, values)
            }
            GlobalServerState.initialized()
        }
    }

    internal class NoSessionNotificationsService: LsNotificationsService {
        val changesMap = ConcurrentHashMap<Uri, MutableList<TextDocumentContentChangeEvent>>()
        val openedMap = ConcurrentHashMap<Uri, String>()
        override suspend fun handleDocumentChanged(uri: Uri, changes: List<TextDocumentContentChangeEvent>) {
            changesMap.computeIfAbsent(uri) { mutableListOf() }.addAll(changes)
        }

        override suspend fun handleFileChanged(uri: Uri) {
        }

        override suspend fun handleFileDeleted(uri: Uri) {
            changesMap.remove(uri)
        }

        override suspend fun handleDocumentOpened(uri: Uri, text: String) {
            openedMap[uri] = text
        }

        override suspend fun handleDocumentClosed(uri: Uri) {
            openedMap.remove(uri)
        }

        override suspend fun handleDocumentSaved(uri: Uri) {
            changesMap.remove(uri)
        }

        override suspend fun handleFileRenamed(old: Uri, actual: Uri) {
            if (!changesMap.containsKey(old)) {
                return
            }
            changesMap[actual] = changesMap[old]!!
            changesMap.remove(old)
        }

        override suspend fun handleFileCreated(uri: Uri) {
        }
    }
}