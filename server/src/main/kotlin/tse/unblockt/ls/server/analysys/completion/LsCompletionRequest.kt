// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

package tse.unblockt.ls.server.analysys.completion

import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModule
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModuleProvider
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.psi.KtFile
import tse.unblockt.ls.protocol.CompletionParams
import tse.unblockt.ls.protocol.Uri
import tse.unblockt.ls.server.analysys.completion.imports.CalculatedImports
import tse.unblockt.ls.server.analysys.completion.util.ij.KDocNameReferencePositionContext
import tse.unblockt.ls.server.analysys.completion.util.ij.KotlinCallableReferencePositionContext
import tse.unblockt.ls.server.analysys.completion.util.ij.KotlinRawPositionContext
import tse.unblockt.ls.server.analysys.index.common.Indexes
import tse.unblockt.ls.server.analysys.psi.languageVersionSettings
import tse.unblockt.ls.server.analysys.text.PrefixMatcher

data class LsCompletionRequest(
    val uri: Uri,
    val file: KtFile,
    val originalFile: KtFile,
    val offset: Int,
    val position: PsiElement,
    val params: CompletionParams,
    val matcher: PrefixMatcher,
    val platform: TargetPlatform,
    val imports: CalculatedImports,
    val prefix: String,
    val document: Document,
    val positionContext: KotlinRawPositionContext,
) {
    val project: Project = originalFile.project

    val useSiteModule: KaModule by lazy {
        KaModuleProvider.getModule(file.project, originalFile, useSiteModule = null)
    }

    val indexes: Indexes by lazy {
        Indexes.instance(project)
    }

    val allowSyntheticJavaProperties: Boolean = positionContext !is KDocNameReferencePositionContext
            && (positionContext !is KotlinCallableReferencePositionContext || file.languageVersionSettings.supportsFeature(LanguageFeature.ReferencesToSyntheticJavaProperties))

    val scopeNameFilter: (Name) -> Boolean
        get() = {
            name -> !name.isSpecial && matcher.isPrefixMatch(name.identifier)
        }
}