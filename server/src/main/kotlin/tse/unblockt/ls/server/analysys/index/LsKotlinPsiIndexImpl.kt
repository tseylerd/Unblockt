// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

package tse.unblockt.ls.server.analysys.index

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.analysis.decompiler.psi.BuiltinsVirtualFileProvider
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import tse.unblockt.ls.server.analysys.index.machines.*
import tse.unblockt.ls.server.analysys.storage.PersistentStorage

class LsKotlinPsiIndexImpl(private val project: Project): LsKotlinPsiIndex {
    private val indexer by lazy {
        LsSourceCodeIndexer.instance(project)
    }
    private val storage by lazy {
        PersistentStorage.instance(project)
    }

    override val builtIns: Collection<VirtualFile> = BuiltinsVirtualFileProvider.getInstance().getBuiltinVirtualFiles()

    override fun getAllClassesByClassId(classId: ClassId): Sequence<KtClassLikeDeclaration> {
        val index = indexer[KtClassIdToClassIndexMachine::class]
        return storage.getSequence(Namespaces.ourKotlinNamespace, index.attribute, classId.asString()).map { it.element }
    }

    override fun getTopLevelClassLikesInPackage(fqName: FqName): Sequence<KtClassLikeDeclaration> {
        val index = indexer[KtPackageToTopLevelClassIndexMachine::class]
        return storage.getSequence(Namespaces.ourKotlinNamespace, index.attribute, fqName.asString())
            .map { it.element }
    }

    override fun getTopLevelClassLikes(): Sequence<KtClassLikeDeclaration> {
        val index = indexer[KtPackageToTopLevelClassIndexMachine::class]
        return storage.getSequenceOfValues(Namespaces.ourKotlinNamespace, index.attribute)
            .map { v ->
                v.element
            }
    }

    override fun getTopLevelFunctionsInPackage(fqName: FqName): Sequence<KtNamedFunction> {
        val index = indexer[KtPackageToTopLevelFunctionIndexMachine::class]
        return storage.getSequence(Namespaces.ourKotlinNamespace, index.attribute, fqName.asString()).map { it.element }
    }

    override fun getTopLevelPropertiesInPackage(fqName: FqName): Sequence<KtProperty> {
        val index = indexer[KtPackageToTopLevelPropertyIndexMachine::class]
        return storage.getSequence(Namespaces.ourKotlinNamespace, index.attribute, fqName.asString()).map { it.element }
    }

    override fun getFilesInPackage(fqName: FqName): Sequence<KtFile> {
        return storage.getSequence(Namespaces.ourKotlinNamespace, indexer[KtFileByFqNameIndexMachine::class].attribute, fqName.asString())
            .map { it.psiFile }
            .filterIsInstance<KtFile>()
    }

    override fun getInternalFilesInPackage(fqName: FqName): Sequence<KtFile> {
        // todo
        return emptySequence()
    }

    override fun getScripts(fqName: FqName): Sequence<KtScript> {
        // todo
        return emptySequence()
    }

    override fun getAllPackagesWithTopLevelClassesOrTypeAliases(): Sequence<FqName> {
        val machine = indexer[KtPackageToTopLevelClassIndexMachine::class]
        return storage.getSequenceOfKeys(Namespaces.ourKotlinNamespace, machine.attribute).map { key ->
            FqName(key)
        }
    }

    override fun getAllPackagesWithTopLevelCallables(): Sequence<FqName> {
        val attribute = indexer[KtPackageToTopLevelCallableIndexMachine::class].attribute
        return storage.getSequenceOfKeys(Namespaces.ourKotlinNamespace, attribute).map { key ->
            FqName(key)
        }
    }

    override fun getTopLevelCallableDeclarations(scope: GlobalSearchScope, filter: (String) -> Boolean): Sequence<KtCallableDeclaration> {
        val machine = indexer[KtCallableNameToTopLevelCallableIndexMachine::class]
        return storage.getSequence(machine.namespace, machine.attribute).mapNotNull { (key, value) ->
            val inScope = value.element.containingFile.virtualFile in scope
            if (!inScope) {
                return@mapNotNull null
            }
            if (!filter(key)) {
                return@mapNotNull null
            }
            value.element
        }
    }

    override fun getAllCallablesDeclarationByReceiverType(type: Name): Sequence<KtCallableDeclaration> {
        val attribute = indexer[KtReceiverToExtensionIndexMachine::class].attribute
        return storage.getSequence(Namespaces.ourKotlinNamespace, attribute, type.asString()).map { it.element }
    }
}