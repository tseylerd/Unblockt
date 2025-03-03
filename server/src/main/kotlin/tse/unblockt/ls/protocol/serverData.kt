// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

package tse.unblockt.ls.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import java.net.URLDecoder

interface WorkDoneProgress {
    val workDoneToken: ProgressToken?
}

@JvmInline
@Serializable
value class ProgressToken(val token: String)

@Serializable
data class InitializationRequestParameters(
    val processId: Int,
    val rootPath: String,
    val initializationOptions: InitializationOptions? = null,
    override val workDoneToken: ProgressToken? = null,
): WorkDoneProgress

@Serializable
data class InitializationOptions(
    val storagePath: String,
    val globalStoragePath: String,
)

@Serializable
data class InitializationResponse(
    val capabilities: ServerCapabilities,
    val serverInfo: ServerInfo
)

@Serializable
data class ServerInfo(
    val name: String,
    val version: String?
)

@Serializable
data class SemanticTokensLegend(
    val tokenTypes: List<String>,
    val tokenModifiers: List<String>
)

@Serializable
data class SemanticTokensOptions(
    val legend: SemanticTokensLegend,
    val range: Boolean,
    val full: FullSemanticTokensOptions
)

@Serializable
data class FullSemanticTokensOptions(
    val delta: Boolean
)

@Serializable
data class ServerCapabilities(
    val positionEncoding: PositionEncodingKind,
    val hoverProvider: Boolean,
    val documentHighlightProvider: Boolean,
    val semanticTokensProvider: SemanticTokensOptions,
    val textDocumentSync: TextDocumentSyncOptions,
    val definitionProvider: Boolean,
    val workspace: WorkspaceCapabilities,
    val diagnosticProvider: DiagnosticOptions,
    val completionProvider: CompletionOptions,
    val executeCommandProvider: ExecuteCommandOptions,
    val signatureHelpProvider: SignatureHelpOptions,
)

@Serializable
data class SignatureHelpOptions(
    val triggerCharacters: List<String>,
    val retriggerCharacters: List<String>,
)

@Serializable
data class WorkspaceCapabilities(
    val fileOperations: FileOperationsCapabilities
)

@Serializable
data class FileOperationsCapabilities(
    val didCreate: FileOperationRegistrationOptions,
    val didDelete: FileOperationRegistrationOptions,
    val didRename: FileOperationRegistrationOptions,
)

@Serializable
data class FileOperationRegistrationOptions(
    val filters: List<FileOperationFilter>
)

@Serializable
data class FileOperationFilter(
    val scheme: String?,
    val pattern: FileOperationPattern
)

@Serializable
data class FileOperationPattern(
    val glob: String,
    val matches: FileOperationPatternKind? = null,
    val options: FileOperationsPatternOptions? = null
)

@Suppress("unused")
@Serializable
enum class FileOperationPatternKind {
    @SerialName("file") FILE,
    @SerialName("folder") FOLDER
}

@Suppress("unused")
@Serializable
enum class FileOperationsPatternOptions(
    val ignoreCase: Boolean,
)

@Suppress("unused")
@Serializable
enum class PositionEncodingKind {
    @SerialName("utf-8") UTF8,
    @SerialName("utf-16") UTF16,
    @SerialName("utf-32") UTF32
}

@Serializable
data class Position(
    val line: Int,
    val character: Int
)

@JvmInline
@Serializable
value class Uri(val value: String) {
    val data: String
        get() = URLDecoder.decode(value, "UTF-8")
}

@Serializable
data class Document(
    val uri: Uri
)

@Serializable
data class HoverParameters(
    val textDocument: Document,
    val position: Position
)

@Suppress("unused")
@Serializable
data class DocumentHighlightParams(
    val textDocument: Document,
    val position: Position
)

@Serializable
data class Range(val start: Position, val end: Position)

@Suppress("unused", "CanBeParameter")
@JvmInline
@Serializable
value class DocumentHighlightKind private constructor(private val value: Int) {
    companion object {
        val TEXT = DocumentHighlightKind(1)
        val READ = DocumentHighlightKind(2)
        val WRITE = DocumentHighlightKind(3)
    }
    init {
        if (value !in listOf(1, 2, 3)) {
            throw IllegalArgumentException("Unsupported value: $value")
        }
    }
}

@Suppress("unused")
@Serializable
data class DocumentHighlight(
    val range: Range,
    val kind: DocumentHighlightKind? = null
)

@Serializable
data class HoverResult(
    val contents: String
)

@Serializable
data class SemanticTokensParams(
    val textDocument: Document
)

@Serializable
data class SemanticTokensDeltaParams(
    val textDocument: Document,
    val previousResultId: String? = null
)

@Serializable
data class SemanticTokensRangeParams(
    val textDocument: Document,
    val range: Range,
)

