// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

package tse.unblockt.ls.server.analysys.declaration

import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.psi.PsiDocumentManager
import org.apache.logging.log4j.kotlin.logger
import tse.unblockt.ls.protocol.Location
import tse.unblockt.ls.protocol.Position
import tse.unblockt.ls.protocol.Range
import tse.unblockt.ls.protocol.Uri
import tse.unblockt.ls.server.analysys.LsSession
import tse.unblockt.ls.server.analysys.files.Offsets
import tse.unblockt.ls.server.fs.LsFileManager

internal class SessionBasedGoToDeclarationProvider(private val session: LsSession, private val filesManager: LsFileManager): LsGoToDeclarationProvider {
    override fun resolve(uri: Uri, location: Position): Location? {
        val psiFile = filesManager.getPsiFile(uri) ?: return null
        val document = filesManager.getDocument(uri) ?: return null
        val offset = Offsets.positionToOffset(document, location)
        val referenceAt = psiFile.findReferenceAt(offset)
        if (referenceAt == null) {
            logger.debug("Reference at ${location.line}:${location.character} not found")
            return null
        }

        val resolvedTo = referenceAt.resolve()
        if (resolvedTo == null) {
            logger.debug("Resolve failed for reference \"${referenceAt.element.text}\" at offset $offset")
            return null
        }

        val resolvedContainingFile = resolvedTo.containingFile ?: return null
        val containingFile = resolvedContainingFile.virtualFile ?: return null

        val vFile = containingFile
        val ioFile = VfsUtilCore.virtualToIoFile(vFile)
        val ioFileURI = ioFile.toURI()
        if (vFile.fileType.isBinary) {
            return Location(Uri(ioFileURI.toString()), Range(Position(0, 0), Position(0, 0)))
        }

        val docManager = PsiDocumentManager.getInstance(session.project)
        val resolvedDoc = docManager.getDocument(resolvedTo.containingFile)
        if (resolvedDoc == null) {
            logger.debug("Resolved document is null")
            return null
        }

        val range = Offsets.textRangeToRange(resolvedTo.textRange, resolvedDoc)
        val lsUri = Uri(ioFileURI.toString())
        return Location(lsUri, range)
    }
}