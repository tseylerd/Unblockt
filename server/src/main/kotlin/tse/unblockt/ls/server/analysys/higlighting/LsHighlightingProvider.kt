// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

package tse.unblockt.ls.server.analysys.higlighting

import tse.unblockt.ls.protocol.DocumentDiagnosticReport
import tse.unblockt.ls.protocol.Range
import tse.unblockt.ls.protocol.SemanticTokens
import tse.unblockt.ls.protocol.Uri
import tse.unblockt.ls.server.threading.Cancellable

interface LsHighlightingProvider {
    context(Cancellable)
    fun diagnostics(uri: Uri): DocumentDiagnosticReport?

    context(Cancellable)
    fun tokens(uri: Uri): SemanticTokens?

    context(Cancellable)
    fun tokensRange(uri: Uri, range: Range): SemanticTokens?
}