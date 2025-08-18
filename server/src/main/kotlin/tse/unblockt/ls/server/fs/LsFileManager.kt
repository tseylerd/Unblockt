// Copyright 2024 Dmitrii Tseiler. Licensed under the Apache 2.0 Licence

package tse.unblockt.ls.server.fs

import com.intellij.openapi.editor.Document
import com.intellij.psi.PsiFile
import tse.unblockt.ls.protocol.Uri

interface LsFileManager {
    fun getPsiFile(uri: Uri): PsiFile?
    fun getDocument(uri: Uri): Document?
}