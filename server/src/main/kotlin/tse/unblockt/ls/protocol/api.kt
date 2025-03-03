// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

package tse.unblockt.ls.protocol

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.apache.logging.log4j.kotlin.logger
import org.jetbrains.kotlin.cli.jvm.compiler.setupIdeaStandaloneExecution
import tse.unblockt.ls.measure
import tse.unblockt.ls.protocol.progress.startProgress
import tse.unblockt.ls.protocol.progress.withProgress
import tse.unblockt.ls.rpc.CancellationType
import tse.unblockt.ls.rpc.RPCMethodCall
import tse.unblockt.ls.rpc.Transport
import tse.unblockt.ls.rpc.jsonRpc
import tse.unblockt.ls.server.project.ProjectImportError.Companion.collectMessagesMultiline
import tse.unblockt.ls.server.threading.LsCallEntrypoint
import tse.unblockt.ls.server.util.Logging
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.seconds

private val ourScope = AtomicReference<CoroutineScope>()

internal val mainScope: CoroutineScope
    get() = ourScope.get()

suspend fun runLanguageServer(name: String, transport: Transport, clientFactory: LanguageClient.Factory, server: (LanguageClient) -> LanguageServer) {
    setupProperties()

    val chan = Channel<RPCMethodCall<*, *>>(Channel.UNLIMITED)
    val receiveChannel = Channel<Any?>(Channel.UNLIMITED)
    val client = clientFactory.create {
        Logging.logger.debug("Sending $it to channel")

        chan.send(it)
        if (!it.isNotification) {
            if (it.responseClazz == Unit::class) {
                Unit
            } else {
                receiveChannel.receive()
            }
        } else {
            null
        }
    }
    val delegate = LsDelegate(name, client, server(client))
    val textDocument = delegate.textDocument
    val workspace = delegate.workspace
    val (start, _) = jsonRpc(transport, chan, receiveChannel) {
        register(null, delegate.initializer)
        register(LanguageServer.TextDocument::class, textDocument)
        register(LanguageServer.Workspace::class, workspace)
        register(LanguageServer.BuildSystem::class, delegate.buildSystem)
        register(LanguageServer.CompletionItem::class, delegate.completionItem)
        register("$", delegate.service)
    }
    start { scope ->
        ourScope.set(scope)
    }
}

private fun setupProperties() {
    if (System.getProperty("java.awt.headless") == null) {
        System.setProperty("java.awt.headless", "true")
    }
    setupIdeaStandaloneExecution()
}

private class LsDelegate(name: String, private val client: LanguageClient, private val server: LanguageServer): LanguageServer by server {
    companion object {
        val ourDefaultCompletionItem = CompletionList(true, listOf(CompletionItem("Dummy")))
    }

    private val locks = LsCallEntrypoint(client)

    override val initializer: InitializerDelegate = InitializerDelegate(name, client, server.initializer)
    override val textDocument: LanguageServer.TextDocument = TextDocumentDelegate(server.textDocument)
    override val buildSystem: LanguageServer.BuildSystem? = server.buildSystem?.let { BuildSystemDelegate(it) }
    override val workspace: LanguageServer.Workspace = WorkspaceDelegate(server.workspace)
    override val service: LanguageServer.Service = ServiceDelegate()
    override val completionItem: LanguageServer.CompletionItem = CompletionItemDelegate(server.completionItem)
    override suspend fun getStatus(): HealthStatusInformation = server.getStatus()

    inner class ServiceDelegate: LanguageServer.Service {
        override suspend fun pollAllRequests(): Boolean {
            locks.write(LsCallEntrypoint.Operation.WriteOperation("pollAllRequests")) { }
            return true
        }

        override suspend fun cancelRequest(params: CancelRequestParams) {
            locks.cancelRequest(params.id.toString(), CancellationType.CLIENT)
        }
    }

    inner class InitializerDelegate(private val name: String, private val client: LanguageClient, private val initializer: LanguageServer.Initializer): LanguageServer.Initializer {
        override val asyncInitializerResponse: InitializationResponse?
            get() = initializer.asyncInitializerResponse

        private var params: InitializationRequestParameters? = null

        override suspend fun shutdown(): Any? {
            locks.shutdown {
                params = null
                initializer.shutdown()
            }
            return null
        }

        override suspend fun exit() {
            initializer.exit()
        }

        override suspend fun initialize(params: InitializationRequestParameters): InitializationResponse {
            val air = initializer.asyncInitializerResponse ?: return locks.initialize {
                with(params) {
                    client.withProgress("Initializing...") {
                        initializer.initialize(params)
                    }
                }
            }

            this.params = params

            return air
        }

        override suspend fun error(errorType: ErrorType) {
            if (params != null) {
                shutdown()
            }
            initializer.error(errorType)
        }

        override suspend fun initialized() {
            if (asyncInitializerResponse != null) {
                val paramsLocal = params ?: throw IllegalStateException("Initialization parameters are null")
                locks.initialize {
                    client.startProgress("Initializing") {
                        initializer.initialize(paramsLocal)
                    }
                }
            }

            locks.write(LsCallEntrypoint.Operation.WriteOperation("initialized")) {
                initializer.initialized()

                mainScope.launch {
                    while (true) {
                        val status = kotlin.runCatching {
                            server.getStatus()
                        }
                        client.unblockt {
                            status {
                                data = when {
                                    status.isSuccess -> status.getOrThrow()
                                    else -> HealthStatusInformation("Internal error", status.exceptionOrNull()?.collectMessagesMultiline() ?: "", HealthStatus.ERROR)
                                }
                            }
                        }
                        delay(1.seconds)
                    }
                }
            }
        }
    }

