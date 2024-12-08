package tse.unblockt.ls.server.analysys.higlighting

import com.intellij.openapi.editor.Document
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import tse.unblockt.ls.protocol.DiagnosticItem
import tse.unblockt.ls.server.threading.Cancellable

class UnresolvedReferenceDiagnosticsProvider: DiagnosticsProvider {
    context(Cancellable)
    override fun provide(document: Document, element: PsiElement): Iterable<DiagnosticItem> {
        if (element !is KtNameReferenceExpression) {
            return emptyList()
        }

        val resolve = element.<start>mainReferenc<caret>
    }
}

//server/src/main/kotlin/tse/unblockt/ls/server/analysys/higlighting