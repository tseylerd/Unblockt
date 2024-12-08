// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

package tse.unblockt.ls.server.analysys.navigation

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.analysis.api.platform.declarations.KotlinDeclarationProvider
import org.jetbrains.kotlin.analysis.api.platform.declarations.KotlinDeclarationProviderFactory
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModule
import tse.unblockt.ls.server.analysys.index.LsKotlinPsiIndex

class LsKotlinDeclarationProviderFactory(project: Project): KotlinDeclarationProviderFactory {
    private val index: LsKotlinPsiIndex by lazy {
        LsKotlinPsiIndex.instance(project)
    }

    override fun createDeclarationProvider(scope: GlobalSearchScope, contextualModule: KaModule?): KotlinDeclarationProvider {
        return LsKotlinDeclarationProvider(index, scope)
    }
}