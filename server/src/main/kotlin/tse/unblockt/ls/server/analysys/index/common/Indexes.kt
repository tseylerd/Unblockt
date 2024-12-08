// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

package tse.unblockt.ls.server.analysys.index.common

import com.intellij.openapi.project.Project
import tse.unblockt.ls.server.analysys.index.LsJavaPsiIndex
import tse.unblockt.ls.server.analysys.index.LsJavaSymbolIndex
import tse.unblockt.ls.server.analysys.index.LsKotlinPsiIndex
import tse.unblockt.ls.server.analysys.index.LsKotlinSymbolIndex

data class Indexes(
    val kotlinPsi: LsKotlinPsiIndex,
    val javaPsi: LsJavaPsiIndex,
    val kotlinSymbol: LsKotlinSymbolIndex,
    val javaSymbol: LsJavaSymbolIndex
) {
    companion object {
        fun instance(project: Project): Indexes {
            return Indexes(
                LsKotlinPsiIndex.instance(project),
                LsJavaPsiIndex.instance(project),
                LsKotlinSymbolIndex.instance(project),
                LsJavaSymbolIndex.instance(project)
            )
        }
    }
}