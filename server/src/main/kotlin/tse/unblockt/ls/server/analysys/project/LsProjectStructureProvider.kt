// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

package tse.unblockt.ls.server.analysys.project

import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KaPlatformInterface
import org.jetbrains.kotlin.analysis.api.platform.projectStructure.KotlinProjectStructureProviderBase
import org.jetbrains.kotlin.analysis.api.projectStructure.KaBuiltinsModule
import org.jetbrains.kotlin.analysis.api.projectStructure.KaLibraryModule
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModule
import org.jetbrains.kotlin.analysis.api.projectStructure.KaNotUnderContentRootModule
import org.jetbrains.kotlin.analysis.low.level.api.fir.LLFirInternals
import org.jetbrains.kotlin.analysis.low.level.api.fir.projectStructure.LLFirBuiltinsSessionFactory
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms
import org.jetbrains.kotlin.serialization.deserialization.builtins.BuiltInSerializerProtocol
import tse.unblockt.ls.server.analysys.project.module.LsNotUnderContentRootModuleImpl

internal class LsProjectStructureProvider(
    private val platform: TargetPlatform,
    val project: Project,
    initialModules: List<KaModule>,
) : KotlinProjectStructureProviderBase() {
    private val ktNotUnderContentRootModuleWithoutPsiFile by lazy {
        LsNotUnderContentRootModuleImpl(
            name = "unnamed-outside-content-root",
            moduleDescription = "Standalone-not-under-content-root-module-without-psi-file",
            project = project,
        )
    }

    val allKtModules = initialModules

    @OptIn(KaPlatformInterface::class, LLFirInternals::class)
    private val builtinsModule: KaBuiltinsModule by lazy {
        LLFirBuiltinsSessionFactory.getInstance(project).getBuiltinsSession(platform).ktModule as KaBuiltinsModule
    }

    @OptIn(KaPlatformInterface::class)
    override fun getNotUnderContentRootModule(project: Project): KaNotUnderContentRootModule {
        return ktNotUnderContentRootModuleWithoutPsiFile
    }

    @OptIn(KaPlatformInterface::class)
    override fun getModule(element: PsiElement, useSiteModule: KaModule?): KaModule {
        val containingFile = element.containingFile
            ?: return ktNotUnderContentRootModuleWithoutPsiFile

        val virtualFile = containingFile.virtualFile
        if (virtualFile != null && virtualFile.extension == BuiltInSerializerProtocol.BUILTINS_FILE_EXTENSION) {
            return builtinsModule
        }

        val special = computeSpecialModule(containingFile)
        if (special != null) {
            return special
        }

        if (virtualFile == null) {
            if (containingFile.fileType == JavaFileType.INSTANCE) {
                val jdkModule =
                    allKtModules.filterIsInstance<KaLibraryModule>().firstOrNull { it.libraryName == "JDK" }
                if (jdkModule != null) {
                    return jdkModule
                }
            }
            throw IllegalStateException("Can't find module for $containingFile")
        }

        return allKtModules.firstOrNull { module -> virtualFile in module.contentScope } ?: getNotUnderContentRootModule(project)
    }

    class Builder(private val project: Project) {
        private val modules = mutableListOf<KaModule>()

        fun build(): LsProjectStructureProvider {
            return LsProjectStructureProvider(JvmPlatforms.defaultJvmPlatform, project, modules)
        }

        fun addModule(module: KaModule) {
            modules.add(module)
        }
    }
}