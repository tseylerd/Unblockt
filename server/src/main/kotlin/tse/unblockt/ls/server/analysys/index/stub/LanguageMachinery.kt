// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

package tse.unblockt.ls.server.analysys.index.stub

import com.intellij.ide.highlighter.JavaClassFileType
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.components.service
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.*
import com.intellij.psi.impl.compiled.ClsFileImpl
import com.intellij.psi.impl.java.stubs.PsiJavaFileStub
import com.intellij.psi.impl.source.PsiJavaFileImpl
import com.intellij.psi.stubs.PsiFileStub
import com.intellij.psi.stubs.PsiFileStubImpl
import com.intellij.util.indexing.FileContent
import com.intellij.util.indexing.FileContentImpl
import org.jetbrains.kotlin.analysis.decompiler.konan.K2KotlinNativeMetadataDecompiler
import org.jetbrains.kotlin.analysis.decompiler.konan.KlibMetaFileType
import org.jetbrains.kotlin.analysis.decompiler.psi.BuiltinsVirtualFileProvider
import org.jetbrains.kotlin.analysis.decompiler.psi.KotlinBuiltInDecompiler
import org.jetbrains.kotlin.analysis.decompiler.psi.KotlinBuiltInFileType
import org.jetbrains.kotlin.analysis.decompiler.stub.file.ClsKotlinBinaryClassCache
import org.jetbrains.kotlin.analysis.decompiler.stub.file.KotlinClsStubBuilder
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.stubs.impl.KotlinFileStubImpl
import org.jetbrains.kotlin.serialization.deserialization.builtins.BuiltInSerializerProtocol

interface LanguageMachinery<S: PsiFileStub<*>> {
    fun supports(file: VirtualFile, cache: ClsKotlinBinaryClassCache): Boolean

    fun build(file: VirtualFile, cache: ClsKotlinBinaryClassCache): S?
    fun loadBuiltIns(): Collection<S>

    class Kotlin(private val project: Project): LanguageMachinery<KotlinFileStubImpl> {
        companion object {
            fun instance(project: Project): Kotlin {
                return project.service()
            }
        }

        private val psiManager = PsiManager.getInstance(project)

        override fun supports(file: VirtualFile, cache: ClsKotlinBinaryClassCache): Boolean {
            val fileContent = FileContentImpl.createByFile(file)
            val fileType = file.fileType
            return when {
                cache.isKotlinJvmCompiledFile(file, fileContent.content) && fileType == JavaClassFileType.INSTANCE -> true
                fileType == KotlinBuiltInFileType && file.extension != BuiltInSerializerProtocol.BUILTINS_FILE_EXTENSION -> true
                fileType == KlibMetaFileType -> true
                else -> false
            }
        }

        override fun build(file: VirtualFile, cache: ClsKotlinBinaryClassCache): KotlinFileStubImpl? {
            val fileContent = FileContentImpl.createByFile(file)
            val fileType = file.fileType
            val builtInDecompiler = KotlinBuiltInDecompiler()
            val stubBuilder = when {
                cache.isKotlinJvmCompiledFile(file, fileContent.content) && fileType == JavaClassFileType.INSTANCE -> KotlinClsStubBuilder()
                fileType == KotlinBuiltInFileType && file.extension != BuiltInSerializerProtocol.BUILTINS_FILE_EXTENSION -> builtInDecompiler.stubBuilder
                fileType == KlibMetaFileType -> K2KotlinNativeMetadataDecompiler().stubBuilder
                else -> null
            } ?: return null

            val kotlinFileStubImpl = stubBuilder.buildFileStub(fileContent) as? KotlinFileStubImpl
            if (kotlinFileStubImpl != null) {
                setFakePsi(psiManager, kotlinFileStubImpl, file, true)
            }
            return kotlinFileStubImpl
        }

        override fun loadBuiltIns(): Collection<KotlinFileStubImpl> {
            val builtInDecompiler = KotlinBuiltInDecompiler()
            return BuiltinsVirtualFileProvider.getInstance().getBuiltinVirtualFiles().mapNotNull { virtualFile ->
                createBuiltInsStub(virtualFile, builtInDecompiler)
            }
        }

        fun createBuiltInsStub(
            virtualFile: VirtualFile,
            builtInDecompiler: KotlinBuiltInDecompiler = KotlinBuiltInDecompiler()
        ): KotlinFileStubImpl? {
            val fileContent = FileContentImpl.createByFile(virtualFile, project)
            return createKtFileStub(psiManager, builtInDecompiler, fileContent)
        }

