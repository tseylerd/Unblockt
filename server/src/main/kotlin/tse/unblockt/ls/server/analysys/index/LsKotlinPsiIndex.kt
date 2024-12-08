// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

package tse.unblockt.ls.server.analysys.index

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*

interface LsKotlinPsiIndex {
    companion object {
        fun instance(project: Project) = project.getService(LsKotlinPsiIndex::class.java)
    }

    val builtIns: Collection<VirtualFile>

    fun getAllClassesByClassId(classId: ClassId): Sequence<KtClassLikeDeclaration>
    fun getTopLevelClassLikesInPackage(fqName: FqName): Sequence<KtClassLikeDeclaration>
    fun getTopLevelClassLikes(): Sequence<KtClassLikeDeclaration>
    fun getTopLevelFunctionsInPackage(fqName: FqName): Sequence<KtNamedFunction>
    fun getTopLevelPropertiesInPackage(fqName: FqName): Sequence<KtProperty>
    fun getFilesInPackage(fqName: FqName): Sequence<KtFile>
    fun getInternalFilesInPackage(fqName: FqName): Sequence<KtFile>
    fun getScripts(fqName: FqName): Sequence<KtScript>
    fun getAllPackagesWithTopLevelClassesOrTypeAliases(): Sequence<FqName>
    fun getAllPackagesWithTopLevelCallables(): Sequence<FqName>
    fun getTopLevelCallableDeclarations(scope: GlobalSearchScope, filter: (String) -> Boolean): Sequence<KtCallableDeclaration>
    fun getAllCallablesDeclarationByReceiverType(type: Name): Sequence<KtCallableDeclaration>
}