    inner class WorkspaceDelegate(private val workspace: LanguageServer.Workspace): LanguageServer.Workspace {
        override suspend fun didCreateFiles(params: CreateFilesParams) {
            locks.write(LsCallEntrypoint.Operation.WriteOperation("didCreateFiles")) {
                workspace.didCreateFiles(params)
            }
        }

        override suspend fun didRenameFiles(params: RenameFilesParams) {
            locks.write(LsCallEntrypoint.Operation.WriteOperation("didRenameFiles")) {
                workspace.didRenameFiles(params)
            }
        }

        override suspend fun didChangeWatchedFiles(params: WatchedFilesChangedParams) {
            locks.write(LsCallEntrypoint.Operation.WriteOperation("didChangedWatchedFiles")) {
                workspace.didChangeWatchedFiles(params)
            }
        }

        override suspend fun didDeleteFiles(params: DeleteFilesParams) {
            locks.write(LsCallEntrypoint.Operation.WriteOperation("didDeleteFiles")) {
                workspace.didDeleteFiles(params)
            }
        }

        override suspend fun executeCommand(params: ExecuteCommandParams) {
            locks.write(LsCallEntrypoint.Operation.WriteOperation("executeCommand")) {
                workspace.executeCommand(params)
            }
        }

        override suspend fun rebuildIndexes(params: JobWithProgressParams): Boolean {
            locks.write(LsCallEntrypoint.Operation.WriteOperation("rebuildIndexes")) {
                with(params) {
                    client.withProgress("Rebuilding indexes...") {
                        workspace.rebuildIndexes(params)
                    }
                }
            }
            return true
        }
    }

    inner class BuildSystemDelegate(private val delegate: LanguageServer.BuildSystem): LanguageServer.BuildSystem {
        override suspend fun reload(params: JobWithProgressParams): Boolean {
            locks.write(LsCallEntrypoint.Operation.WriteOperation("Reload")) {
                with(params) {
                    client.withProgress("Reloading project...") {
                        delegate.reload(params)
                    }
                }
            }
            return true
        }
    }

    inner class TextDocumentDelegate(private val delegate: LanguageServer.TextDocument): LanguageServer.TextDocument {
        override suspend fun definition(params: GoToDefinitionParams): Location? {
            return locks.read(LsCallEntrypoint.Operation.ReadOperation.SimpleOperation("Go to definition", true)) {
                measure("definition") {
                    delegate.definition(params)
                }
            }
        }

        override suspend fun hover(params: HoverParameters): HoverResult? {
            return locks.read(LsCallEntrypoint.Operation.ReadOperation.SimpleOperation("Hover", true)) {
                delegate.hover(params)
            }
        }

        override suspend fun didOpen(params: DidOpenTextDocumentParams) {
            return locks.write(LsCallEntrypoint.Operation.WriteOperation("didOpen")) {
                measure("didOpen") {
                    delegate.didOpen(params)
                }
            }
        }

        override suspend fun didClose(params: DidCloseTextDocumentParams) {
            return locks.write(LsCallEntrypoint.Operation.WriteOperation("didClose")) {
                delegate.didClose(params)
            }
        }

        override suspend fun didSave(params: DidSaveTextDocumentParams) {
            return locks.write(LsCallEntrypoint.Operation.WriteOperation("didSave")) {
                measure("didSave") {
                    delegate.didSave(params)
                }
            }
        }

        override suspend fun semanticTokensFull(params: SemanticTokensParams): SemanticTokens? {
            return locks.read(LsCallEntrypoint.Operation.ReadOperation.SimpleOperation("semanticTokensFull", true)) {
                measure("semanticTokens") {
                    delegate.semanticTokensFull(params)
                }
            }
        }

        override suspend fun semanticTokensRange(params: SemanticTokensRangeParams): SemanticTokens? {
            return locks.read(LsCallEntrypoint.Operation.ReadOperation.SimpleOperation("semanticTokensRange", true)) {
                measure("semanticTokensRange") {
                    delegate.semanticTokensRange(params)
                }
            }
        }

        override suspend fun didChange(params: DidChangeTextDocumentParams) {
            return locks.write(LsCallEntrypoint.Operation.WriteOperation("didChange")) {
                measure("didChange") {
                    delegate.didChange(params)
                }
            }
        }

        override suspend fun diagnostic(params: DiagnosticTextDocumentParams): DocumentDiagnosticReport? {
            return locks.read(LsCallEntrypoint.Operation.ReadOperation.SimpleOperation("diagnostic", true)) {
                measure("diagnostic") {
                    delegate.diagnostic(params)
                }
            }
        }

        override suspend fun completion(params: CompletionParams): CompletionList? {
            return locks.read(LsCallEntrypoint.Operation.ReadOperation.CancellableOperation("completion", true, onCancelled = ourDefaultCompletionItem)) {
                measure("completion") {
                    delegate.completion(params)
                }
            }
        }

        override suspend fun signatureHelp(params: SignatureHelpParams): SignatureHelp? {
            return locks.read(LsCallEntrypoint.Operation.ReadOperation.SimpleOperation("signatureHelp", true)) {
                measure("signatureHelp") {
                    delegate.signatureHelp(params)
                }
            }
        }
    }
    
    inner class CompletionItemDelegate(private val delegate: LanguageServer.CompletionItem): LanguageServer.CompletionItem {
        override suspend fun resolve(item: CompletionItem): CompletionItem {
            return locks.read(LsCallEntrypoint.Operation.ReadOperation.SimpleOperation("completionResolve", true)) {
                measure("resolve") {
                    delegate.resolve(item)
                }
            }
        }
    }
}