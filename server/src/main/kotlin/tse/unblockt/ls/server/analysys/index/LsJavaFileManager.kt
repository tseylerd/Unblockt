// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

@file:Suppress("UnstableApiUsage")

package tse.unblockt.ls.server.analysys.index

import com.intellij.core.CoreJavaFileManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiJavaModule
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiPackage
import com.intellij.psi.impl.file.PsiPackageImpl
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.load.java.JavaClassFinder
import org.jetbrains.kotlin.load.java.structure.JavaClass
import org.jetbrains.kotlin.load.java.structure.impl.JavaClassImpl
import org.jetbrains.kotlin.load.java.structure.impl.source.JavaElementSourceFactory
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.resolve.jvm.KotlinCliJavaFileManager
import tse.unblockt.ls.server.analysys.index.machines.JavaClassIdToClassIndexMachine
import tse.unblockt.ls.server.analysys.index.machines.JavaPackageToTopLevelClassIndexMachine
import tse.unblockt.ls.server.analysys.index.machines.PackageIndexMachine
import tse.unblockt.ls.server.analysys.index.model.PsiEntry
import tse.unblockt.ls.server.analysys.storage.PersistentStorage

class LsJavaFileManager(private val project: Project): KotlinCliJavaFileManager, CoreJavaFileManager(PsiManager.getInstance(project)) {
    private val manager by lazy {
        PsiManager.getInstance(project)
    }
    private val indexer by lazy {
        LsSourceCodeIndexer.instance(project)
    }
    private val storage by lazy {
        PersistentStorage.instance(project)
    }

    override fun findPackage(p0: String): PsiPackage? {
        val machine = indexer[PackageIndexMachine::class]
        if (!storage.exists(machine.namespace, machine.attribute, p0)) {
            return null
        }
        return object : PsiPackageImpl(manager, p0) {
            override fun isValid(): Boolean = true
        }
    }

    override fun findClass(request: JavaClassFinder.Request, searchScope: GlobalSearchScope): JavaClass? {
        val p0 = request.classId.asString()
        val machine = indexer[JavaClassIdToClassIndexMachine::class]
        val clazz = storage.getSequence(machine.namespace, machine.attribute, p0)
            .filter { it.element.containingFile.virtualFile in searchScope }
            .map { it.element }
            .firstOrNull() ?: return null
        return createJavaClassByPsiClass(clazz)
    }

    private fun createJavaClassByPsiClass(psiClass: PsiClass): JavaClassImpl {
        val sourceFactory = JavaElementSourceFactory.getInstance(project)
        return JavaClassImpl(sourceFactory.createPsiSource(psiClass))
    }

    override fun findClass(p0: String, p1: GlobalSearchScope): PsiClass? {
        return findClasses(p0, p1).firstOrNull()
    }

    override fun findClasses(p0: String, p1: GlobalSearchScope): Array<PsiClass> {
        val machine = indexer[JavaClassIdToClassIndexMachine::class]
        forEachClassId(p0) { classId ->
            val classes = storage.getSequence(machine.namespace, machine.attribute, classId.asString())
                .filter { it.element.containingFile.virtualFile in p1 }
                .map { it.element }
                .toList()
            if (classes.isNotEmpty()) {
                return classes.toTypedArray()
            }
        }
        return emptyArray()
    }

    override fun getNonTrivialPackagePrefixes(): Collection<String> = emptyList()

    override fun knownClassNamesInPackage(packageFqName: FqName): Set<String> {
        val machine = indexer[JavaPackageToTopLevelClassIndexMachine::class]
        return storage.getSequence(machine.namespace, machine.attribute, packageFqName.asString()).mapNotNull { el: PsiEntry<PsiClass> -> el.element.name }.toSet()
    }

    override fun findModules(p0: String, p1: GlobalSearchScope): Collection<PsiJavaModule> {
        return emptySet()
    }

    private inline fun forEachClassId(fqName: String, block: (ClassId) -> Unit) {
        var classId = ClassId.topLevel(FqName(fqName))

        while (true) {
            block(classId)

            val packageFqName = classId.packageFqName
            if (packageFqName.isRoot) break

            classId = ClassId(
                packageFqName.parent(),
                FqName(packageFqName.shortName().asString() + "." + classId.relativeClassName.asString()),
                isLocal = false
            )
        }
    }
}