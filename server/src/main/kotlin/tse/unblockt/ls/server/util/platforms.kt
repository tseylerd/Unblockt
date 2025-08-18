// Copyright 2024 Dmitrii Tseiler. Licensed under the Apache 2.0 Licence

package tse.unblockt.ls.server.util

import org.jetbrains.kotlin.analysis.api.platform.projectStructure.KotlinProjectStructureProvider
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.psi.KtElement

val KtElement.platform: TargetPlatform
    get() {
        val module = KotlinProjectStructureProvider.getInstance(containingKtFile.project).getModule(containingKtFile, null)
        return module.targetPlatform
    }

