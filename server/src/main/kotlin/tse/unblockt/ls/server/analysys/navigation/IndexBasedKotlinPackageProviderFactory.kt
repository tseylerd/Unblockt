// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

package tse.unblockt.ls.server.analysys.navigation

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.analysis.api.platform.packages.KotlinPackageProvider
import org.jetbrains.kotlin.analysis.api.platform.packages.KotlinPackageProviderBase
import org.jetbrains.kotlin.analysis.api.platform.packages.KotlinPackageProviderFactory
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import tse.unblockt.ls.server.analysys.index.LsSourceCodeIndexer
import tse.unblockt.ls.server.analysys.index.machines.KtPackageIndexMachine
import tse.unblockt.ls.server.analysys.storage.PersistentStorage

class IndexBasedKotlinPackageProviderFactory(private val project: Project): KotlinPackageProviderFactory {
    override fun createPackageProvider(searchScope: GlobalSearchScope): KotlinPackageProvider {
        return PackageProvider(project, searchScope)
    }

    class PackageProvider(project: Project, scope: GlobalSearchScope) : KotlinPackageProviderBase(project, scope) {
        private val indexer by lazy {
            LsSourceCodeIndexer.instance(project)
        }

        private val storage by lazy {
            PersistentStorage.instance(project)
        }

        private val packages = mutableSetOf<FqName>()

        private val kotlinPackageToSubPackages: Map<FqName, Set<Name>> by lazy {
            // unused
            emptyMap()
        }

        override fun doesKotlinOnlyPackageExist(packageFqName: FqName): Boolean {
            if (packageFqName.isRoot || packageFqName in packages) {
                return true
            }
            val machine = indexer[KtPackageIndexMachine::class]
            // we don't check for scope here in order to be faster: this method is heavily called
            val contains = storage.exists(machine.namespace, machine.attribute, packageFqName.asString())
            if (contains) {
                packages += packageFqName
            }
            return contains
        }

        override fun getKotlinOnlySubPackagesFqNames(packageFqName: FqName, nameFilter: (Name) -> Boolean): Set<Name> {
            return kotlinPackageToSubPackages[packageFqName]?.filterTo(mutableSetOf()) { nameFilter(it) } ?: emptySet()
        }
    }
}