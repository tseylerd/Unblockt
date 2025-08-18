// Copyright 2024 Dmitrii Tseiler. Licensed under the Apache 2.0 Licence

package tse.unblockt.ls.server.analysys.higlighting

import com.intellij.openapi.editor.Document
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KaSession
import tse.unblockt.ls.protocol.DiagnosticItem
import tse.unblockt.ls.server.threading.Cancellable

interface DiagnosticsProvider {
    context(Cancellable, KaSession)
    fun provide(document: Document, element: PsiElement): Iterable<DiagnosticItem>
}