// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

package tse.unblockt.ls.server.framework

import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import org.junit.jupiter.api.TestInfo
import tse.unblockt.ls.protocol.*
import tse.unblockt.ls.server.analysys.completion.SessionBasedCompletionMachine.Companion.ourNumberedParametersRegex
import tse.unblockt.ls.server.analysys.files.Offsets
import tse.unblockt.ls.server.analysys.text.applyEdits
import tse.unblockt.ls.server.analysys.text.buildEdits
import tse.unblockt.ls.server.fs.LsFileSystem
import tse.unblockt.ls.server.fs.asPath
import tse.unblockt.ls.server.fs.uri
import tse.unblockt.ls.util.*
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import kotlin.io.path.*
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LsClientSimulator(private val projectRoot: Path, private val info: TestInfo) {
    companion object {
        @Suppress("OPT_IN_USAGE")
        private val json = Json {
            prettyPrint = true
            prettyPrintIndent = "  "
        }
    }

    object Labels {
        const val START_LABEL = "<start>"
        const val END_LABEL = "<end>"
        const val CARET_LABEL = "<caret>"

        val all = setOf(
            START_LABEL,
            END_LABEL,
            CARET_LABEL,
        )
    }

    private var _caret: Int? = null

    lateinit var fileToWorkWith: Path

    private val compensations = mutableListOf<() -> Unit>()
    private val labelsMap = mutableMapOf<String, Int>()

    private val caretPosition: Position
        get() {
            val document = document()
            val curCaret: Int? = _caret
            assertNotNull(curCaret)

            return Offsets.offsetToPosition(curCaret, document)
        }

    private val uri: Uri
        get() = fileToWorkWith.uri

    val modifiedFiles = mutableListOf<Path>()

    context(RkTestEnvironment)
    suspend fun highlight() {
        val semanticTokens = languageServer.textDocument.semanticTokensFull(
            SemanticTokensParams(
                Document(
                    fileToWorkWith.uri
                )
            )
        )

        val projected = projectTokens(fileToWorkWith, semanticTokens)
        assertEqualsWithFile(projected, info)
    }

    context(RkTestEnvironment)
    suspend fun highlightOnRange() {
        val startPosition = positionFor(Labels.START_LABEL)
        val endPosition = positionFor(Labels.END_LABEL)
        val semanticTokens = languageServer.textDocument.semanticTokensRange(
            SemanticTokensRangeParams(
                Document(
                    fileToWorkWith.uri
                ),
                Range(
                    startPosition,
                    endPosition
                )
            )
        )

        val projected = projectTokens(fileToWorkWith, semanticTokens)
        assertEqualsWithFile(projected, info)
    }

    context(RkTestEnvironment)
    suspend fun rename(from: Path, to: String): Path {
        fun renameOnly(from: Path, to: Path) {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING)
        }

        val parent = from.parent
        val newFile = parent.resolve(to)
        renameOnly(from, newFile)

        compensations += {
            renameOnly(newFile, from)
        }
        languageServer.workspace.didRenameFiles(
            RenameFilesParams(
                files = listOf(FileRenameEvent(from.uri, newFile.uri))
            )
        )
        languageServer.service.pollAllRequests()
        return newFile
    }

    context(RkTestEnvironment)
    suspend fun declaration() {
        val result = languageServer.textDocument.definition(
            GoToDefinitionParams(textDocument = TextDocumentIdentifier(uri), position = caretPosition)
        )
        assertNotNull(result, "Location is null")
        assertEqualsWithFile(json.encodeToString(result.copy(uri = relativeUri(result.uri))), info)
    }

    context(RkTestEnvironment)
    suspend fun delete(path: Path) {
        languageServer.workspace.didChangeWatchedFiles(
            WatchedFilesChangedParams(
                changes = listOf(
                    WatchedFileChangeEvent(
                        path.uri,
                        FileChangeType.DELETED
                    )
                )
            )
        )
    }

    context(RkTestEnvironment)
    suspend fun signatureHelp() {
        val result = languageServer.textDocument.signatureHelp(
            SignatureHelpParams(
                textDocument = TextDocumentIdentifier(fileToWorkWith.uri),
                position = caretPosition
            )
        )
        assertNotNull(result, "SignatureHelp is null")
        assertEqualsWithFile(json.encodeToString(result), info)
    }

    context(RkTestEnvironment)
    suspend fun diagnose() {
        diagnose(fileToWorkWith)
    }

    context(RkTestEnvironment)
    suspend fun diagnose(path: Path) {
        val diagnostics = diagnostics(path)

        val projected = projectDiagnostics(document(path).text, diagnostics)
        assertEqualsWithFile(projected, info)
    }

    context(RkTestEnvironment)
    suspend fun diagnostics(path: Path): DocumentDiagnosticReport? {
        val diagnostics = languageServer.textDocument.diagnostic(
            DiagnosticTextDocumentParams(
                TextDocumentIdentifier(
                    path.uri
                )
            ),
        )
        return diagnostics
    }

    context(RkTestEnvironment)
    suspend fun completionItems() {
        val list = languageServer.textDocument.completion(
            CompletionParams(
                TextDocumentIdentifier(uri),
                caretPosition,
                null,
                null,
                null
            )
        )

        assertNotNull(list)
        val sorted = list.copy(
            items = list.items.map { item ->
                item.copy(
                    data = item.data?.let { data ->
                        data.copy(document = relativeUri(data.document))
                    },
                    command = item.command?.let { com ->
                        com.copy(arguments = com.arguments?.let { args ->
                            args.map { data ->
                                data.copy(document = relativeUri(data.document))
                            }
                        })
                    }
                )
            }.sortedBy { it.label }
        )
        assertEqualsWithFile(json.encodeToString(sorted), info)
    }

    context(RkTestEnvironment)
    suspend fun complete(chooser: (List<CompletionItem>) -> CompletionItem? = { it.firstOrNull() }) {
        completeAndDo(chooser) {
            assertContentIsExpected()
        }
    }

    context(RkTestEnvironment)
    @Suppress("unused")
    suspend fun type(offset: Int, text: String) {
        val position = Offsets.offsetToPosition(offset, document())
        languageServer.textDocument.didChange(
            DidChangeTextDocumentParams(
                textDocument = VersionedTextDocumentIdentifier(
                    uri = fileToWorkWith.uri,
                    0
                ), contentChanges = listOf(
                    TextDocumentContentChangeEvent(
                        text,
                        Range(position, position)
                    )
                )
            )
        )
        languageServer.service.pollAllRequests()
    }

    suspend fun RkTestEnvironment.applyTestData(create: Boolean) {
        changeMainFile(create)
        changeOtherFiles()
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    suspend fun RkTestEnvironment.changeDocument(path: Path, content: String, type: ChangeType) {
        if (type == ChangeType.WORKSPACE) {
            val before = Files.readString(path)
            Files.writeString(path, content)
            compensations += {
                Files.writeString(path, before)
            }

            languageServer.workspace.didChangeWatchedFiles(
                WatchedFilesChangedParams(
                    changes = listOf(
                        WatchedFileChangeEvent(
                            uri = path.uri,
                            FileChangeType.CHANGED
                        )
                    ),
                )
            )
        } else {
            @Suppress("BlockingMethodInNonBlockingContext")
            languageServer.textDocument.didChange(
                DidChangeTextDocumentParams(
                    textDocument = VersionedTextDocumentIdentifier(
                        path.uri,
                        1
                    ),
                    listOf(
                        TextDocumentContentChangeEvent(
                            content,
                            wholeText(Files.readString(path)),
                        )
                    )
                )
            )
        }
        languageServer.service.pollAllRequests()
    }

    fun cleanup() {
        for (compensation in compensations.reversed()) {
            compensation()
        }
        if (::fileToWorkWith.isInitialized) {
            fileToWorkWith.deleteIfExists()
        }
    }

    fun assertContentIsExpected() {
        val document = document()
        assertEqualsWithFile(document.text, info)
    }

    context(RkTestEnvironment)
    private suspend fun completeAndDo(chooser: (List<CompletionItem>) -> CompletionItem? = { it.firstOrNull() }, action: () -> Unit) {
        val list = languageServer.textDocument.completion(
            CompletionParams(
                TextDocumentIdentifier(uri),
                caretPosition,
                null,
                null,
                null
            )
        )

        assertNotNull(list)

        val initialItem = chooser(list.items)

        assertNotNull(initialItem)

        val completionItem = languageServer.completionItem.resolve(initialItem)
        val insertTextRaw = completionItem.insertText
        assertNotNull(insertTextRaw)

        val caretRange = ourNumberedParametersRegex.find(insertTextRaw)?.range
        val insertText = when {
            caretRange != null -> buildString {
                append(insertTextRaw, 0, caretRange.first)
                append("$".repeat(caretRange.last - caretRange.first + 1))
                if (caretRange.last + 1 < insertTextRaw.length) {
                    append(insertTextRaw, caretRange.last + 1, insertTextRaw.length)
                }
            }

            else -> insertTextRaw
        }.replace(ourNumberedParametersRegex, "")

        val edit = TextEdit(insertText, Range(positionFor(Labels.START_LABEL), caretPosition))
        applyTextEdits(listOf(edit))

        val additionalTextEdits = completionItem.additionalTextEdits
        if (!additionalTextEdits.isNullOrEmpty()) {
            applyTextEdits(additionalTextEdits)
        }


        val command = completionItem.command
        if (command == null) {
            action()
            return
        }

        languageServer.workspace.executeCommand(
            ExecuteCommandParams(
                command = completionItem.command!!.command,
                completionItem.command!!.arguments?.map { Json.encodeToJsonElement(it) },
            )
        )

        val nextEdits = try {
            withTimeout(1000L) {
                clientsCallChannel.consumeAsFlow().first { it.method == "workspace/applyEdit" }
            }.data
        } catch (e: TimeoutCancellationException) {
            null
        }
        if (nextEdits == null) {
            action()
            return
        }

        assertTrue(nextEdits is ApplyEditsParams)
        val textEdits = nextEdits.edit.changes.flatMap { it.value }

        applyTextEdits(textEdits)

        action()
    }

    private fun positionFor(label: String): Position {
        val offset = labelsMap[label]
        assertNotNull(offset)
        val document = document()
        val position = Offsets.offsetToPosition(offset, document)
        return position
    }

    context(RkTestEnvironment)
    private suspend fun applyTextEdits(edits: List<TextEdit>) {
        val document = document()
        assertNotNull(document)

        val content = document.text
        val newContent = applyEdits(buildEdits(content, edits), content)
        changeDocument(newContent)
    }

    fun document(): Document {
        return document(fileToWorkWith)
    }

    private fun document(path: Path): Document {
        val vFile = LsFileSystem.instance().getVirtualFile(path.uri)
        assertNotNull(vFile)

        val document = FileDocumentManager.getInstance().getDocument(vFile)
        assertNotNull(document)
        return document
    }

    @OptIn(ExperimentalPathApi::class)
    private suspend fun RkTestEnvironment.changeMainFile(create: Boolean) {
        val initialFile = info.findInitial
        if (!Files.exists(initialFile)) {
            return
        }

        @Suppress("BlockingMethodInNonBlockingContext")
        val contentInitial = Files.readString(initialFile)
        val lines = contentInitial.lines()
        val lastLine = lines.last()
        var content = when {
            lastLine.startsWith("//") -> {
                val withoutComment = lastLine.trimStart('/')
                fileToWorkWith = projectRoot.resolve(withoutComment).resolve("playground.kt")
                lines.dropLast(1).joinToString("\n")
            }

            else -> {
                fileToWorkWith = projectRoot.resolve("src/main/java/tse/com/playground.kt")
                contentInitial
            }
        }
        val sortedPairs = Labels.all.map { l ->
            l to content.indexOf(l)
        }.filter {
            it.second >= 0
        }.sortedBy { it.second }
        if (sortedPairs.isNotEmpty()) {
            var currentDelta = 0
            for ((label, offset) in sortedPairs) {
                val offsetValue = offset - currentDelta
                if (label == Labels.CARET_LABEL) {
                    _caret = offsetValue
                } else {
                    labelsMap[label] = offsetValue
                }
                currentDelta += label.length
                content = content.replace(label, "")
            }
        }

        if (create) {
            val parent = fileToWorkWith.parent
            if (!parent.exists()) {
                var start = parent
                val path = mutableListOf<Path>()
                path.add(parent)
                while (start.parent != null && !start.parent.exists()) {
                    start = start.parent
                    path.add(start)
                }
                parent.createDirectories()
                compensations += {
                    start.deleteRecursively()
                }
                languageServer.workspace.didCreateFiles(CreateFilesParams(
                    files = path.reversed().map { FileCreateEvent(it.uri) }
                ))
                languageServer.service.pollAllRequests()
            }
        }
        createDocument(content)
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    private suspend fun RkTestEnvironment.changeOtherFiles() {
        val modDir = info.findModificationsDirectory
        if (!Files.exists(modDir)) {
            return
        }
        @Suppress("BlockingMethodInNonBlockingContext")
        val allFiles = Files.list(modDir).use { it.toList() }
        for (oneFile in allFiles) {
            @Suppress("BlockingMethodInNonBlockingContext")
            val content = Files.readString(oneFile)
            val lines = content.lines()
            val last = lines.last().trim().trimStart('/')
            val separatorIndex = last.indexOf(':')
            val pathToFile = when {
                separatorIndex < 0 -> last
                else -> last.substring(0, separatorIndex)
            }
            val type = when {
                separatorIndex < 0 -> ChangeType.DOCUMENT
                else -> ChangeType.of(last.substring(separatorIndex + 1))
            }
            val fileToChange = projectRoot.resolve(pathToFile)
            modifiedFiles.add(fileToChange)
            val contentToSet = lines.dropLast(1).joinToString("\n")
            if (!Files.exists(fileToChange)) {
                Files.writeString(fileToChange, contentToSet, StandardOpenOption.CREATE)
                created(fileToChange)
                compensations += {
                    Files.deleteIfExists(fileToChange)
                }
            }
            changeDocument(fileToChange, contentToSet, type)
        }
    }

    context(RkTestEnvironment)
    private suspend fun created(fileToChange: Path) {
        languageServer.workspace.didCreateFiles(
            CreateFilesParams(
                listOf(
                    FileCreateEvent(
                        fileToChange.uri
                    )
                )
            )
        )
    }

    private suspend fun RkTestEnvironment.changeDocument(content: String) {
        changeDocument(fileToWorkWith, content, ChangeType.DOCUMENT)
    }

    private suspend fun RkTestEnvironment.createDocument(content: String) {
        @Suppress("BlockingMethodInNonBlockingContext")
        Files.writeString(fileToWorkWith, content, StandardOpenOption.CREATE)
        languageServer.workspace.didCreateFiles(
            CreateFilesParams(
                files = listOf(
                    FileCreateEvent(
                        uri = uri
                    )
                )
            )

        )
        languageServer.service.pollAllRequests()
    }

    private fun relativeUri(uri: Uri): Uri {
        val uriAsPath = uri.asPath()
        if (uriAsPath.startsWith(projectRoot)) {
            return uriAsPath.relativeTo(projectRoot).uri
        }
        return uriAsPath.fileName.uri
    }

    enum class ChangeType {
        DOCUMENT,
        WORKSPACE;

        companion object {
            fun of(name: String?): ChangeType {
                for (entry in entries) {
                    if (entry.name.lowercase() == name) {
                        return entry
                    }
                }
                return DOCUMENT
            }
        }
    }
}

context(RkTestEnvironment)
suspend fun simulateClient(root: Path, info: TestInfo, create: Boolean = false, call: suspend LsClientSimulator.() -> Unit) {
    val fileTestingFramework = LsClientSimulator(root, info)
    init(root)
    languageServer.initializer.initialized()
    try {
        with(fileTestingFramework) {
            applyTestData(create)
            call()
        }
    } finally {
        fileTestingFramework.cleanup()
    }
}