// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package tse.unblockt.ls.server.analysys.completion.provider.ij

import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
import tse.unblockt.ls.server.analysys.completion.LsCompletionRequest
import tse.unblockt.ls.server.analysys.completion.imports.AutoImportAction

data class CallableInsertionOptions(
    val action: AutoImportAction,
    val insertionStrategy: CallableInsertionStrategy,
) {
    fun withImportingStrategy(newImportStrategy: AutoImportAction): CallableInsertionOptions =
        copy(action = newImportStrategy)
}

context(KaSession)
internal fun detectCallableOptions(request: LsCompletionRequest, symbol: KaCallableSymbol): CallableInsertionOptions {
    return CallableInsertionOptions(
        action = AutoImportAction.ofCallable(request, symbol),
        insertionStrategy = when (symbol) {
            is KaNamedFunctionSymbol -> CallableInsertionStrategy.AsCall
            else -> CallableInsertionStrategy.AsIdentifier
        }
    )
}
