// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

@file:OptIn(KaExperimentalApi::class)

package tse.unblockt.ls.server.analysys.parameters

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.siblings
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.components.KaSubtypingErrorTypePolicy
import org.jetbrains.kotlin.analysis.api.renderer.declarations.impl.KaDeclarationRendererForSource
import org.jetbrains.kotlin.analysis.api.resolution.KaCallCandidateInfo
import org.jetbrains.kotlin.analysis.api.resolution.KaSimpleFunctionCall
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaValueParameterSymbol
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.allChildren
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstance
import tse.unblockt.ls.protocol.*
import tse.unblockt.ls.server.analysys.files.Offsets
import tse.unblockt.ls.server.fs.LsFileManager

class SessionBasedParametersHintService(private val fileManager: LsFileManager): ParameterHintsService {
    companion object {
        val STOP_SEARCH_CLASSES: Set<Class<out KtElement>> = setOf(
            KtNamedFunction::class.java,
            KtVariableDeclaration::class.java,
            KtValueArgumentList::class.java,
            KtLambdaArgument::class.java,
            KtContainerNode::class.java,
            KtTypeArgumentList::class.java
        )
    }

    override fun analyzePosition(uri: Uri, position: Position): SignatureHelp? {
        val document = fileManager.getDocument(uri) ?: return null
        val offset = Offsets.positionToOffset(document, position)
        val argumentsList = getArgumentsList(uri, offset) ?: return null
        return analyze(argumentsList) {
            val ktCallExpression = findReferenceExpression(argumentsList)
            val callCandidates = filterCallCandidates(argumentsList, offset, ktCallExpression.resolveToCallCandidates())
            val signatures = callCandidates.map { c: KaCallCandidateInfo ->
                val call = c.candidate as KaSimpleFunctionCall
                val index = getParameterIndex(argumentsList, call, offset)
                SignatureInformation(
                    label = signature(call),
                    documentation = null,
                    parameters = call.symbol.valueParameters.map { p ->
                        ParameterInformation(typeAsString(p))
                    },
                    index
                )
            }
            SignatureHelp(signatures = signatures, 0, null)
        }
    }

    private fun findReferenceExpression(argumentsList: KtValueArgumentList): KtReferenceExpression {
        return argumentsList.siblings(forward = false, withSelf = false).firstIsInstance<KtReferenceExpression>()
    }

    context(KaSession)
    private fun signature(call: KaSimpleFunctionCall): String {
        return call.symbol.render(KaDeclarationRendererForSource.WITH_SHORT_NAMES)
    }

    context(KaSession)
    private fun typeAsString(p: KaValueParameterSymbol): String {
        return p.render(KaDeclarationRendererForSource.WITH_SHORT_NAMES)
    }

    context(KaSession)
    private fun filterCallCandidates(argumentList: KtValueArgumentList, offset: Int, resolveToCallCandidates: List<KaCallCandidateInfo>): List<KaCallCandidateInfo> {
        val idx = getParameterIndexByPsi(argumentList, offset)
        return resolveToCallCandidates.filter { call ->
            val sfc = call.candidate as? KaSimpleFunctionCall ?: return@filter false
            if (sfc.symbol.valueParameters.size <= idx) {
                return@filter false
            }

            if (!argumentList.arguments.all {
                !it.isNamed() || indexOfArgument(sfc, it) > -1
            }) {
                return@filter false
            }

            val mapping = sfc.argumentMapping
            mapping.all { (expr, param) ->
                val expressionType = expr.expressionType
                val paramType = param.symbol.returnType
                expressionType?.isSubtypeOf(paramType, KaSubtypingErrorTypePolicy.LENIENT) ?: false
            }
        }
    }

    private fun getArgumentsList(uri: Uri, offset: Int): KtValueArgumentList? {
        val file = fileManager.getPsiFile(uri) as? KtFile ?: return null
        val token = file.findElementAt(offset) ?: return null
        return PsiTreeUtil.getParentOfType(token, KtValueArgumentList::class.java, true, *STOP_SEARCH_CLASSES.toTypedArray())
    }

    context(KaSession)
    private fun getParameterIndex(arguments: KtValueArgumentList, call: KaSimpleFunctionCall, offset: Int): Int {
        val indexByOrder = getParameterIndexByPsi(arguments, offset)
        val children = arguments.children
        val ktArgument = children.getOrNull(indexByOrder) as? KtValueArgument ?: return indexByOrder
        if (!ktArgument.isNamed()) {
            return indexByOrder
        }
        return indexOfArgument(call, ktArgument)
    }

    private fun indexOfArgument(call: KaSimpleFunctionCall, ktArguments: KtValueArgument) = call.symbol.valueParameters.indexOfFirst { vp ->
        vp.name == ktArguments.getArgumentName()!!.asName
    }

    private fun getParameterIndexByPsi(arguments: KtValueArgumentList, offset: Int) = arguments.allChildren
        .takeWhile { it.startOffset < offset }
        .count { it.node.elementType == KtTokens.COMMA }
}