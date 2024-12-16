// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

package tse.unblockt.ls.server.analysys.completion

import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.util.TextRange
import com.intellij.pom.PomManager
import com.intellij.pom.core.impl.PomModelImpl
import com.intellij.psi.impl.source.tree.FileElement
import kotlinx.coroutines.*
import org.jetbrains.kotlin.analysis.api.analyzeCopy
import org.jetbrains.kotlin.analysis.api.projectStructure.KaDanglingFileResolutionMode
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtFile
import tse.unblockt.ls.protocol.*
import tse.unblockt.ls.server.analysys.LsSession
import tse.unblockt.ls.server.analysys.completion.imports.CalculatedImports
import tse.unblockt.ls.server.analysys.completion.imports.PsiImportManager
import tse.unblockt.ls.server.analysys.completion.imports.addImportModel
import tse.unblockt.ls.server.analysys.completion.util.addImportEdit
import tse.unblockt.ls.server.analysys.completion.util.createProviders
import tse.unblockt.ls.server.analysys.completion.util.findPrefix
import tse.unblockt.ls.server.analysys.completion.util.getCompletionPosition
import tse.unblockt.ls.server.analysys.completion.util.ij.KotlinPositionContextDetector
import tse.unblockt.ls.server.analysys.files.Offsets
import tse.unblockt.ls.server.analysys.text.PrefixMatcher
import tse.unblockt.ls.server.analysys.text.applyEdits
import tse.unblockt.ls.server.analysys.text.buildEdits
import tse.unblockt.ls.server.analysys.text.ij.CamelCaseMatcher
import tse.unblockt.ls.server.fs.LsFileManager
import tse.unblockt.ls.server.threading.Cancellable
import tse.unblockt.ls.server.util.platform
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

const val COMPLETION_ITEMS_PROPERTY_KEY = "unblockt.completion.items.limit"
private val ourMaxCompletionItems = System.getProperty(COMPLETION_ITEMS_PROPERTY_KEY, "50").toInt()

