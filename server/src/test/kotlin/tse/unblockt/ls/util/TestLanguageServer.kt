// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

package tse.unblockt.ls.util

import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import tse.unblockt.ls.protocol.*
import tse.unblockt.ls.rpc.RPCCallException
import tse.unblockt.ls.rpc.RpcError
import tse.unblockt.ls.server.KotlinLanguageServer
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class TestLanguageServer(
    private val sendChannel: SendChannel<String>,
    private val receiveChannel: ReceiveChannel<String>
): LanguageServer {

    companion object {
        private val idGenerator = AtomicLong(0)

        private fun extractResult(response: String): JsonElement {
            val jsonElement = Json.parseToJsonElement(response)
            assertTrue { jsonElement is JsonObject }

            val result = jsonElement.jsonObject["result"]
            val error = jsonElement.jsonObject["error"]

            if (error != null) {
                val rpcError = Json.decodeFromJsonElement<RpcError>(error)
                throw RPCCallException(rpcError.message, rpcError.code, rpcError.data)
            }

            assertNotNull(result)

            return result
        }
    }

    override val initializer: LanguageServer.Initializer
        get() = TestInitializer()

    override val textDocument: LanguageServer.TextDocument
        get() = TestTextDocument()

    override val workspace: LanguageServer.Workspace
        get() = TestWorkspace()
    override val buildSystem: LanguageServer.BuildSystem
        get() = TestBuildSystem()

    override val service: LanguageServer.Service
        get() = Service()

    override val completionItem: LanguageServer.CompletionItem
        get() = TestCompletionItem()

    override suspend fun getStatus(): HealthStatusInformation = HealthStatusInformation("health", "health", HealthStatus.HEALTHY)

    private inner class Service: LanguageServer.Service {
        override suspend fun pollAllRequests(): Boolean {
            call("$/pollAllRequests", null)
            val res = result()
            return res.decode()
        }

        override suspend fun cancelRequest(params: CancelRequestParams) {
            call("$/cancelRequest", params.encode())
        }
    }
    private inner class TestInitializer : LanguageServer.Initializer {
        override suspend fun shutdown(): Any? {
            notify("shutdown", null)
            return null
        }

        override suspend fun exit() {
            notify("exit", null)
        }

        override suspend fun initialize(params: InitializationRequestParameters): InitializationResponse {
            call("initialize", params.encode())
            val response = result().decode<InitializationResponse>()

            assertEquals(KotlinLanguageServer.DEFAULT_INITIALIZATION_RESPONSE, response)

            return response
        }

        override suspend fun initialized() {
            notify("initialized", null)
        }
    }

    private inner class TestBuildSystem: LanguageServer.BuildSystem {
        override suspend fun reload(params: JobWithProgressParams): Boolean {
            call("buildSystem/reload", params.encode())
            return result().decode()
        }
    }

    private inner class TestTextDocument: LanguageServer.TextDocument {
        override suspend fun definition(params: GoToDefinitionParams): Location? {
            call("textDocument/definition", params.encode())
            return result().decode<Location?>()
        }

        override suspend fun hover(params: HoverParameters): HoverResult {
            throw UnsupportedOperationException()
        }

        override suspend fun semanticTokensFull(params: SemanticTokensParams): SemanticTokens? {
            call("textDocument/semanticTokens/full", params.encode())
            return result().decode()
        }

        override suspend fun semanticTokensRange(params: SemanticTokensRangeParams): SemanticTokens? {
            call("textDocument/semanticTokens/range", params.encode())
            return result().decode()
        }

        override suspend fun didOpen(params: DidOpenTextDocumentParams) {
            notify("textDocument/didOpen", params.encode())
        }

        override suspend fun didChange(params: DidChangeTextDocumentParams) {
            notify("textDocument/didChange", params.encode())
        }

        override suspend fun diagnostic(params: DiagnosticTextDocumentParams): DocumentDiagnosticReport? {
            call("textDocument/diagnostic", params.encode())
            return result().decode()
        }

        override suspend fun completion(params: CompletionParams): CompletionList {
            call("textDocument/completion", params.encode())
            return result().decode()
        }

        override suspend fun signatureHelp(params: SignatureHelpParams): SignatureHelp? {
            call("textDocument/signatureHelp", params.encode())
            return result().decode()
        }

        override suspend fun didClose(params: DidCloseTextDocumentParams) {
            throw UnsupportedOperationException()
        }

        override suspend fun didSave(params: DidSaveTextDocumentParams) {
            notify("textDocument/didSave", params.encode())
        }
    }

    private inner class TestWorkspace : LanguageServer.Workspace {
        override suspend fun didCreateFiles(params: CreateFilesParams) {
            call("workspace/didCreateFiles", params.encode())
        }

        override suspend fun didRenameFiles(params: RenameFilesParams) {
            call("workspace/didRenameFiles", params.encode())
        }

        override suspend fun executeCommand(params: ExecuteCommandParams) {
            call("workspace/executeCommand", params.encode())
        }

        override suspend fun didChangeWatchedFiles(params: WatchedFilesChangedParams) {
            call("workspace/didChangeWatchedFiles", params.encode())
        }

        override suspend fun didDeleteFiles(params: DeleteFilesParams) {
            call("workspace/didDeleteFiles", params.encode())
        }

        override suspend fun rebuildIndexes(params: JobWithProgressParams): Boolean {
            call("workspace/rebuildIndexes", params.encode())
            return result().decode()
        }
    }

    private inner class TestCompletionItem : LanguageServer.CompletionItem {
        override suspend fun resolve(item: CompletionItem): CompletionItem {
            call("completionItem/resolve", item.encode())
            return result().decode()
        }
    }

    private suspend fun notify(method: String, data: String?) {
        call(method, data, isNotification = true)
    }

    private suspend fun call(method: String, data: String?, isNotification: Boolean = false) {
        sendChannel.send("""
            {"jsonrpc":"2.0",${if (isNotification) "" else "\"id\":${idGenerator.incrementAndGet()},"}"method":"$method" ${if (data != null) ", \"params\":$data" else ""}}                
            """.trimIndent())
    }

    private suspend fun result(): JsonElement {
        val received = withTimeout(1440.seconds) {
            receiveChannel.receive()
        }
        assertNotNull(received)

        val result = extractResult(received)
        assertNotNull(result)

        return result
    }

    inline fun <reified T> JsonElement.decode(): T {
        return Json.decodeFromJsonElement(this)
    }

    inline fun <reified T> T.encode(): String {
        return Json.encodeToString(this)
    }
}