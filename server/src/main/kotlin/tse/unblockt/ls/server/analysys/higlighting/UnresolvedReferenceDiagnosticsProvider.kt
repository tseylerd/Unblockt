// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

package tse.unblockt.ls.server.analysys.higlighting

import com.intellij.openapi.editor.Document
import com.intellij.psi.PsiElement
import org.apache.logging.log4j.kotlin.logger
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import tse.unblockt.ls.protocol.DiagnosticItem
import tse.unblockt.ls.protocol.DiagnosticSeverity
import tse.unblockt.ls.server.analysys.files.Offsets
import tse.unblockt.ls.server.threading.Cancellable

class UnresolvedReferenceDiagnosticsProvider: DiagnosticsProvider {
    context(Cancellable, KaSession)
    override fun provide(document: Document, element: PsiElement): Iterable<DiagnosticItem> {
        cancellationPoint()

        if (element !is KtNameReferenceExpression) {
            return emptyList()
        }
        
        val resolve = try {
            element.mainReference.multiResolve(false)
        } catch (t: Throwable) {
            logger.warn(t)
            null
        }
        if (!resolve.isNullOrEmpty()) {
            return emptyList()
        }
        return listOf(DiagnosticItem(
            range = Offsets.textRangeToRange(element.textRange, document),
            severity = DiagnosticSeverity.ERROR,
            null,
            null,
            "Unresolved reference",
            null,
            null
        ))
    }
}