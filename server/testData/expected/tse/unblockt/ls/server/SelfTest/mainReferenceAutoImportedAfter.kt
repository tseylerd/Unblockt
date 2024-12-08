package tse.unblockt.ls.server.analysys.higlighting

import tse.unblockt.ls.protocol.DiagnosticItem
import com.intellij.openapi.editor.Document
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.idea.references.mainReference
import tse.unblockt.ls.server.threading.Cancellable
import com.intellij.psi.PsiElement

class UnresolvedReferenceDiagnosticsProvider: DiagnosticsProvider {
    context(Cancellable)
    override fun provide(document: Document, element: PsiElement): Iterable<DiagnosticItem> {
        if (element !is KtNameReferenceExpression) {
            return emptyList()
        }

        val resolve = element.mainReference
    }
}
