// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

package tse.unblockt.ls.server.analysys.completion.util

import org.jetbrains.kotlin.analysis.api.KaSession
import tse.unblockt.ls.server.analysys.completion.LsCompletionRequest
import tse.unblockt.ls.server.analysys.completion.provider.CompletionItemsProvider
import tse.unblockt.ls.server.analysys.completion.provider.impl.ij.*
import tse.unblockt.ls.server.analysys.completion.util.ij.*

context(KaSession)
fun createProviders(request: LsCompletionRequest): List<CompletionItemsProvider> {
    val positionContext = request.positionContext
    val result = mutableListOf<CompletionItemsProvider>()
    when (positionContext) {
        is KotlinExpressionNameReferencePositionContext -> if (!positionContext.allowsOnlyNamedArguments()) {
            result += LsKeywordCompletionProvider()
            result += LsClassifierCompletionProvider()
            result += LsCallableCompletionProvider(request.project)
        }

        is KotlinTypeNameReferencePositionContext -> {
            result += LsKeywordCompletionProvider()
            result += LsClassifierCompletionProvider()
        }

        is KotlinAnnotationTypeNameReferencePositionContext -> {
            result += LsAnnotationsCompletionProvider()
            result += LsKeywordCompletionProvider()
        }

        is KotlinMemberDeclarationExpectedPositionContext -> {
            result += LsKeywordCompletionProvider()
        }

        is KotlinLabelReferencePositionContext -> {
            result += LsKeywordCompletionProvider()
        }

        is KotlinUnknownPositionContext -> {
            result += LsKeywordCompletionProvider()
        }

        is KotlinWithSubjectEntryPositionContext -> {
            result += LsCallableCompletionProvider(request.project)
        }

        is KotlinCallableReferencePositionContext -> {
            result += LsClassReferenceCompletionProvider()
        }

        is KotlinInfixCallPositionContext -> {
            result += LsKeywordCompletionProvider()
        }

        is KotlinSimpleParameterPositionContext -> {
            result += LsKeywordCompletionProvider()
        }

        is KotlinPrimaryConstructorParameterPositionContext -> {
            result += LsKeywordCompletionProvider()
        }

        is KDocLinkNamePositionContext -> {
            result += LsClassifierCompletionProvider()
        }
        else -> {}
    }
    return result
}