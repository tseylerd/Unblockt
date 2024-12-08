// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

package tse.unblockt.ls.server.analysys.higlighting

import com.intellij.openapi.editor.Document
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiErrorElement
import org.jetbrains.kotlin.analysis.api.KaSession
import tse.unblockt.ls.protocol.DiagnosticItem
import tse.unblockt.ls.protocol.DiagnosticSeverity
import tse.unblockt.ls.server.analysys.files.Offsets
import tse.unblockt.ls.server.threading.Cancellable

class ParserErrorsDiagnosticsProvider: DiagnosticsProvider {
    context(Cancellable, KaSession)
    override fun provide(document: Document, element: PsiElement): Iterable<DiagnosticItem> {
        cancellationPoint()

        if (element is PsiErrorElement) {
            val textRange = element.textRange
            return listOf(
                DiagnosticItem(
                    range = Offsets.textRangeToRange(textRange, document),
                    severity = DiagnosticSeverity.ERROR,
                    null,
                    null,
                    element.errorDescription,
                    null,
                    null
                )
            )
        }
        return emptyList()
    }
}