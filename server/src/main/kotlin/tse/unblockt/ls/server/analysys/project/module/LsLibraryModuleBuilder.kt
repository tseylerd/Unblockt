// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

@file:OptIn(KaImplementationDetail::class, KaPlatformInterface::class, KaExperimentalApi::class)

package tse.unblockt.ls.server.analysys.project.module

import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaImplementationDetail
import org.jetbrains.kotlin.analysis.api.KaPlatformInterface
import org.jetbrains.kotlin.analysis.api.impl.base.util.LibraryUtils
import org.jetbrains.kotlin.analysis.api.projectStructure.KaLibraryModule
import org.jetbrains.kotlin.analysis.api.standalone.base.projectStructure.StandaloneProjectFactory
import org.jetbrains.kotlin.analysis.project.structure.builder.KtBinaryModuleBuilder
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreProjectEnvironment
import java.nio.file.Path

class LsLibraryModuleBuilder(private val env: KotlinCoreProjectEnvironment): KtBinaryModuleBuilder() {
    lateinit var libraryName: String

    override fun build(): KaLibraryModule {
        return build(false)
    }

    fun addBinaryRootsFromJdkHome(jdkHome: Path, isJre: Boolean) {
        val jdkRoots = LibraryUtils.findClassesFromJdkHome(jdkHome, isJre)
        addBinaryRoots(jdkRoots)
    }

    fun build(isSdk: Boolean): KaLibraryModule {
        val binaryRoots = getBinaryRoots()
        val vFiles = StandaloneProjectFactory.getVirtualFilesForLibraryRoots(binaryRoots, env)
        val contentScope = LsLibraryScope(vFiles.toSet())
        val binaryVirtualFiles = getBinaryVirtualFiles()
        return LsLibraryModule(
            directRegularDependencies,
            directDependsOnDependencies,
            directFriendDependencies,
            contentScope,
            platform,
            env.project,
            binaryRoots,
            binaryVirtualFiles,
            libraryName,
            null,
            isSdk,
        )
    }
}