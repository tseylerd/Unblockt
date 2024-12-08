// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package tse.unblockt.ls.server.analysys.completion.imports.ij

import org.jetbrains.kotlin.analysis.api.components.KaScopeWithKind
import org.jetbrains.kotlin.analysis.api.scopes.KaScope
import org.jetbrains.kotlin.analysis.api.symbols.KaClassifierSymbol
import org.jetbrains.kotlin.name.Name

internal class HierarchicalScope private constructor(private val scopes: List<KaScope>) {
    fun findClassifiers(name: Name): Sequence<List<KaClassifierSymbol>> {
        return scopes.asSequence()
            .map { it.classifiers(name).toList() }
            .filter { it.isNotEmpty() }
    }

    companion object {
        fun createFrom(scopes: List<KaScopeWithKind>): HierarchicalScope {
            val scopesSorted = scopes.sortedBy { it.kind.indexInTower }.map { it.scope }

            return HierarchicalScope(scopesSorted)
        }
    }
}