// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

package tse.unblockt.ls.server.analysys.higlighting

import com.intellij.openapi.editor.Document
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.resolution.KaFunctionCall
import org.jetbrains.kotlin.analysis.api.resolution.singleCallOrNull
import org.jetbrains.kotlin.analysis.api.symbols.typeParameters
import org.jetbrains.kotlin.analysis.api.types.symbol
import org.jetbrains.kotlin.analysis.utils.printer.parentOfType
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.kotlin.types.Variance
import tse.unblockt.ls.protocol.DiagnosticItem
import tse.unblockt.ls.protocol.DiagnosticSeverity
import tse.unblockt.ls.protocol.Range
import tse.unblockt.ls.server.analysys.completion.symbols.Renderers
import tse.unblockt.ls.server.analysys.files.Offsets
import tse.unblockt.ls.server.threading.Cancellable

class WrongParametersTypeDiagnosticsProvider: DiagnosticsProvider {
    context(Cancellable, KaSession)
    @OptIn(KaExperimentalApi::class)
    override fun provide(document: Document, element: PsiElement): Iterable<DiagnosticItem> {
        if (element !is KtValueArgument) {
            return emptyList()
        }
        
        val expr = element.parentOfType<KtCallExpression>() ?: return emptyList()
        val call = expr.resolveToCall()?.singleCallOrNull<KaFunctionCall<*>>() ?: return emptyList()
        val mapping = call.argumentMapping
        val argExpr = element.getArgumentExpression() ?: return emptyList()
        val signature = mapping[argExpr] ?: return emptyList()
        val passedType = argExpr.expressionType ?: return emptyList()
        val expectedType = signature.returnType
        if (passedType.isSubtypeOf(expectedType) || passedType.isFunctionType || passedType.isKFunctionType || passedType.isKSuspendFunctionType || passedType.isSuspendFunctionType) {
            return emptyList()
        }
        if (passedType.symbol?.typeParameters?.isNotEmpty() == true || expectedType.symbol?.typeParameters?.isNotEmpty() == true) {
            return emptyList()
        }
        val passedTypeStr = passedType.render(Renderers.Type.CONCISE, Variance.INVARIANT)
        val expectedTypeStr = expectedType.render(Renderers.Type.CONCISE, Variance.INVARIANT)
        return listOf(
            DiagnosticItem(
                range = Range(Offsets.offsetToPosition(element.startOffset, document), Offsets.offsetToPosition(element.endOffset, document)),
                severity = DiagnosticSeverity.ERROR,
                code = null,
                source = null,
                message = "Expected: $expectedTypeStr, got: $passedTypeStr",
                tags = null,
                relatedInformation = null
            )
        )
    }
}