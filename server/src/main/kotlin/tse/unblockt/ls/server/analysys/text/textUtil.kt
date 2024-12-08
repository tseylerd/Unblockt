// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

package tse.unblockt.ls.server.analysys.text

import tse.unblockt.ls.protocol.TextEdit
import tse.unblockt.ls.server.analysys.files.Offsets


data class OffsetBasedEdit(val start: Int, val end: Int, val text: String)

fun applyEdits(markup: List<OffsetBasedEdit>, content: String): String {
    return buildString {
        var lastOffset = 0
        for (element in markup) {
            append(content, lastOffset, element.start)
            append(element.text)
            lastOffset = element.end
        }
        append(content, lastOffset, content.length)
    }
}

fun buildEdits(content: String, edits: List<TextEdit>): List<OffsetBasedEdit> {
    val lines = content.lines()
    val lineToStartOffset = mutableMapOf<Int, Int>()
    var currentOffset = 0
    for (i in lines.indices) {
        lineToStartOffset[i] = currentOffset
        currentOffset += lines[i].length + 1
    }
    val markup = edits.flatMap { item ->
        val startOffset = Offsets.positionToOffset(item.range.start) {
            lineToStartOffset[it]!!
        }
        val endOffset = Offsets.positionToOffset(item.range.end) {
            lineToStartOffset[it]!!
        }
        listOf(
            OffsetBasedEdit(startOffset, endOffset, item.newText),
        )
    }
    return markup
}