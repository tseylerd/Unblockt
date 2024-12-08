// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

@file:Suppress("UnstableApiUsage")

package tse.unblockt.ls.server.analysys.completion.imports

import org.jetbrains.kotlin.psi.KtFile

fun KtFile.addImportModel(): AddImportModel {
    val packageDirectiveTextRange = packageDirective?.textRange?.takeIf { !it.isEmpty }
    val importListTextRange = importList?.textRange?.takeIf { !it.isEmpty }
    val text: String = text
    val offset = importListTextRange?.startOffset ?: packageDirectiveTextRange?.endOffset?.let { minOf(it + 1, text.length - 1) } ?: 0
    return AddImportModel(
        packageDirectiveTextRange,
        importListTextRange,
        addLineBreakBefore(offset, text),
        packageDirectiveTextRange == null && importListTextRange == null && text.isNotEmpty() && !text[0].isWhitespace(),
        offset
    )
}

fun AddImportModel.appendLineBreaks(text: String): String {
    return (if (addLineBreakBefore) "\n" else "") + text + (if (addLineBreakAfter) "\n" else "")
}

private fun addLineBreakBefore(offset: Int, text: String): Boolean {
    if (offset < 2) {
        return false
    }
    return text[offset - 1] != '\n' || text[offset - 2] != '\n'
}