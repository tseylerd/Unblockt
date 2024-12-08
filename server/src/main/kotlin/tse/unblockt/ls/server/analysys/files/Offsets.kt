// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

package tse.unblockt.ls.server.analysys.files

import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.TextRange
import tse.unblockt.ls.protocol.Position
import tse.unblockt.ls.protocol.Range

object Offsets {
    fun offsetToPosition(offset: Int, document: Document): Position {
        return offsetToPosition(offset, lineNumber = {
            document.getLineNumber(it)
        }) {
            document.getLineStartOffset(it)
        }
    }

    fun offsetToPosition(offset: Int, content: String): Position {
        return offsetToPosition(offset, { off ->
            val substring = content.substring(startIndex = 0, endIndex = off)
            val lineBreaks = substring.count { it == '\n' }
            lineBreaks
        }) { line ->
            content.lines().take(line).sumOf { it.length + 1 }
        }
    }

    private fun offsetToPosition(offset: Int, lineNumber: (Int) -> Int, lineStart: (Int) -> Int): Position {
        val ln = lineNumber(offset)
        val startOffset = lineStart(ln)
        return Position(ln, offset - startOffset)
    }

    fun textRangeToRange(range: TextRange, document: Document): Range {
        return Range(offsetToPosition(range.startOffset, document), offsetToPosition(range.endOffset, document))
    }

    fun rangeToTextRange(range: Range, document: Document): TextRange {
        return TextRange(positionToOffset(document, range.start), positionToOffset(document, range.end))
    }

    fun textRangeToRange(range: TextRange, content: String): Range {
        return Range(offsetToPosition(range.startOffset, content), offsetToPosition(range.endOffset, content))
    }

    fun positionToOffset(document: Document, position: Position): Int {
        val lineStartOffset = document.getLineStartOffset(position.line)
        return lineStartOffset + position.character
    }

    fun positionToOffset(position: Position, lineToOffset: (Int) -> Int): Int {
        val lineStartOffset = lineToOffset(position.line)
        return lineStartOffset + position.character
    }
}