sealed interface SemanticTokensDeltaResponse

@Serializable
data class SemanticTokens(
    val resultId: String? = null,
    val data: IntArray
): SemanticTokensDeltaResponse {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SemanticTokens

        return data.contentEquals(other.data)
    }

    override fun hashCode(): Int {
        return data.contentHashCode()
    }
}

@Serializable
data class SemanticTokensDelta(
    val resultId: String? = null,
    val edits: List<SemanticTokensEdit>
): SemanticTokensDeltaResponse

@Serializable
data class SemanticTokensEdit(
    val start: Int,
    val deleteCount: Int,
    val data: IntArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SemanticTokensEdit

        if (start != other.start) return false
        if (deleteCount != other.deleteCount) return false
        if (!data.contentEquals(other.data)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = start
        result = 31 * result + deleteCount
        result = 31 * result + data.contentHashCode()
        return result
    }
}

@Serializable
enum class SemanticTokenType(val schemaName: String) {
    @SerialName("namespace") NAMESPACE("namespace"),
    @SerialName("type") TYPE("type"),
    @SerialName("class") CLASS("class"),
    @SerialName("enum") ENUM("enum"),
    @SerialName("interface") INTERFACE("interface"),
    @SerialName("struct") STRUCT("struct"),
    @SerialName("typeParameter") TYPEPARAMETER("typeParameter"),
    @SerialName("parameter") PARAMETER("parameter"),
    @SerialName("variable") VARIABLE("variable"),
    @SerialName("property") PROPERTY("property"),
    @SerialName("enumMember") ENUMMEMBER("enumMember"),
    @SerialName("event") EVENT("event"),
    @SerialName("function") FUNCTION("function"),
    @SerialName("method") METHOD("method"),
    @SerialName("macro") MACRO("macro"),
    @SerialName("keyword") KEYWORD("keyword"),
    @SerialName("modifier") MODIFIER("modifier"),
    @SerialName("comment") COMMENT("comment"),
    @SerialName("string") STRING("string"),
    @SerialName("number") NUMBER("number"),
    @SerialName("regexp") REGEXP("regexp"),
    @SerialName("operator") OPERATOR("operator"),
    @SerialName("edecorator") EDECORATOR("edecorator");
    companion object {
        fun byOrdinal(ordinal: Int): SemanticTokenType = entries.first { it.ordinal == ordinal }
    }
}

@Serializable
enum class SemanticTokenModifier(val schemaName: String) {
    @SerialName("declaration") DECLARATION("declaration"),
    @SerialName("definition") DEFINITION("definition"),
    @SerialName("readonly") READONLY("readonly"),
    @SerialName("static") STATIC("static"),
    @SerialName("deprecated") DEPRECATED("deprecated"),
    @SerialName("abstract") ABSTRACT("abstract"),
    @SerialName("async") ASYNC("async"),
    @SerialName("modification") MODIFICATION("modification"),
    @SerialName("documentation") DOCUMENTATION("documentation"),
    @SerialName("defaultLibrary") DEFAULTLIBRARY("defaultLibrary")
}

@Suppress("unused", "CanBeParameter")
@Serializable
@JvmInline
value class TextDocumentSyncKind(private val value: Int) {
    companion object {
        val NONE = TextDocumentSyncKind(0)
        val FULL = TextDocumentSyncKind(1)
        val INCREMENTAL = TextDocumentSyncKind(2)
    }

    init {
        if (value !in setOf(0, 1, 2)) {
            throw IllegalArgumentException("Must be one of the predefined values: $value")
        }
    }
}

@Serializable
data class TextDocumentSyncOptions(
    val openClose: Boolean,
    val change: TextDocumentSyncKind,
    val save: Boolean
)

@Serializable
data class TextDocumentItem(
    val uri: Uri,
    val languageId: String,
    val version: Int,
    val text: String
)

@Serializable
data class DidOpenTextDocumentParams(
    val textDocument: TextDocumentItem
)

@Serializable
data class VersionedTextDocumentIdentifier(
    val uri: Uri,
    val version: Int,
)

@Serializable
data class TextDocumentIdentifier(
    val uri: Uri,
)

@Serializable
data class TextDocumentContentChangeEvent(
    val text: String,
    val range: Range,
)

@Serializable
data class DidChangeTextDocumentParams(
    val textDocument: VersionedTextDocumentIdentifier,
    val contentChanges: List<TextDocumentContentChangeEvent>
)

@Serializable
data class DidCloseTextDocumentParams(
    val textDocument: TextDocumentIdentifier,
)

@Serializable
data class DidSaveTextDocumentParams(
    val textDocument: TextDocumentIdentifier,
    val text: String? = null
)

@Serializable
data class GoToDefinitionParams(
    val textDocument: TextDocumentIdentifier,
    val position: Position
)

