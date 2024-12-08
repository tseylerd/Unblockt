// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

@file:Suppress("UnstableApiUsage")

package tse.unblockt.ls.server.analysys.completion.util.ij

import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.permissions.forbidAnalysis
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModuleProvider
import org.jetbrains.kotlin.analysis.api.symbols.KaClassLikeSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaDeclarationSymbol
import org.jetbrains.kotlin.builtins.jvm.JavaToKotlinClassMap
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.load.java.javaToKotlinNameMap
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtCodeFragment
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.psiUtil.isPrivate
import org.jetbrains.kotlin.resolve.deprecation.DeprecationLevelValue
import tse.unblockt.ls.server.analysys.completion.LsCompletionRequest

@OptIn(KaExperimentalApi::class)
internal class CompletionVisibilityChecker(
    private val request: LsCompletionRequest
) {
    fun isDefinitelyInvisibleByPsi(declaration: KtDeclaration): Boolean = forbidAnalysis("isDefinitelyInvisibleByPsi") {
        if (request.originalFile is KtCodeFragment) return false

        val declarationContainingFile = declaration.containingKtFile
        if (declaration.isPrivate()) {
            if (declarationContainingFile != request.originalFile && declarationContainingFile != request.file) {
                return true
            }
        }
        if (declaration.hasModifier(KtTokens.INTERNAL_KEYWORD)) {
            return !canAccessInternalDeclarationsFromFile(declarationContainingFile)
        }

        return false
    }

    private fun canAccessInternalDeclarationsFromFile(file: KtFile): Boolean {
        if (file.isCompiled) {
            return false
        }
        val useSiteModule = request.useSiteModule

        val declarationModule = KaModuleProvider.getModule(file.project, file, useSiteModule = useSiteModule)

        return declarationModule == useSiteModule ||
                declarationModule in request.useSiteModule.directFriendDependencies
    }

    context(KaSession)
    fun isVisible(symbol: KaDeclarationSymbol): Boolean {
        if (request.positionContext is KDocNameReferencePositionContext) return true

        if (symbol.deprecationStatus?.deprecationLevel == DeprecationLevelValue.HIDDEN) return false

        if (symbol is KaClassLikeSymbol) {
            val classId = (symbol as? KaClassLikeSymbol)?.classId
            if (classId?.asSingleFqName()?.isJavaClassNotToBeUsedInKotlin() == true) return false
        }

        if (request.originalFile is KtCodeFragment) return true

        return isVisible(
            symbol,
            request.originalFile.symbol,
            (request.positionContext as? KotlinSimpleNameReferencePositionContext)?.explicitReceiver,
            request.positionContext.position
        )
    }

    private fun FqName.isJavaClassNotToBeUsedInKotlin(): Boolean =
        JavaToKotlinClassMap.isJavaPlatformClass(this) || this in javaToKotlinNameMap
}