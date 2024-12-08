// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package tse.unblockt.ls.server.analysys.completion.provider.ij

import org.jetbrains.kotlin.analysis.api.components.KaScopeKind

internal sealed class CompletionSymbolOrigin {
    class Scope(val kind: KaScopeKind) : CompletionSymbolOrigin()

    data object Index : CompletionSymbolOrigin()

    companion object {
        const val SCOPE_OUTSIDE_TOWER_INDEX: Int = -1
    }
}