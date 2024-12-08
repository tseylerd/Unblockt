// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

package tse.unblockt.ls.server.analysys.project.module

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaPlatformInterface
import org.jetbrains.kotlin.analysis.api.platform.projectStructure.computeTransitiveDependsOnDependencies
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModule
import org.jetbrains.kotlin.analysis.api.projectStructure.KaNotUnderContentRootModule
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms

@OptIn(KaPlatformInterface::class, KaExperimentalApi::class)
internal class LsNotUnderContentRootModuleImpl(
    override val name: String,
    override val directRegularDependencies: List<KaModule> = emptyList(),
    override val directDependsOnDependencies: List<KaModule> = emptyList(),
    override val directFriendDependencies: List<KaModule> = emptyList(),
    override val targetPlatform: TargetPlatform = JvmPlatforms.defaultJvmPlatform,
    override val file: PsiFile? = null,
    override val moduleDescription: String,
    override val project: Project,
) : KaNotUnderContentRootModule {
    override val transitiveDependsOnDependencies: List<KaModule> by lazy {
        computeTransitiveDependsOnDependencies(directDependsOnDependencies)
    }

    override val contentScope: GlobalSearchScope =
        if (file != null) GlobalSearchScope.fileScope(file) else GlobalSearchScope.EMPTY_SCOPE
}
