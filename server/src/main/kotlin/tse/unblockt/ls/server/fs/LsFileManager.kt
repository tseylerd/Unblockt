// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

package tse.unblockt.ls.server.fs

import com.intellij.openapi.editor.Document
import com.intellij.psi.PsiFile
import tse.unblockt.ls.protocol.Uri

interface LsFileManager {
    fun getPsiFile(uri: Uri): PsiFile?
    fun getDocument(uri: Uri): Document?
}