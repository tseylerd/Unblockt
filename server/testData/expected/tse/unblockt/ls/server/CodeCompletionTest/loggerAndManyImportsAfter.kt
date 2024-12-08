package tse.Unblockt.ls.server

import tse.Unblockt.ls.server.analysys.AnalysisEntrypoint
import kotlinx.coroutines.coroutineScope
import tse.Unblockt.ls.server.analysys.files.isKotlin
import kotlin.system.exitProcess
import tse.unblockt.ls.server.threading.Cancellable
import tse.Unblockt.ls.server.analysys.files.isSupportedByLanguageServer
import org.apache.logging.log4j.kotlin.logger
import tse.Unblockt.ls.protocol.*
import java.nio.file.Paths

class KotlinLanguageServer(client: LanguageClient) : LanguageServer {
    companion object {
        val DEFAULT_INITIALIZATION_RESPONSE = InitializationResponse(
            ServerCapabilities(
                positionEncoding = PositionEncodingKind.UTF16,
                hoverProvider = true,
                documentHighlightProvider = false,
                semanticTokensProvider = SemanticTokensOptions(
                    SemanticTokensLegend(
                        tokenTypes = SemanticTokenType.entries.map { it.schemaName },
                        tokenModifiers = SemanticTokenModifier.entries.map { it.schemaName }
                    ),
                    range = true,
                    full = FullSemanticTokensOptions(
                        false
                    )
                ),
                textDocumentSync = TextDocumentSyncOptions(
                    openClose = true,
                    change = TextDocumentSyncKind.INCREMENTAL,
                    save = true
                ),
                definitionProvider = true,
                workspace = WorkspaceCapabilities(
                    fileOperations = FileOperationsCapabilities(
                        didCreate = FileOperationRegistrationOptions(
                            filters = listOf(
                                FileOperationFilter(
                                    scheme = "file",
                                    pattern = FileOperationPattern(
                                        glob = "**/*",
                                        matches = FileOperationPatternKind.FILE,
                                        options = null
                                    )
                                )
                            )
                        ),
                        didRename = FileOperationRegistrationOptions(
                            filters = listOf(
                                FileOperationFilter(
                                    scheme = "file",
                                    pattern = FileOperationPattern(
                                        glob = "**/*",
                                        matches = FileOperationPatternKind.FILE,
                                        options = null
                                    )
                                )
                            )
                        ),
                        didDelete = FileOperationRegistrationOptions(
                            filters = listOf(
                                FileOperationFilter(
                                    scheme = "file",
                                    pattern = FileOperationPattern(
                                        glob = "**/*",
                                        matches = FileOperationPatternKind.FILE,
                                        options = null
                                    )
                                )
                            )
                        )
                    )
                ),
                diagnosticProvider = DiagnosticOptions(
                    "kt",
                    interFileDependencies = true,
                    workspaceDiagnostics = false
                ),
                completionProvider = CompletionOptions(
                    triggerCharacters = listOf("."),
                    allCommitCharacters = null,
                    resolveProvider = true,
                    completionItem = CompletionItemOptions(
                        labelDetailsSupport = true
                    )
                ),
                executeCommandProvider = ExecuteCommandOptions(
                    commands = emptyList()
                ),
                signatureHelpProvider = SignatureHelpOptions(
                    triggerCharacters = listOf("("),
                    retriggerCharacters = listOf(",")
                )
            ),
            ServerInfo(
                name = ProductInfo.NAME,
                version = ProductInfo.VERSION
            )
        )
    }

    override val initializer: LanguageServer.Initializer = Initializer(client)
    override val textDocument: LanguageServer.TextDocument = KotlinTextDocument(client)
    override val workspace: LanguageServer.Workspace = KotlinWorkspace(client)
    override val buildSystem: LanguageServer.BuildSystem = KotlinBuildSystem()
    override val service: LanguageServer.Service
        get() = throw NotImplementedError()
    override val completionItem: LanguageServer.CompletionItem = KotlinCompletionItem()

    override suspend fun getStatus(): HealthStatusInformation = AnalysisEntrypoint.services.health

    private class KotlinBuildSystem: LanguageServer.BuildSystem {
        override suspend fun reload(params: JobWithProgressParams): Boolean {
            AnalysisEntrypoint.services.buildService.reload()
            return true
        }
    }

    private class Initializer(@Suppress("unused") private val client: LanguageClient) : LanguageServer.Initializer {
        override suspend fun shutdown() {
            AnalysisEntrypoint.shutdown()
            exitProcess(0)
        }

        override suspend fun initialize(params: InitializationRequestParameters): InitializationResponse {
            logger
            AnalysisEntrypoint.init(Paths.get(params.rootPath))
            return DEFAULT_INITIALIZATION_RESPONSE
        }

