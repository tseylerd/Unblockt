// Copyright 2024 Dmitrii Tseiler. Licensed under the Apache 2.0 Licence

package tse.unblockt.ls.server.analysys.navigation

import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.kotlin.analysis.api.platform.packages.KotlinPackagePartProviderFactory
import org.jetbrains.kotlin.load.kotlin.PackagePartProvider

internal class LsPackagePartProviderFactory(
    private val packagePartProvider: (GlobalSearchScope) -> PackagePartProvider,
) : KotlinPackagePartProviderFactory {
    private val cache = ContainerUtil.createConcurrentSoftMap<GlobalSearchScope, PackagePartProvider>()

    override fun createPackagePartProvider(scope: GlobalSearchScope): PackagePartProvider {
        return cache.getOrPut(scope) {
            packagePartProvider(scope)
        }
    }
}