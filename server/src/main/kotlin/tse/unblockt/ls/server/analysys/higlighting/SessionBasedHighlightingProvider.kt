// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

package tse.unblockt.ls.server.analysys.higlighting

import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.psi.tree.IElementType
import org.apache.logging.log4j.kotlin.logger
import org.jetbrains.kotlin.KtNodeTypes
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.kdoc.psi.api.KDoc
import org.jetbrains.kotlin.lexer.KtKeywordToken
import org.jetbrains.kotlin.lexer.KtModifierKeywordToken
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import tse.unblockt.ls.protocol.*
import tse.unblockt.ls.server.analysys.files.Offsets
import tse.unblockt.ls.server.analysys.psi.isKotlin
import tse.unblockt.ls.server.analysys.psi.prevSiblingSkipSpaces
import tse.unblockt.ls.server.fs.LsFileManager
import tse.unblockt.ls.server.threading.Cancellable

internal class SessionBasedHighlightingProvider(private val fileManager: LsFileManager): LsHighlightingProvider {
    companion object {
        private val ourProviders = listOf(
            ParserErrorsDiagnosticsProvider(),
            UnresolvedReferenceDiagnosticsProvider(),
            WrongParametersTypeDiagnosticsProvider(),
        )
    }

    context(Cancellable)
    override fun diagnostics(uri: Uri): DocumentDiagnosticReport? {
        val psiFile = fileManager.getPsiFile(uri) as? KtFile
        val doc = fileManager.getDocument(uri)
        if (doc == null || psiFile == null) {
            logger.warn("Failed to find psi file or doc for $uri")
            return null
        }
        return analyze(psiFile) {
            val items = SyntaxTraverser.psiTraverser(psiFile).traverse().flatMap { el ->
                cancellationPoint()
                ourProviders.flatMap { provider -> provider.provide(doc, el) }
            }.toList()
            DocumentDiagnosticReport(
                DocumentDiagnosticReportKind.FULL,
                null,
                items
            )
        }
    }

    context(Cancellable)
    override fun tokens(uri: Uri): SemanticTokens? {
        return tokensOnRange(uri, null)
    }

    context(Cancellable)
    override fun tokensRange(uri: Uri, range: Range): SemanticTokens? {
        val document = fileManager.getDocument(uri)!!
        val tr = Offsets.rangeToTextRange(range, document)
        return tokensOnRange(uri, tr)
    }

    context(Cancellable)
    private fun tokensOnRange(uri: Uri, range: TextRange?): SemanticTokens? {
        val psiFile = fileManager.getPsiFile(uri) as? KtFile
        if (psiFile == null) {
            logger.warn("Failed to find file for $uri")
            return null
        }
        if (!psiFile.isKotlin) {
            logger.warn("File is not supported: $uri")
            return null
        }

        return analyze(psiFile) {
            val document = PsiDocumentManager.getInstance(psiFile.project).getDocument(psiFile)!!

            val baseTraverser = SyntaxTraverser.psiTraverser(psiFile)
            val restricted = when(range) {
                null -> baseTraverser
                else -> baseTraverser.onRange { el ->
                    el.textRange.intersects(range)
                }
            }
            val allLeafs = restricted.expand { e ->
                cancellationPoint()
                !isLeaf(e)
            }.filter { e ->
                cancellationPoint()
                isLeaf(e) || e.children.isEmpty()
            }.toList()


            val allInts = mutableListOf<Int>()
            var prevLineNumber = 0
            var prevLineOffset = 0
            for (leaf in allLeafs.sortedBy { it.startOffset }) {
                cancellationPoint()
                val token = leaf.asToken() ?: continue
                cancellationPoint()

                var startOffset = leaf.startOffset
                val startLineNumber = document.getLineNumber(startOffset)

                var remainingLength = leaf.textLength
                var curLine = startLineNumber
                while (remainingLength > 0) {
                    cancellationPoint()
                    if (prevLineNumber != curLine) {
                        prevLineOffset = 0
                    }

                    val lineStartOffset = document.getLineStartOffset(curLine)
                    val lineEndOffset = document.getLineEndOffset(curLine)

                    val lineLength = lineEndOffset - lineStartOffset + when {
                        lineEndOffset - lineStartOffset >= remainingLength -> 0
                        else -> 1
                    }

                    val elementStartOffset = startOffset - lineStartOffset
                    val elementLength = minOf(remainingLength, lineLength)
                    remainingLength -= lineLength

                    val lineOffset = curLine - prevLineNumber
                    val relativeStartOffset = elementStartOffset - prevLineOffset

                    allInts.add(lineOffset)
                    allInts.add(relativeStartOffset)
                    allInts.add(elementLength)
                    allInts.add(token.type.ordinal)
                    allInts.add(0)

                    prevLineOffset = elementStartOffset
                    prevLineNumber = curLine
                    curLine++
                    startOffset += elementLength
                }
            }
            SemanticTokens(null, allInts.toIntArray())
        }
    }