        override suspend fun initialized() {
            AnalysisEntrypoint.services.onInitialized()
        }
    }

    private class KotlinTextDocument(@Suppress("unused") private val client: LanguageClient): LanguageServer.TextDocument {
        override suspend fun definition(params: GoToDefinitionParams): Location? {
            if (!params.textDocument.uri.isKotlin) {
                return null
            }

            return AnalysisEntrypoint.goToDeclarationProvider.resolve(params.textDocument.uri, params.position)
        }

        override suspend fun hover(params: HoverParameters): HoverResult? {
            return null
        }

        override suspend fun didOpen(params: DidOpenTextDocumentParams) {
            AnalysisEntrypoint.services.notificationsService.handleDocumentOpened(params.textDocument.uri, params.textDocument.text)
        }

        override suspend fun didChange(params: DidChangeTextDocumentParams) {
            if (!params.textDocument.uri.isSupportedByLanguageServer) {
                return
            }

            AnalysisEntrypoint.services.notificationsService.handleDocumentChanged(params.textDocument.uri, params.contentChanges)
        }

        override suspend fun diagnostic(params: DiagnosticTextDocumentParams): DocumentDiagnosticReport? {
            if (!params.textDocument.uri.isKotlin) {
                return null
            }
            return coroutineScope {
                with(Cancellable()) {
                    AnalysisEntrypoint.highlightingProvider.diagnostics(params.textDocument.uri)
                }
            }
        }

        override suspend fun signatureHelp(params: SignatureHelpParams): SignatureHelp? {
            if (!params.textDocument.uri.isKotlin) {
                return null
            }
            return AnalysisEntrypoint.services.parameterHintsService.analyzePosition(params.textDocument.uri, params.position)
        }

        override suspend fun completion(params: CompletionParams): CompletionList? {
            if (!params.textDocument.uri.isKotlin) {
                return null
            }
            val machine = AnalysisEntrypoint.completionMachine
            val allItems = coroutineScope {
                with(Cancellable()) {
                    machine.launchCompletion(params)
                }
            }
            return allItems
        }

        override suspend fun didClose(params: DidCloseTextDocumentParams) {
        }

        override suspend fun didSave(params: DidSaveTextDocumentParams) {
            AnalysisEntrypoint.services.notificationsService.handleDocumentSaved(params.textDocument.uri)
        }

        override suspend fun semanticTokensFull(params: SemanticTokensParams): SemanticTokens? {
            return coroutineScope {
                with(Cancellable()) {
                    AnalysisEntrypoint.highlightingProvider.tokens(params.textDocument.uri)
                }
            }
        }

        override suspend fun semanticTokensRange(params: SemanticTokensRangeParams): SemanticTokens? {
            return coroutineScope {
                with(Cancellable()) {
                    AnalysisEntrypoint.highlightingProvider.tokensRange(params.textDocument.uri, params.range)
                }
            }
        }
    }

    private class KotlinCompletionItem: LanguageServer.CompletionItem {
        override suspend fun resolve(item: CompletionItem): CompletionItem {
            return AnalysisEntrypoint.completionMachine.resolve(item)
        }
    }

    private class KotlinWorkspace(@Suppress("unused") private val client: LanguageClient): LanguageServer.Workspace {
        override suspend fun didCreateFiles(params: CreateFilesParams) {
            val instance = AnalysisEntrypoint.services.notificationsService
            for (file in params.files) {
                instance.handleFileCreated(file.uri)
            }
        }

        override suspend fun didRenameFiles(params: RenameFilesParams) {
            val notificationsService = AnalysisEntrypoint.services.notificationsService
            for (file in params.files) {
                notificationsService.handleFileRenamed(file.oldUri, file.newUri)
            }
        }

        override suspend fun didChangedWatchedFiles(params: WatchedFilesChangedParams) {
            val notificationsService = AnalysisEntrypoint.services.notificationsService
            for (event in params.files) {
                when (event.type) {
                    FileChangeType.CREATED -> notificationsService.handleFileCreated(event.uri)
                    FileChangeType.CHANGED -> {
                        notificationsService.handleFileChanged(event.uri)
                        notificationsService.handleDocumentSaved(event.uri)
                    }
                    FileChangeType.DELETED -> notificationsService.handleFileDeleted(event.uri)
                }
            }
        }

        override suspend fun didDeleteFiles(params: DeleteFilesParams) {
            didChangedWatchedFiles(
                WatchedFilesChangedParams(
                    files = params.files.map { WatchedFileChangeEvent(it.uri, FileChangeType.DELETED) }
                )
            )
        }

        override suspend fun executeCommand(params: ExecuteCommandParams) {
        }

        override suspend fun rebuildIndexes(params: JobWithProgressParams): Boolean {
            AnalysisEntrypoint.services.projectService.rebuildIndexes()
            return true
        }
    }
}
