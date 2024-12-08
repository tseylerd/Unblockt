// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

package tse.unblockt.ls.server.analysys.navigation

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.impl.VirtualFileEnumeration
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaPlatformInterface
import org.jetbrains.kotlin.analysis.api.platform.packages.KotlinPackageProvider
import org.jetbrains.kotlin.analysis.api.platform.packages.KotlinPackageProviderBase
import org.jetbrains.kotlin.analysis.api.platform.packages.KotlinPackageProviderFactory
import org.jetbrains.kotlin.analysis.api.platform.projectStructure.KotlinProjectStructureProvider
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtFile
import tse.unblockt.ls.server.analysys.index.LsPsiCache
import tse.unblockt.ls.server.analysys.index.LsSourceCodeIndexer
import tse.unblockt.ls.server.analysys.index.machines.KtFileByFqNameIndexMachine
import tse.unblockt.ls.server.analysys.project.LsProjectStructureProvider
import tse.unblockt.ls.server.analysys.project.module.LsLibraryModule
import tse.unblockt.ls.server.analysys.project.module.LsSourceModule
import tse.unblockt.ls.server.analysys.storage.PersistentStorage

class IndexBasedKotlinPackageProviderFactory(private val project: Project): KotlinPackageProviderFactory {
    override fun createPackageProvider(searchScope: GlobalSearchScope): KotlinPackageProvider {
        return PackageProvider(project, searchScope)
    }

    class PackageProvider(project: Project, scope: GlobalSearchScope) : KotlinPackageProviderBase(project, scope) {
        private val provider by lazy {
            KotlinProjectStructureProvider.getInstance(project) as LsProjectStructureProvider
        }

        private val indexer by lazy {
            LsSourceCodeIndexer.instance(project)
        }

        private val storage by lazy {
            PersistentStorage.instance(project)
        }

        private val packages = mutableSetOf<FqName>()

        @Suppress("UnstableApiUsage")
        private val kotlinPackageToSubPackages: Map<FqName, Set<Name>> by lazy {
            val extracted = VirtualFileEnumeration.extract(scope)
            val scopedFiles = when {
                extracted == null || extracted.filesIfCollection == null -> getAllFiles().filter {
                    it in scope
                }
                else -> extracted.filesIfCollection!!.asSequence().filterNotNull()
            }
            val psiManager = LsPsiCache.instance(project)
            val filesInScope = scopedFiles.map { psiManager[it] }
                .filterIsInstance<KtFile>()
                .map { it.packageFqName }

            val packages: MutableMap<FqName, MutableSet<Name>> = mutableMapOf()
            for (fqName in filesInScope) {
                var currentPackage = FqName.ROOT
                for (subPackage in fqName.pathSegments()) {
                    packages.getOrPut(currentPackage) { mutableSetOf() } += subPackage
                    currentPackage = currentPackage.child(subPackage)
                }
                packages.computeIfAbsent(currentPackage) { mutableSetOf() }
            }
            packages
        }

        override fun doesKotlinOnlyPackageExist(packageFqName: FqName): Boolean {
            if (packageFqName.isRoot || packageFqName in packages) {
                return true
            }
            val machine = indexer[KtFileByFqNameIndexMachine::class]
            val contains = storage.getSequence(machine.namespace, machine.attribute, packageFqName).any { entry ->
                val file = entry.psiFile.virtualFile
                file != null && file in searchScope
            }
            if (contains) {
                packages += packageFqName
            }
            return contains
        }

        override fun getKotlinOnlySubPackagesFqNames(packageFqName: FqName, nameFilter: (Name) -> Boolean): Set<Name> {
            return kotlinPackageToSubPackages[packageFqName]?.filterTo(mutableSetOf()) { nameFilter(it) } ?: emptySet()
        }

        @OptIn(KaPlatformInterface::class, KaExperimentalApi::class)
        private fun getAllFiles(): Sequence<VirtualFile> {
            return provider.allKtModules.asSequence().flatMap { m ->
                when (m) {
                    is LsSourceModule -> m.contentScope.allFilesInModule
                    is LsLibraryModule -> m.contentScope.allFilesInModule
                    else -> emptySet()
                }
            }
        }
    }
}