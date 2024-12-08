// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

package tse.unblockt.ls.server.analysys.completion.util

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.psi.KtFile
import tse.unblockt.ls.protocol.TextEdit
import tse.unblockt.ls.server.analysys.completion.imports.addImportModel
import tse.unblockt.ls.server.analysys.completion.imports.appendLineBreaks
import tse.unblockt.ls.server.analysys.files.Offsets

fun getCompletionPosition(file: PsiFile, offset: Int): PsiElement? {
    return file.findElementAt(offset) ?: if (file.textLength == offset) PsiTreeUtil.getDeepestLast(file) else null
}

fun findPrefix(
    file: PsiFile,
    offsetInFile: Int
): String {
    val prefix: String? = findPrefixForReference(file, offsetInFile)
    if (prefix != null) return prefix

    return findDefaultPrefix(file, offsetInFile)
}

private fun findDefaultPrefix(file: PsiFile, offset: Int): String {
    val text = file.text
    var start = offset - 1
    while (start > 0 && Character.isJavaIdentifierPart(text[start])) {
        start--
    }
    if (start == offset - 1) {
        return ""
    }
    return text.substring(start + 1, offset)
}

private fun findPrefixForReference(file: PsiFile, offsetInFile: Int): String? {
    val ref = file.findReferenceAt(offsetInFile)
    if (ref != null) {
        val element = ref.element
        val offsetInElement = offsetInFile - element.textRange.startOffset
        val rangeInElement = ref.rangeInElement
        if (rangeInElement.contains(offsetInElement)) {
            val beginIndex = rangeInElement.startOffset
            val text = element.text
            return text.substring(beginIndex, offsetInElement)
        }
    }
    return null
}

fun addImportEdit(file: KtFile, name: String): TextEdit {
    val addImportModel = file.addImportModel()
    val offsetToAddImports = addImportModel.offsetToAddImports
    val range = Offsets.textRangeToRange(TextRange.create(offsetToAddImports, offsetToAddImports), file.text)
    val text = "$name\n"
    return TextEdit(addImportModel.appendLineBreaks(text), range)
}