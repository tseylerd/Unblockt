// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

package tse.unblockt.ls.server.fs

import com.intellij.openapi.editor.Document
import com.intellij.psi.PsiDocumentManager
import org.jetbrains.kotlin.psi.KtFile
import tse.unblockt.ls.protocol.Uri
import tse.unblockt.ls.server.analysys.LsSession
import tse.unblockt.ls.server.analysys.index.LsPsiCache

internal class SessionBasedFileManager(private val session: LsSession): LsFileManager {
    private val psiCache: LsPsiCache = LsPsiCache.instance(session.project)

    override fun getPsiFile(uri: Uri): KtFile? {
        return psiCache[uri.asIJUrl] as? KtFile
    }

    override fun getDocument(uri: Uri): Document? {
        val psiFile = getPsiFile(uri) ?: return null
        return PsiDocumentManager.getInstance(session.project).getDocument(psiFile)
    }
}