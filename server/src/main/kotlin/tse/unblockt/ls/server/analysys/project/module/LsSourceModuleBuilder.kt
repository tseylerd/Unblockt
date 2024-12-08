// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

package tse.unblockt.ls.server.analysys.project.module

import com.intellij.openapi.vfs.VirtualFileManager
import org.jetbrains.kotlin.analysis.api.projectStructure.KaSourceModule
import org.jetbrains.kotlin.analysis.project.structure.builder.KtModuleBuilder
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreProjectEnvironment
import org.jetbrains.kotlin.config.*
import java.nio.file.Path

class LsSourceModuleBuilder(
    private val kotlinCoreProjectEnvironment: KotlinCoreProjectEnvironment,
) : KtModuleBuilder() {
    lateinit var moduleName: String

    private val languageVersionSettings: LanguageVersionSettings = LanguageVersionSettingsImpl(
        LanguageVersion.LATEST_STABLE,
        ApiVersion.LATEST,
        specificFeatures = mapOf(
            LanguageFeature.ContextReceivers to LanguageFeature.State.ENABLED
        )
    )

    private val sourceRoots: MutableList<Path> = mutableListOf()

    fun addSourceRoot(path: Path) {
        sourceRoots.add(path)
    }

    override fun build(): KaSourceModule {
        val contentScope = LsModuleScope(kotlinCoreProjectEnvironment.project, sourceRoots.mapNotNull {
            VirtualFileManager.getInstance().findFileByNioPath(it)
        }.toSet(), sourceRoots.toSet())
        return LsSourceModule(
            directRegularDependencies,
            directDependsOnDependencies,
            directFriendDependencies,
            contentScope,
            platform,
            kotlinCoreProjectEnvironment.project,
            moduleName,
            languageVersionSettings,
        )
    }
}
