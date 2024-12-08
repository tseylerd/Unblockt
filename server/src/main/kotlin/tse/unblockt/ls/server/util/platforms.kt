// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

package tse.unblockt.ls.server.util

import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.analysisContext

val KtElement.platform: TargetPlatform
    get() = calculateTargetPlatform(containingKtFile) ?: JvmPlatforms.defaultJvmPlatform

private fun calculateTargetPlatform(file: KtFile): TargetPlatform? {
    val context = file.analysisContext
    if (context != null) {
        return when (val contextFile = context.containingFile) {
            is KtFile -> return calculateTargetPlatform(contextFile)
            else -> JvmPlatforms.unspecifiedJvmPlatform
        }
    }

    return JvmPlatforms.defaultJvmPlatform
}

