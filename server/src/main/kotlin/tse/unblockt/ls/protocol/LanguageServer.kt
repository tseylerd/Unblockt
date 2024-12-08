// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

package tse.unblockt.ls.protocol

interface LanguageServer {
    val initializer: Initializer
    val textDocument: TextDocument
    val workspace: Workspace
    val buildSystem: BuildSystem?
    val service: Service
    val completionItem: CompletionItem

    suspend fun getStatus(): HealthStatusInformation

    interface Initializer {
        suspend fun initialize(params: InitializationRequestParameters): InitializationResponse
        suspend fun initialized()
        suspend fun shutdown()
    }

    interface Service {
        suspend fun pollAllRequests(): Boolean
        suspend fun cancelRequest(params: CancelRequestParams)
    }

    interface TextDocument {
        suspend fun definition(params: GoToDefinitionParams): Location?
        suspend fun hover(params: HoverParameters): HoverResult?
        suspend fun didOpen(params: DidOpenTextDocumentParams)
        suspend fun didClose(params: DidCloseTextDocumentParams)
        suspend fun didSave(params: DidSaveTextDocumentParams)
        suspend fun semanticTokensFull(params: SemanticTokensParams): SemanticTokens?
        suspend fun semanticTokensRange(params: SemanticTokensRangeParams): SemanticTokens?
        suspend fun didChange(params: DidChangeTextDocumentParams)
        suspend fun diagnostic(params: DiagnosticTextDocumentParams): DocumentDiagnosticReport?
        suspend fun completion(params: CompletionParams): CompletionList?
        suspend fun signatureHelp(params: SignatureHelpParams): SignatureHelp?
    }

    interface CompletionItem {
        suspend fun resolve(item: tse.unblockt.ls.protocol.CompletionItem): tse.unblockt.ls.protocol.CompletionItem
    }

    interface BuildSystem {
        suspend fun reload(params: JobWithProgressParams): Boolean
    }

    interface Workspace {
        suspend fun didCreateFiles(params: CreateFilesParams)
        suspend fun didRenameFiles(params: RenameFilesParams)
        suspend fun didDeleteFiles(params: DeleteFilesParams)
        suspend fun didChangeWatchedFiles(params: WatchedFilesChangedParams)
        suspend fun executeCommand(params: ExecuteCommandParams)
        suspend fun rebuildIndexes(params: JobWithProgressParams): Boolean
    }
}