        private fun createKtFileStub(
            psiManager: PsiManager,
            builtInDecompiler: KotlinBuiltInDecompiler,
            fileContent: FileContent,
        ): KotlinFileStubImpl? {
            val ktFileStub = builtInDecompiler.stubBuilder.buildFileStub(fileContent) as? KotlinFileStubImpl ?: return null
            setFakePsi(psiManager, ktFileStub, fileContent.file, false)
            return ktFileStub
        }

        private fun setFakePsi(psiManager: PsiManager, stub: KotlinFileStubImpl, virtualFile: VirtualFile, physical: Boolean): KtFile {
            val fakeFile = object : KtFile(
                KtClassFileViewProvider(
                    psiManager,
                    virtualFile
                ), isCompiled = true) {
                override fun getStub() = stub
                override fun isPhysical() = physical
            }
            stub.psi = fakeFile
            return fakeFile
        }

        private class KtClassFileViewProvider(
            psiManager: PsiManager,
            virtualFile: VirtualFile,
        ) : SingleRootFileViewProvider(psiManager, virtualFile, true, KotlinLanguage.INSTANCE)
    }

    class Java(project: Project): LanguageMachinery<PsiFileStubImpl<*>> {
        companion object {
            fun instance(project: Project): Java {
                return project.service()
            }
        }

        private val psiManager = PsiManager.getInstance(project)

        override fun supports(file: VirtualFile, cache: ClsKotlinBinaryClassCache): Boolean {
            val fileContent = FileContentImpl.createByFile(file)
            val fileType = file.fileType
            val content = file.contentsToByteArray()
            return when {
                cache.isKotlinJvmCompiledFile(file, fileContent.content) && fileType == JavaClassFileType.INSTANCE -> false
                fileType == KotlinBuiltInFileType
                        && file.extension != BuiltInSerializerProtocol.BUILTINS_FILE_EXTENSION -> false
                fileType == KlibMetaFileType -> false
                fileType != JavaClassFileType.INSTANCE -> false
                ClassFileViewProvider.isInnerClass(file, content) -> false
                file.name == "module-info.class" -> false
                else -> true
            }
        }

        override fun build(file: VirtualFile, cache: ClsKotlinBinaryClassCache): PsiFileStubImpl<*>? {
            val fileContent = FileContentImpl.createByFile(file)
            val fileType = file.fileType
            val content = file.contentsToByteArray()
            when {
                cache.isKotlinJvmCompiledFile(file, fileContent.content) && fileType == JavaClassFileType.INSTANCE -> return null
                fileType == KotlinBuiltInFileType
                        && file.extension != BuiltInSerializerProtocol.BUILTINS_FILE_EXTENSION -> return null
                fileType == KlibMetaFileType -> return null
                fileType != JavaClassFileType.INSTANCE -> return null
                ClassFileViewProvider.isInnerClass(file, content) -> return null
                file.name == "module-info.class" -> return null
            }
            val stub: PsiJavaFileStub = ClsFileImpl.buildFileStub(file, content) ?: throw IllegalStateException("Can't build stub from ${file.path}")
            val packageName = stub.packageName
            if (packageName.startsWith("kotlin")) {
                return null
            }
            @Suppress("UNCHECKED_CAST")
            val psiFileStubImpl = stub as PsiFileStubImpl<PsiJavaFile>
            setFakePsi(psiManager, psiFileStubImpl, file)
            return psiFileStubImpl
        }

        override fun loadBuiltIns(): Collection<PsiFileStubImpl<*>> {
            return emptyList()
        }

        private fun setFakePsi(psiManager: PsiManager, stub: PsiFileStubImpl<PsiJavaFile>, virtualFile: VirtualFile): PsiFile {
            val fakeFile = object : PsiJavaFileImpl(
                SingleRootFileViewProvider(
                    psiManager,
                    virtualFile,
                    false,
                    JavaFileType.INSTANCE,
                )
            ) {
                override fun getStub() = stub
                override fun accept(p0: PsiElementVisitor) {
                    p0.visitFile(this)
                }

                override fun getVirtualFile(): VirtualFile {
                    return virtualFile
                }

                override fun isPhysical() = false
                override fun getFileType(): FileType = JavaFileType.INSTANCE
            }
            stub.psi = fakeFile
            return fakeFile
        }
    }
}
