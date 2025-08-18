// Copyright 2024 Dmitrii Tseiler. Licensed under the Apache 2.0 Licence

package tse.unblockt.ls.server.analysys.navigation

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.analysis.api.platform.declarations.KotlinCompositeDeclarationProvider
import org.jetbrains.kotlin.analysis.api.platform.declarations.KotlinDeclarationProvider
import org.jetbrains.kotlin.analysis.api.platform.declarations.KotlinDeclarationProviderMerger
import org.jetbrains.kotlin.analysis.api.platform.declarations.createDeclarationProvider
import org.jetbrains.kotlin.analysis.api.platform.mergeSpecificProviders

class IncrementalDeclarationProviderMerger(private val project: Project) : KotlinDeclarationProviderMerger {
    override fun merge(providers: List<KotlinDeclarationProvider>): KotlinDeclarationProvider =
        providers.mergeSpecificProviders<_, LsKotlinDeclarationProvider>(KotlinCompositeDeclarationProvider.factory) { targetProviders ->
            project.createDeclarationProvider(GlobalSearchScope.union(targetProviders.map { it.scope }), contextualModule = null)
        }
}
