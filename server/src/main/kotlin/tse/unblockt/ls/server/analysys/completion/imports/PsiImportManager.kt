// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

@file:OptIn(KaIdeApi::class)

package tse.unblockt.ls.server.analysys.completion.imports

import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.analysis.api.KaIdeApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.analyzeCopy
import org.jetbrains.kotlin.analysis.api.projectStructure.KaDanglingFileResolutionMode
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.ImportPath
import tse.unblockt.ls.protocol.TextEdit
import tse.unblockt.ls.server.analysys.completion.imports.ij.ImportsAnalyzer
import tse.unblockt.ls.server.analysys.completion.imports.ij.OptimizedImportsBuilder
import tse.unblockt.ls.server.analysys.completion.imports.ij.UsedReferencesCollector
import tse.unblockt.ls.server.analysys.completion.util.addImportEdit
import tse.unblockt.ls.server.analysys.files.Offsets

@Suppress("UnstableApiUsage")
object PsiImportManager {
    fun shortenReferences(file: KtFile, range: TextRange): ShortenReferencesEdits? {
        val command = analyze(file) {
            collectPossibleReferenceShortenings(file, range)
        }
        val allImportsToAdd = command.importsToAdd.joinToString { "import " + it.asString() }
        val importsEdit = when {
            command.importsToAdd.isEmpty() -> null
            else -> addImportEdit(file, allImportsToAdd)
        }
        val textAndEdit = when {
            command.listOfQualifierToShortenInfo.size == 1 -> {
                val shortenEdits = command.listOfQualifierToShortenInfo.single()
                val qualifierToShorten = shortenEdits.qualifierToShorten.element
                val shortText = qualifierToShorten?.selectorExpression?.text
                val textRange = qualifierToShorten?.textRange
                if (textRange != null && shortText != null) {
                    val rangeToEdit = TextRange.create(textRange)
                    val lsRange = Offsets.textRangeToRange(rangeToEdit, file.text)
                    shortText to TextEdit(shortText, lsRange)
                } else {
                    null
                }
            }
            command.listOfTypeToShortenInfo.size == 1 -> {
                val shortenEdits = command.listOfTypeToShortenInfo.single()
                val element = shortenEdits.typeToShorten.element
                val shortText = element?.referencedName
                val textRange = element?.textRange
                if (textRange != null && shortText != null) {
                    val rangeToEdit = TextRange.create(textRange)
                    val lsRange = Offsets.textRangeToRange(rangeToEdit, file.text)
                    shortText to TextEdit(shortText, lsRange)
                } else {
                    null
                }
            }
            else -> null
        } ?: return null
        return ShortenReferencesEdits(
            forImports = listOfNotNull(importsEdit),
            shortText = textAndEdit.first,
            shortenEdit = textAndEdit.second
        )
    }

    fun optimizedImports(file: KtFile): TextEdit? {
        val command = analyzeCopy(file, KaDanglingFileResolutionMode.PREFER_SELF) {
            val analyzed = ImportsAnalyzer.analyzeImports(file) ?: return@analyzeCopy null
            buildOptimizedImports(file, analyzed.usedReferencesData)
        } ?: return null

        val importList = file.importList!!.textRange
        val importsStart = importList.startOffset
        val importsEnd = importList.endOffset
        val newImports = command.joinToString("\n") {
            "import ${it.pathStr}"
        }.trim()
        return TextEdit(
            newImports,
            Offsets.textRangeToRange(createTextRange(file, newImports, importsStart, importsEnd), file.text)
        )
    }

    context(KaSession)
    private fun buildOptimizedImports(
        file: KtFile,
        data: UsedReferencesCollector.Result,
    ): Set<ImportPath>? {
        return OptimizedImportsBuilder(file, data).run { build() }
    }

    private fun createTextRange(file: KtFile, newImports: String, start: Int, end: Int): TextRange {
        if (newImports.isNotBlank()) {
            return TextRange.create(start, end)
        }
        val text = file.text
        var current = start - 1
        while (current > 0 && text[current] == '\n') {
            current--
        }
        val realStart = when {
            current != start -> current + 1
            else -> start
        }
        current = end + 2
        while (current < text.length && text[current] == '\n') {
            current++
        }
        val realEnd = when {
            current != end -> current - 2
            else -> end
        }
        return TextRange.create(realStart, realEnd)
    }

    data class ShortenReferencesEdits(
        val forImports: List<TextEdit>,
        val shortText: String,
        val shortenEdit: TextEdit,
    )
}