    context(KaSession)
    private fun PsiElement.asToken(): OneToken? {
        val node: ASTNode? = try {
            node
        } catch (t: Throwable) {
            return asTokenBinary()
        }
        val elementType: IElementType? = node?.elementType
        val type = when {
            this is KtTypeParameter -> SemanticTokenType.TYPEPARAMETER
            this is PsiComment -> SemanticTokenType.COMMENT
            elementType == KtTokens.IDENTIFIER -> {
                val parent = parent
                val grandPa = parent.parent
                when {
                    parent is KtParameter -> SemanticTokenType.PARAMETER
                    parent is KtNamedFunction || grandPa is KtCallExpression -> SemanticTokenType.FUNCTION
                    parent is KtProperty -> SemanticTokenType.VARIABLE
                    parent is KtClass -> {
                        val prevSibling: PsiElement? = prevSiblingSkipSpaces()
                        val prevSiblingNode: ASTNode? = prevSibling?.node
                        val prevSiblingElementType = prevSiblingNode?.elementType
                        when {
                            prevSiblingElementType == KtTokens.INTERFACE_KEYWORD -> SemanticTokenType.INTERFACE
                            else -> SemanticTokenType.CLASS
                        }
                    }
                    grandPa is KtPackageDirective -> SemanticTokenType.NAMESPACE
                    grandPa is KtUserType -> SemanticTokenType.TYPE
                    parent is KtNameReferenceExpression -> {
                        val reference = parent.reference
                        val resolved = try {
                            reference?.resolve()
                        } catch (e: Throwable) {
                            logger.warn("Error resolving element $reference", e)
                            null
                        }
                        resolved?.asToken()?.type
                    }
                    else -> null
                }
            }
            elementType is KtModifierKeywordToken -> when(elementType) {
                KtTokens.FUN_KEYWORD, KtTokens.COMPANION_KEYWORD -> SemanticTokenType.KEYWORD 
                else -> SemanticTokenType.MODIFIER 
            }
            elementType is KtKeywordToken -> SemanticTokenType.KEYWORD
            elementType == KtNodeTypes.STRING_TEMPLATE -> SemanticTokenType.STRING
            elementType == KtTokens.INTEGER_LITERAL -> SemanticTokenType.NUMBER
            elementType == KtTokens.FLOAT_LITERAL -> SemanticTokenType.NUMBER
            this is KtEnumEntry -> SemanticTokenType.ENUMMEMBER
            this is KtClass -> when {
                isInterface() -> SemanticTokenType.INTERFACE
                isEnum() -> SemanticTokenType.ENUM
                else -> SemanticTokenType.CLASS
            }
            else -> null
        } ?: return null
        return OneToken(this, type, modifiers = null)
    }

    private fun PsiElement.asTokenBinary(): OneToken? {
        return when (this) {
            is PsiMethod, is KtNamedFunction -> OneToken(this, SemanticTokenType.FUNCTION, null)
            is PsiClass -> {
                when {
                    isEnum -> OneToken(this, SemanticTokenType.ENUM, null)
                    isInterface -> OneToken(this, SemanticTokenType.INTERFACE, null)
                    else -> OneToken(this, SemanticTokenType.CLASS, null)
                }
            }
            is KtEnumEntry, is PsiEnumConstant -> OneToken(this, SemanticTokenType.ENUMMEMBER, null)
            is KtClass -> {
                when {
                    isEnum() -> OneToken(this, SemanticTokenType.ENUM, null)
                    isInterface() -> OneToken(this, SemanticTokenType.INTERFACE, null)
                    else -> OneToken(this, SemanticTokenType.CLASS, null)
                }
            }
            is PsiField, is KtProperty -> OneToken(this, SemanticTokenType.VARIABLE, null)
            else -> null
        }
    }

    private fun isLeaf(element: PsiElement): Boolean {
        if (element is KDoc) {
            return true
        }

        return element.node.elementType in listOf(KtNodeTypes.STRING_TEMPLATE, KtTokens.DOC_COMMENT)
    }

    private data class OneToken(val element: PsiElement, val type: SemanticTokenType, val modifiers: SemanticTokenModifier?)
}