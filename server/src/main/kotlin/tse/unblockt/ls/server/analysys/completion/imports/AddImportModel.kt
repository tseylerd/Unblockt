// Copyright 2024 Dmitrii Tseiler. Licensed under the Apache 2.0 Licence

package tse.unblockt.ls.server.analysys.completion.imports

import com.intellij.openapi.util.TextRange

data class AddImportModel(
    val packageStatement: TextRange?,
    val imports: TextRange?,
    val addLineBreakBefore: Boolean,
    val addLineBreakAfter: Boolean,
    val offsetToAddImports: Int
)