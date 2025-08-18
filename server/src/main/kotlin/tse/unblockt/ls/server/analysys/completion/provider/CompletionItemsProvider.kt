// Copyright 2024 Dmitrii Tseiler. Licensed under the Apache 2.0 Licence

package tse.unblockt.ls.server.analysys.completion.provider

import org.jetbrains.kotlin.analysis.api.KaSession
import tse.unblockt.ls.protocol.CompletionItem
import tse.unblockt.ls.server.analysys.completion.LsCompletionRequest
import tse.unblockt.ls.server.threading.Cancellable

interface CompletionItemsProvider {
    context(KaSession, Cancellable)
    fun provide(request: LsCompletionRequest): Sequence<CompletionItem>
}