internal class SessionBasedCompletionMachine(
    @Suppress("unused") private val session: LsSession,
    private val fileManager: LsFileManager,
) : LsCompletionMachine {
    companion object {
        val ourNumberedParametersRegex = Regex("\\$[0-9]+")
        val ourCommentedParametersRegex = Regex("/\\*\\$[0-9]+\\*/")

        private const val FAKE_IDENTIFIER = "rkFakeIdentifier "
    }

    private val String.withoutParameters: String
        get() = replaceInString(this, ourNumberedParametersRegex) {
            "/*$it*/"
        }

    private val String.withParameters: String
        get() = replaceInString(this, ourCommentedParametersRegex) {
            it.substring(2, it.length - 2)
        }

    override fun resolve(item: CompletionItem): CompletionItem {
        if (item.data == null) {
            return item
        }

        val psiFile = fileManager.getPsiFile(item.data.document) as KtFile

        val data = item.data
        val insertText = item.insertText!!.withoutParameters
        val (edits, text) = when {
            data.shortenReference != null -> {
                val packageOfSymbol = data.shortenReference.packageName
                if (psiFile.packageFqName.asString() == packageOfSymbol) {
                    emptyList<TextEdit>() to data.shortenReference.shortenName
                } else {
                    val copy = fileWithAppliedCompletionItem(psiFile, data, insertText)
                    val shorten = PsiImportManager.shortenReferences(copy, TextRange.create(data.start, data.start + insertText.length)) ?: return item
                    val (edits, text) = shorten
                    edits to text.withParameters
                }
            }

            data.addImport != null -> {
                val packageName = data.addImport.packageName
                if (psiFile.packageFqName == FqName(packageName)) {
                    emptyList<TextEdit>() to item.insertText
                } else {
                    val applied = fileWithAppliedCompletionItem(psiFile, data, insertText)
                    val addImportEdit = addImportEdit(applied, "import ${data.addImport.import}")
                    val model = psiFile.addImportModel()
                    val importEdit = when {
                        model.imports == null -> addImportEdit
                        else -> {
                            val changedCopy = changeFile(applied) { content ->
                                applyEdits(buildEdits(content, listOf(addImportEdit)), content)
                            }
                            val optimizedEdit = PsiImportManager.optimizedImports(changedCopy)
                            when {
                                optimizedEdit == null -> addImportEdit
                                else -> optimizedEdit.copy(range = Offsets.textRangeToRange(model.imports, psiFile.text))
                            }
                        }
                    }
                    listOfNotNull(importEdit) to item.insertText
                }
            }

            else -> throw UnsupportedOperationException("Data is not valid")
        }
        return item.copy(insertText = text, additionalTextEdits = edits, command = null)
    }

    private fun fileWithAppliedCompletionItem(psiFile: KtFile, data: AdditionalCompletionData, text: String) = changeFile(psiFile) { content ->
        insertCompletionItem(content, data.start, data.end, text)
    }

    private fun insertCompletionItem(content: String, start: Int, end: Int, insertText: String): String {
        return buildString {
            append(content, 0, start)
            append(insertText)
            append(content, end, content.length)
        }
    }

    context(Cancellable)
    override suspend fun launchCompletion(params: CompletionParams): CompletionList {
        val psiFile = fileManager.getPsiFile(params.textDocument.uri) as KtFile
        val position = params.position
        val document = fileManager.getDocument(params.textDocument.uri) ?: return CompletionList(false, emptyList())
        val offset = Offsets.positionToOffset(document, position)
        val fileCopy = changeFile(psiFile, replaceInRange(offset, offset, FAKE_IDENTIFIER))
        val prefix: String = findPrefix(fileCopy, offset)
        val matcher = when {
            prefix.isBlank() -> PrefixMatcher.Always
            else -> CamelCaseMatcher(prefix, isCaseSensitive = true, isTypoTolerant = false)
        }

        val leaf = getCompletionPosition(fileCopy, offset) ?: return CompletionList(false, emptyList())
        val positionContext = KotlinPositionContextDetector.detect(leaf)
        val request = LsCompletionRequest(
            params.textDocument.uri,
            fileCopy,
            psiFile,
            offset,
            leaf,
            params,
            matcher,
            psiFile.platform,
            CalculatedImports(psiFile),
            prefix,
            document,
            positionContext
        )
        return framedCompletion(params.context, request)
    }

    private fun replaceInRange(start: Int, end: Int, @Suppress("SameParameterValue") replacement: String): (String) -> String {
        return { str ->
            buildString {
                append(str, 0, start)
                append(replacement)
                append(str, end, str.length)
            }
        }
    }

    private fun replaceInString(value: String, regex: Regex, replace: (String) -> String): String {
        val all = regex.findAll(value)
        var current = 0
        return buildString {
            for (result in all) {
                append(value, current, result.range.first)
                append(replace(result.value))
                current = result.range.last + 1
            }
            if (current < value.length) {
                append(value, current, value.length)
            }
        }
    }

    context(Cancellable)
    private suspend fun framedCompletion(@Suppress("UNUSED_PARAMETER") context: CompletionContext?, request: LsCompletionRequest): CompletionList {
        val result: MutableSet<CompletionItem> = Collections.newSetFromMap(ConcurrentHashMap())
        val items = AtomicInteger(0)
        return try {
            val cancellable = this@Cancellable
            coroutineScope {
                with(Cancellable(Cancellable(), cancellable)) {
                    val jobs = mutableListOf<Job>()
                    val providers = analyzeCopy(request.file, KaDanglingFileResolutionMode.PREFER_SELF) {
                        createProviders(request)
                    }
                    for (provider in providers) {
                        cancellationPoint()
                        jobs += launch {
                            analyzeCopy(request.file, KaDanglingFileResolutionMode.PREFER_SELF) {
                                cancellationPoint()
                                for (completionItem in provider.provide(request)) {
                                    cancellationPoint()
                                    if (request.matcher.isPrefixMatch(completionItem.label)) {
                                        val andIncrement = items.incrementAndGet()
                                        if (andIncrement > ourMaxCompletionItems) {
                                            return@analyzeCopy
                                        }
                                        result += completionItem
                                    }
                                }
                            }
                        }
                    }
                    jobs.joinAll()
                    CompletionList(items.get() > ourMaxCompletionItems, result.toList())
                }
            }
        } catch (e: TimeoutCancellationException) {
            CompletionList(true, result.toList())
        }
    }
}

fun changeFile(file: KtFile, modification: (String) -> String): KtFile {
    val fileCopy = file.copy() as KtFile
    val text = file.text
    val replacementText = modification(text)
    val tempDocument = DocumentImpl(text, text.contains('\r') || replacementText.contains('\r'), true)
    tempDocument.setText(replacementText)

    val node = fileCopy.node as? FileElement
        ?: throw IllegalStateException("Node is not a FileElement ${fileCopy.javaClass.name} / ${fileCopy.fileType} / ${fileCopy.node}")

    @Suppress("UnstableApiUsage")
    val applyPsiChange = (PomManager.getModel(fileCopy.project) as PomModelImpl).reparseFile(
        fileCopy,
        node,
        tempDocument.immutableCharSequence
    )
    applyPsiChange?.run()
    return fileCopy
}