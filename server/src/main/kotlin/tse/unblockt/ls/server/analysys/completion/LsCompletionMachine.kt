// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

package tse.unblockt.ls.server.analysys.completion

import tse.unblockt.ls.protocol.CompletionItem
import tse.unblockt.ls.protocol.CompletionList
import tse.unblockt.ls.protocol.CompletionParams
import tse.unblockt.ls.server.threading.Cancellable

interface LsCompletionMachine {
    context(Cancellable)
    suspend fun launchCompletion(params: CompletionParams): CompletionList
    fun resolve(item: CompletionItem): CompletionItem
}