@Serializable
data class Location(
    val uri: Uri,
    val range: Range
)

@Serializable
data class CreateFilesParams(
    val files: List<FileCreateEvent>
)

@Serializable
data class RenameFilesParams(
    val files: List<FileRenameEvent>
)

@Serializable
data class DeleteFilesParams(
    val files: List<DeleteFileEvent>
)

@Serializable
data class WatchedFilesChangedParams(
    val changes: List<WatchedFileChangeEvent>
)

@Serializable
data class WatchedFileChangeEvent(
    val uri: Uri,
    val type: FileChangeType
)

@Serializable
data class DeleteFileEvent(
    val uri: Uri,
)

@JvmInline
@Serializable
value class FileChangeType private constructor(val value: Int) {
    companion object {
        val CREATED = FileChangeType(1)
        val CHANGED = FileChangeType(2)
        val DELETED = FileChangeType(3)
    }
    init {
        if (value !in 1..3) {
            throw IllegalArgumentException("Unsupported file change type: $value")
        }
    }
}

@Serializable
data class FileCreateEvent(
    val uri: Uri,
)

@Serializable
data class FileRenameEvent(
    val oldUri: Uri,
    val newUri: Uri,
)

@Serializable
data class DiagnosticOptions(
    val identifier: String? = null,
    val interFileDependencies: Boolean,
    val workspaceDiagnostics: Boolean
)

@Serializable
data class DiagnosticTextDocumentParams(
    val textDocument: TextDocumentIdentifier,
    val identifier: String? = null,
    val previousResultId: String? = null
)

@Serializable
data class DocumentDiagnosticReport(
    val kind: DocumentDiagnosticReportKind,
    val resultId: String? = null,
    val items: List<DiagnosticItem>
)

@Suppress("unused")
@Serializable
enum class DocumentDiagnosticReportKind {
    @SerialName("full") FULL,
    @SerialName("unchanged") UNCHANGED,
}

@Serializable
data class DiagnosticItem(
    val range: Range,
    val severity: DiagnosticSeverity,
    val code: Int? = null,
    val source: String? = null,
    val message: String,
    val tags: List<DiagnosticTag>? = null,
    val relatedInformation: List<DiagnosticRelatedInformation>? = null,
)

@Suppress("unused")
@JvmInline
@Serializable
value class DiagnosticSeverity private constructor(val value: Int) {
    companion object {
        val ERROR = DiagnosticSeverity(1)
        val WARNING = DiagnosticSeverity(2)
        val INFORMATION = DiagnosticSeverity(3)
        val HINT = DiagnosticSeverity(4)
    }
}

@Suppress("unused")
@JvmInline
@Serializable
value class DiagnosticTag private constructor(val value: Int) {
    companion object {
        val UNNECESSARY = DiagnosticTag(1)
        val DEPRECATED = DiagnosticTag(2)
    }
}

@Serializable
data class DiagnosticRelatedInformation(
    val location: Location,
    val message: String
)

@Serializable
data class CompletionItemOptions(
    val labelDetailsSupport: Boolean
)
@Serializable
data class CompletionOptions(
    val triggerCharacters: List<String>? = null,
    val allCommitCharacters: List<String>? = null,
    val resolveProvider: Boolean,
    val completionItem: CompletionItemOptions? = null
)

@Serializable
@JvmInline
value class IdToken(val value: String)

@Serializable
@JvmInline
value class CompletionTriggerKind private constructor(val value: Int) {
    companion object {
        val INVOKED = CompletionTriggerKind(1)
        val TRIGGER_CHARACTER = CompletionTriggerKind(2)
        val TRIGGER_FOR_INCOMPLETE_COMPLETIONS = CompletionTriggerKind(3)
    }
}

@Serializable
data class CompletionContext(
    val triggerKind: CompletionTriggerKind,
    val triggerCharacter: String? = null,
) {
    val invocationCount: Int = 1
}

@Serializable
data class CompletionParams(
    val textDocument: TextDocumentIdentifier,
    val position: Position,
    val workDoneToken: IdToken? = null,
    val partialResultToken: IdToken? = null,
    val context: CompletionContext? = null
)

@Serializable
data class CompletionList(
    val isIncomplete: Boolean,
    val items: List<CompletionItem>
)

@Serializable
data class CompletionItemLabelDetails(
    val detail: String? = null,
    val description: String? = null
)

