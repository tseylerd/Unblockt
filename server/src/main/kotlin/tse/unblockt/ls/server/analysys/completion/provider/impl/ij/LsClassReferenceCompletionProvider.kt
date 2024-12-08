// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package tse.unblockt.ls.server.analysys.completion.provider.impl.ij

import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.platform.jvm.isJvm
import tse.unblockt.ls.protocol.CompletionItem
import tse.unblockt.ls.server.analysys.completion.LsCompletionRequest
import tse.unblockt.ls.server.analysys.completion.provider.CompletionItemsProvider
import tse.unblockt.ls.server.analysys.completion.util.KeywordCompletionItem
import tse.unblockt.ls.server.analysys.completion.util.ij.KotlinCallableReferencePositionContext
import tse.unblockt.ls.server.threading.Cancellable

internal class LsClassReferenceCompletionProvider : CompletionItemsProvider {
    context(KaSession, Cancellable)
    override fun provide(request: LsCompletionRequest): Sequence<CompletionItem> {
        val positionContext = request.positionContext as? KotlinCallableReferencePositionContext ?: return emptySequence()
        if (positionContext.explicitReceiver == null) return emptySequence()
        return sequence {
            cancellationPoint()
            yield(KeywordCompletionItem("class"))
            if (request.platform.isJvm()) {
                cancellationPoint()
                yield(KeywordCompletionItem("class.java"))
            }
        }
    }
}
