// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

package tse.unblockt.ls.server

import com.intellij.openapi.util.TextRange
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import org.apache.logging.log4j.io.IoBuilder
import org.apache.logging.log4j.kotlin.logger
import org.jetbrains.kotlin.psi.KtFile
import tse.unblockt.ls.protocol.*
import tse.unblockt.ls.server.analysys.AnalysisEntrypoint
import tse.unblockt.ls.server.analysys.completion.changeFile
import tse.unblockt.ls.server.analysys.completion.imports.PsiImportManager
import tse.unblockt.ls.server.analysys.files.isKotlin
import tse.unblockt.ls.server.analysys.files.isSupportedByLanguageServer
import tse.unblockt.ls.server.threading.Cancellable
import java.io.PrintStream
import java.nio.file.Paths
import java.util.*
import kotlin.system.exitProcess

class KotlinLanguageServer(client: LanguageClient) : LanguageServer {
    companion object {
        private val SUPPORTED_FILES_FILTERS = listOf(
            FileOperationFilter(
                scheme = "file",
                pattern = FileOperationPattern(
                    glob = "**/*.kt",
                    matches = FileOperationPatternKind.FILE,
                    options = null
                )
            ),
            FileOperationFilter(
                scheme = "file",
                pattern = FileOperationPattern(
                    glob = "**/*.kts",
                    matches = FileOperationPatternKind.FILE,
                    options = null
                )
            ),
            FileOperationFilter(
                scheme = "file",
                pattern = FileOperationPattern(
                    glob = "**",
                    matches = FileOperationPatternKind.FOLDER,
                    options = null
                )
            )
        )

        val DEFAULT_INITIALIZATION_RESPONSE = InitializationResponse(
            ServerCapabilities(
                positionEncoding = PositionEncodingKind.UTF16,
                hoverProvider = false,
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
                            filters = SUPPORTED_FILES_FILTERS,
                        ),
                        didRename = FileOperationRegistrationOptions(
                            filters = SUPPORTED_FILES_FILTERS
                        ),
                        didDelete = FileOperationRegistrationOptions(
                            filters = SUPPORTED_FILES_FILTERS
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
                    commands = listOf(Commands.handleInsert.command)
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

    override suspend fun getStatus(): HealthStatusInformation {
        return AnalysisEntrypoint.health
    }

    private class KotlinBuildSystem: LanguageServer.BuildSystem {
        override suspend fun reload(params: JobWithProgressParams): Boolean {
            AnalysisEntrypoint.services.buildService.reload()
            return true
        }
    }

    private class Initializer(@Suppress("unused") private val client: LanguageClient) : LanguageServer.Initializer {
        override val asyncInitializerResponse: InitializationResponse
            get() = DEFAULT_INITIALIZATION_RESPONSE

        override suspend fun shutdown(): Any? {
            logger.info("Received shutdown request")

            GlobalServerState.shutdown()
            return null
        }

        override suspend fun exit() {
            logger.info("Received exit request")

            exitProcess(0)
        }

        override suspend fun error(errorType: ErrorType) {
            if (client.data.name != "test") {
                exitProcess(1)
            } else {
                throw IllegalStateException("Failed to parse input")
            }
        }

        override suspend fun initialize(params: InitializationRequestParameters): InitializationResponse {
            logger.info("Received initialization request")
            logger.info("Client: ${client.data.name}")

            if (client.data.name != "test") {
                configureStdOut()
            }

            val rootPath = Paths.get(params.rootPath)
            val workspaceStoragePath = params.initializationOptions?.storagePath?.let { Paths.get(it) } ?: rootPath.resolve(".unblockt").resolve("local")
            val globalStoragePath = params.initializationOptions?.globalStoragePath?.let { Paths.get(it).resolve(".unblockt").resolve("global") } ?: rootPath.resolve(".unblockt").resolve("global")
            logger.info("Workspace storage path: $workspaceStoragePath")
            logger.info("Global storage path: $globalStoragePath")

            AnalysisEntrypoint.init(rootPath, workspaceStoragePath, globalStoragePath)
            logger.info("Initialization finished")

            return DEFAULT_INITIALIZATION_RESPONSE
        }

        override suspend fun initialized() {
            logger.info("Initialized notification received")
            GlobalServerState.initialized()

            logger.info("Register didChangeWatchFiles capability")
            client.registerCapability {
                data = RegistrationParams(
                    registrations = listOf(
                        Registration(
                            id = UUID.randomUUID().toString(),
                            method = "workspace/didChangeWatchedFiles",
                            registerOptions = RegistrationOptions.DidChangeWatchedFilesRegistrationOptions(
                                watchers = listOf(
                                    FileSystemWatcher(
                                        globPattern = "**/*.{kt,kts}",
                                    )
                                )
                            )
                        )
                    )
                )
            }
            logger.info("Requesting tokens refresh")
            client.workspace {
                semanticTokens {
                    refresh {}
                }
            }

            logger.info("Requesting diagnostics refresh")
            client.workspace {
                diagnostic {
                    refresh {}
                }
            }
        }

        private fun configureStdOut() {
            System.setOut(PrintStream(IoBuilder.forLogger("stdout").buildOutputStream()))
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
            AnalysisEntrypoint.services.notificationsService.handleDocumentClosed(params.textDocument.uri)
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

        override suspend fun didChangeWatchedFiles(params: WatchedFilesChangedParams) {
            val notificationsService = AnalysisEntrypoint.services.notificationsService
            val set = params.changes.toSet()
            for (event in set) {
                when (event.type) {
                    FileChangeType.CREATED -> notificationsService.handleFileCreated(event.uri)
                    FileChangeType.CHANGED -> notificationsService.handleFileChanged(event.uri)
                    FileChangeType.DELETED -> notificationsService.handleFileDeleted(event.uri)
                }
            }
        }

        override suspend fun didDeleteFiles(params: DeleteFilesParams) {
            didChangeWatchedFiles(
                WatchedFilesChangedParams(
                    changes = params.files.map { WatchedFileChangeEvent(it.uri, FileChangeType.DELETED) }
                )
            )
        }

        override suspend fun executeCommand(params: ExecuteCommandParams) {
            val command = params.command
            if (command != Commands.handleInsert.command) {
                return
            }

            val args = params.arguments!!.first()
            val compParams = Json.decodeFromJsonElement<AdditionalCompletionData>(args)
            val psiFile = AnalysisEntrypoint.filesManager.getPsiFile(compParams.document) as? KtFile ?: return
            val shortenReference = compParams.shortenReference
            val edits = if (shortenReference != null) {
                val shortenReferences = PsiImportManager.shortenReferences(
                    psiFile,
                    TextRange.create(compParams.start, compParams.start + shortenReference.packageName.length + shortenReference.shortenName.length + 1)
                )
                shortenReferences?.let { it.forImports + it.shortenEdit }
            } else {
                val copy = changeFile(psiFile) { it }
                listOfNotNull(PsiImportManager.optimizedImports(copy))
            } ?: emptyList()
            if (edits.isEmpty()) {
                return
            }
            client.workspace {
                applyEdit {
                    data = ApplyEditsParams(
                        edit = WorkspaceEdit(mapOf(compParams.document to edits))
                    )
                }
            }
        }

        override suspend fun rebuildIndexes(params: JobWithProgressParams): Boolean {
            AnalysisEntrypoint.services.projectService.rebuildIndexes()
            return true
        }
    }
}