@Serializable
@JvmInline
value class CompletionItemKind private constructor(val value: Int) {
    companion object {
        val TEXT = CompletionItemKind(1)
        val METHOD = CompletionItemKind(2)
        val FUNCTION = CompletionItemKind(3)
        val CONSTRUCTOR = CompletionItemKind(4)
        val FIELD = CompletionItemKind(5)
        val VARIABLE = CompletionItemKind(6)
        val CLASS = CompletionItemKind(7)
        val INTERFACE = CompletionItemKind(8)
        val MODULE = CompletionItemKind(9)
        val PROPERTY = CompletionItemKind(10)
        val UNIT = CompletionItemKind(11)
        val VALUE = CompletionItemKind(12)
        val ENUM = CompletionItemKind(13)
        val KEYWORD = CompletionItemKind(14)
        val SNIPPET = CompletionItemKind(15)
        val COLOR = CompletionItemKind(16)
        val FILE = CompletionItemKind(17)
        val REFERENCE = CompletionItemKind(18)
        val FOLDER = CompletionItemKind(19)
        val ENUMMEMBER = CompletionItemKind(20)
        val CONSTANT = CompletionItemKind(21)
        val STRUCT = CompletionItemKind(22)
        val EVENT = CompletionItemKind(23)
        val OPERATOR = CompletionItemKind(24)
        val TYPEPARAMETER = CompletionItemKind(25)
    }
}

@Serializable
@JvmInline
value class CompletionItemTag private constructor(val value: Int) {
    companion object {
        val DEPRECATED = CompletionItemTag(1)
    }
}

@Serializable
@JvmInline
value class InsertTextFormat private constructor(val value: Int) {
    companion object {
        val PLAIN_TEXT = InsertTextFormat(1)
        val SNIPPET = InsertTextFormat(2)
    }
}

@Serializable
@JvmInline
value class InsertTextMode private constructor(val value: Int) {
    companion object {
        val AS_IS = InsertTextMode(1)
        val ADJUST_INDENTATION = InsertTextMode(2)
    }
}

@Serializable
enum class MarkupKind {
    @SerialName("plaintext") PLAINTEXT,
    @SerialName("markdown") MARKDOWN,
}
@Serializable
data class MarkupContent(
    val kind: MarkupKind,
    val value: String
)

@Serializable
data class InsertReplaceEdit(
    val newText: String,
    val insert: Range,
    val replace: Range
)

@Serializable
data class TextEdit(
    val newText: String,
    val range: Range,
)

@Serializable
data class Command(
    val title: String,
    val command: String,
    val arguments: JsonElement?
)

@Serializable
data class CompletionCommand(
    val title: String,
    val command: String,
    val arguments: List<AdditionalCompletionData>? = null
)

@Serializable
data class CompletionItem(
    val label: String,
    val labelDetails: CompletionItemLabelDetails? = null,
    val kind: CompletionItemKind? = null,
    val tags: List<CompletionItemTag>? = null,
    val details: String? = null,
    val documentation: MarkupContent? = null,
    val preselect: Boolean? = null,
    val sortText: String? = null,
    val filterText: String? = null,
    val insertText: String? = null,
    val insertTextFormat: InsertTextFormat? = null,
    val insertTextMode: InsertTextMode? = null,
    val textEdit: InsertReplaceEdit? = null,
    val textEditText: String? = null,
    val additionalTextEdits: List<TextEdit>? = null,
    val commitCharacters: List<String>? = null,
    val command: CompletionCommand? = null,
    val data: AdditionalCompletionData? = null,
    val deprecated: Boolean? = null
)

@Serializable
data class AdditionalCompletionData(
    val document: Uri,
    val start: Int,
    val end: Int,
    val shortenReference: ShortenReferenceData? = null,
    val addImport: AddImportData? = null
)

@Serializable
data class ShortenReferenceData(
    val packageName: String,
    val shortenName: String
)

@Serializable
data class AddImportData(
    val packageName: String,
    val import: String,
)

@Serializable
data class CancelRequestParams(
    val id: Long
)

@Serializable
data class ExecuteCommandOptions(
    val commands: List<String>
)

@Serializable
data class ExecuteCommandParams(
    val command: String,
    var arguments: List<JsonElement>? = null,
)

@Serializable
data class JobWithProgressParams(
    override val workDoneToken: ProgressToken? = null
): WorkDoneProgress

@Serializable
data class SignatureHelpParams(
    val textDocument: TextDocumentIdentifier,
    val position: Position,
)

@Serializable
data class SignatureHelp(
    val signatures: List<SignatureInformation>,
    val activeSignature: Int,
    val activeParameter: Int? = null
)

@Serializable
data class SignatureInformation(
    val label: String,
    val documentation: String? = null,
    val parameters: List<ParameterInformation>,
    val activeParameter: Int? = null
)

@Serializable
data class ParameterInformation(
    val label: String,
    val documentation: String? = null,
)

@Serializable
@JvmInline
value class ErrorType private constructor(val value: Int) {
    companion object {
        val PARSING_ERROR = ErrorType(1)
    }
    init {
        if (value !in listOf(1)) {
            throw IllegalArgumentException("Unsupported value: $value")
        }
    }
}