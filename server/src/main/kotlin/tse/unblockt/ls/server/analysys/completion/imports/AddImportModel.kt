// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

package tse.unblockt.ls.server.analysys.completion.imports

import com.intellij.openapi.util.TextRange

data class AddImportModel(
    val packageStatement: TextRange?,
    val imports: TextRange?,
    val addLineBreakBefore: Boolean,
    val addLineBreakAfter: Boolean,
    val offsetToAddImports: Int
)