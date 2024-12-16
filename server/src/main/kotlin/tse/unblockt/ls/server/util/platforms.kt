// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

package tse.unblockt.ls.server.util

import org.jetbrains.kotlin.analysis.api.platform.projectStructure.KotlinProjectStructureProvider
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.psi.KtElement

val KtElement.platform: TargetPlatform
    get() {
        val module = KotlinProjectStructureProvider.getInstance(containingKtFile.project).getModule(containingKtFile, null)
        return module.targetPlatform
    }

