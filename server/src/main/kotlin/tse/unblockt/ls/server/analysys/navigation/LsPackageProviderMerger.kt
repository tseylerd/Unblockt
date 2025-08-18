// Copyright 2024 Dmitrii Tseiler. Licensed under the Apache 2.0 Licence

package tse.unblockt.ls.server.analysys.navigation

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.analysis.api.platform.mergeSpecificProviders
import org.jetbrains.kotlin.analysis.api.platform.packages.KotlinCompositePackageProvider
import org.jetbrains.kotlin.analysis.api.platform.packages.KotlinPackageProvider
import org.jetbrains.kotlin.analysis.api.platform.packages.KotlinPackageProviderMerger
import org.jetbrains.kotlin.analysis.api.platform.packages.createPackageProvider

class LsPackageProviderMerger(private val project: Project) : KotlinPackageProviderMerger {
    override fun merge(providers: List<KotlinPackageProvider>): KotlinPackageProvider =
        providers.mergeSpecificProviders<_, IndexBasedKotlinPackageProviderFactory.PackageProvider>(KotlinCompositePackageProvider.factory) { targetProviders ->
            project.createPackageProvider(GlobalSearchScope.union(targetProviders.map { it.searchScope }))
        }
}
