// Copyright 2024 Dmitrii Tseiler. Licensed under the Apache 2.0 Licence

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