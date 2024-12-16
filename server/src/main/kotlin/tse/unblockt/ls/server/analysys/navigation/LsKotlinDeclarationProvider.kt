// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

package tse.unblockt.ls.server.analysys.navigation

import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.analysis.api.platform.declarations.KotlinDeclarationProvider
import org.jetbrains.kotlin.fileClasses.javaFileFacadeFqName
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import tse.unblockt.ls.server.analysys.index.LsKotlinPsiIndex
import tse.unblockt.ls.server.analysys.storage.UniversalCache
import java.util.concurrent.CompletableFuture

class LsKotlinDeclarationProvider(
    private val index: LsKotlinPsiIndex,
    internal val scope: GlobalSearchScope,
    private val cache: UniversalCache,
) : KotlinDeclarationProvider {
    companion object {
        private val ourTopLevelClassifiersPackagesKey = UniversalCache.Key<Set<String>>("ourTopLevelClassifiersPackagesKey")
        private val ourTopLevelCallablesPackagesKey = UniversalCache.Key<Set<String>>("ourTopLevelCallablesPackagesKey")
    }

    override val hasSpecificClassifierPackageNamesComputation: Boolean = true
    override val hasSpecificCallablePackageNamesComputation: Boolean = true

    private val KtElement.inScope: Boolean
        get() = containingKtFile.virtualFile in scope

    override fun getClassLikeDeclarationByClassId(classId: ClassId): KtClassLikeDeclaration? {
        return getAllClassesByClassId(classId).firstOrNull()
            ?: getAllTypeAliasesByClassId(classId).firstOrNull()
    }

    override fun getAllClassesByClassId(classId: ClassId): Collection<KtClassOrObject> {
        val allClassesByClassIdSync = index.getAllClassesByClassId(classId)
        return allClassesByClassIdSync.filterIsInstance<KtClassOrObject>().filter { ktClassOrObject ->
            ktClassOrObject.inScope
        }.toList()
    }

    override fun getAllTypeAliasesByClassId(classId: ClassId): Collection<KtTypeAlias> {
        return index.getAllClassesByClassId(classId).filterIsInstance<KtTypeAlias>().filter { ktTypeAlias ->
            ktTypeAlias.inScope
        }.toList()
    }

    override fun getTopLevelKotlinClassLikeDeclarationNamesInPackage(packageFqName: FqName): Set<Name> {
        return index.getTopLevelClassLikesInPackage(packageFqName)
            .filter { it.inScope }.mapNotNull { it.nameAsName }
            .toSet()
    }

    override fun getTopLevelCallableNamesInPackage(packageFqName: FqName): Set<Name> {
        val classLikes = CompletableFuture.supplyAsync {
            index.getTopLevelFunctionsInPackage(packageFqName)
                .filter { it.inScope }.mapNotNull { it.nameAsName }.toSet()
        }
        val aliases = CompletableFuture.supplyAsync {
            index.getTopLevelPropertiesInPackage(packageFqName)
                .filter { it.inScope }.mapNotNull { it.nameAsName }.toSet()
        }
        return classLikes.get() + aliases.get()
    }

    override fun findFilesForFacadeByPackage(packageFqName: FqName): Collection<KtFile> {
        return index.getFilesInPackage(packageFqName).filter { it.virtualFile in scope }.toList()
    }

    override fun findFilesForFacade(facadeFqName: FqName): Collection<KtFile> {
        if (facadeFqName.shortNameOrSpecial().isSpecial) return emptyList()
        return findFilesForFacadeByPackage(facadeFqName.parent()).filter { it.javaFileFacadeFqName == facadeFqName }
    }

    override fun findInternalFilesForFacade(facadeFqName: FqName): Collection<KtFile> {
        return index.getInternalFilesInPackage(facadeFqName).filter { it.virtualFile in scope }.toList()
    }

    override fun findFilesForScript(scriptFqName: FqName): Collection<KtScript> {
        return index.getScripts(scriptFqName).filter { it.containingKtFile.virtualFile in scope }.toList()
    }

    override fun computePackageNamesWithTopLevelClassifiers(): Set<String> {
        return cache.getOrCompute(ourTopLevelClassifiersPackagesKey) {
            buildPackageNamesSetFrom(index.getAllPackagesWithTopLevelClassesOrTypeAliases())
        }
    }

    override fun computePackageNamesWithTopLevelCallables(): Set<String> {
        return cache.getOrCompute(ourTopLevelCallablesPackagesKey) {
            buildPackageNamesSetFrom(index.getAllPackagesWithTopLevelCallables())
        }
    }

    override fun getTopLevelProperties(callableId: CallableId): Collection<KtProperty> {
        return index.getTopLevelPropertiesInPackage(callableId.packageName).filter { ktProperty ->
            ktProperty.nameAsName == callableId.callableName && ktProperty.inScope
        }.toList()
    }

    override fun getTopLevelFunctions(callableId: CallableId): Collection<KtNamedFunction> {
        return index.getTopLevelFunctionsInPackage(callableId.packageName).filter { ktNamedFunction ->
            ktNamedFunction.nameAsName == callableId.callableName && ktNamedFunction.inScope
        }.toList()
    }

    private fun buildPackageNamesSetFrom(vararg fqNameSets: Sequence<FqName>): Set<String> {
        return fqNameSets.flatMap { it.map { fq -> fq.asString() } }.toSet()
    }

    override fun getTopLevelCallableFiles(callableId: CallableId): Collection<KtFile> = buildSet {
        getTopLevelProperties(callableId).mapTo(this) { it.containingKtFile }
        getTopLevelFunctions(callableId).mapTo(this) { it.containingKtFile }
    }
}
