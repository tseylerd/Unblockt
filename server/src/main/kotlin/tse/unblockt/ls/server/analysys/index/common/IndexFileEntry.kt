// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

package tse.unblockt.ls.server.analysys.index.common

import com.intellij.psi.PsiFile
import com.intellij.psi.stubs.PsiFileStub

data class IndexFileEntry(
    val psiFile: PsiFile,
    val stub: PsiFileStub<*>?,
    val isKotlin: Boolean,
    val builtIns: Boolean,
)
