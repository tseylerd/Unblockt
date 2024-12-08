// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

@file:Suppress("UnstableApiUsage")

package tse.unblockt.ls.server.analysys

import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.impl.DocumentWriteAccessGuard

class LsDocumentWriteAccessGuard: DocumentWriteAccessGuard() {
    @Suppress("RemoveRedundantQualifierName")
    override fun isWritable(p0: Document): DocumentWriteAccessGuard.Result {
        throw UnsupportedOperationException("not implemented")
    }
}