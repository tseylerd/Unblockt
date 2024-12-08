// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

package tse.unblockt.ls.server.analysys.completion.provider

import org.jetbrains.kotlin.analysis.api.KaSession
import tse.unblockt.ls.protocol.CompletionItem
import tse.unblockt.ls.server.analysys.completion.LsCompletionRequest
import tse.unblockt.ls.server.threading.Cancellable

interface CompletionItemsProvider {
    context(KaSession, Cancellable)
    fun provide(request: LsCompletionRequest): Sequence<CompletionItem>
}