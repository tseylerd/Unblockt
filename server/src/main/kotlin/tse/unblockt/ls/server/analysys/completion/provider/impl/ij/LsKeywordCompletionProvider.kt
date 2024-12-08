// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package tse.unblockt.ls.server.analysys.completion.provider.impl.ij

import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.*
import com.intellij.psi.filters.ClassFilter
import com.intellij.psi.filters.ElementFilter
import com.intellij.psi.filters.NotFilter
import com.intellij.psi.filters.OrFilter
import com.intellij.psi.filters.position.PositionElementFilter
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.parentOfType
import com.intellij.psi.util.parentOfTypes
import org.jetbrains.kotlin.KtNodeTypes
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.descriptors.annotations.KotlinTarget.*
import org.jetbrains.kotlin.lexer.KtKeywordToken
import org.jetbrains.kotlin.lexer.KtModifierKeywordToken
import org.jetbrains.kotlin.lexer.KtTokens.*
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.*
import org.jetbrains.kotlin.renderer.render
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull
import tse.unblockt.ls.protocol.CompletionItem
import tse.unblockt.ls.server.analysys.completion.LsCompletionRequest
import tse.unblockt.ls.server.analysys.completion.filter.ij.LeftNeighbour
import tse.unblockt.ls.server.analysys.completion.filter.ij.TextFilter
import tse.unblockt.ls.server.analysys.completion.provider.CompletionItemsProvider
import tse.unblockt.ls.server.analysys.completion.util.KeywordCompletionItem
import tse.unblockt.ls.server.analysys.text.PrefixMatcher
import tse.unblockt.ls.server.threading.Cancellable

class LsKeywordCompletionProvider: CompletionItemsProvider {
    companion object {
        private val ALL_KEYWORDS = (KEYWORDS.types + SOFT_KEYWORDS.types)
            .map { it as KtKeywordToken }

        private val GENERAL_FILTER = NotFilter(
            OrFilter(
                CommentFilter(),
                ParentFilter(ClassFilter(KtLiteralStringTemplateEntry::class.java)),
                ParentFilter(ClassFilter(KtConstantExpression::class.java)),
                FileFilter(ClassFilter(KtTypeCodeFragment::class.java)),
                LeftNeighbour(TextFilter(".")),
                LeftNeighbour(TextFilter("?."))
            )
        )

        private val KEYWORDS_ALLOWED_INSIDE_ANNOTATION_ENTRY: Set<KtKeywordToken> = setOf(
            IF_KEYWORD,
            ELSE_KEYWORD,
            TRUE_KEYWORD,
            FALSE_KEYWORD,
            WHEN_KEYWORD,
        )

        private val INCOMPATIBLE_KEYWORDS_AROUND_SEALED = setOf(
            SEALED_KEYWORD,
            ANNOTATION_KEYWORD,
            DATA_KEYWORD,
            ENUM_KEYWORD,
            OPEN_KEYWORD,
            INNER_KEYWORD,
            ABSTRACT_KEYWORD
        ).mapTo(HashSet()) { it.value }

        private val KEYWORD_CONSTRUCTS = mapOf<KtKeywordToken, String>(
            IF_KEYWORD to "fun foo() { if (caret)",
            WHILE_KEYWORD to "fun foo() { while(caret)",
            FOR_KEYWORD to "fun foo() { for(caret)",
            TRY_KEYWORD to "fun foo() { try {\ncaret\n}",
            CATCH_KEYWORD to "fun foo() { try {} catch (caret)",
            FINALLY_KEYWORD to "fun foo() { try {\n}\nfinally{\ncaret\n}",
            DO_KEYWORD to "fun foo() { do {\ncaret\n}",
            INIT_KEYWORD to "class C { init {\ncaret\n}",
            CONSTRUCTOR_KEYWORD to "class C { constructor(caret)",
            CONTEXT_KEYWORD to "context(caret)",
        )

        private val COMPOUND_KEYWORDS_NOT_SUGGEST_TOGETHER = mapOf<KtKeywordToken, Set<KtKeywordToken>>(
            SEALED_KEYWORD to setOf(FUN_KEYWORD),
        )
    }


    private class CommentFilter : ElementFilter {
        override fun isAcceptable(element: Any?, context: PsiElement?) = (element is PsiElement) && KtPsiUtil.isInComment(element)

        override fun isClassAcceptable(hintClass: Class<out Any?>) = true
    }

    private class ParentFilter(filter: ElementFilter) : PositionElementFilter() {
        init {
            setFilter(filter)
        }

        override fun isAcceptable(element: Any?, context: PsiElement?): Boolean {
            val parent = (element as? PsiElement)?.parent
            return parent != null && (filter?.isAcceptable(parent, context) ?: true)
        }
    }

    private class FileFilter(filter: ElementFilter) : PositionElementFilter() {
        init {
            setFilter(filter)
        }

        override fun isAcceptable(element: Any?, context: PsiElement?): Boolean {
            val file = (element as? PsiElement)?.containingFile
            return file != null && (filter?.isAcceptable(file, context) ?: true)
        }
    }

    context(KaSession, Cancellable)
    override fun provide(request: LsCompletionRequest): Sequence<CompletionItem> {
        if (!GENERAL_FILTER.isAcceptable(request.position, request.position)) return emptySequence()
        val parserFilter = buildFilter(request.position)
        return sequence {
            for (keywordToken in ALL_KEYWORDS) {
                val nextKeywords = keywordToken.getNextPossibleKeywords(request.position) ?: setOf(null)
                nextKeywords.forEach { kw ->
                    handleCompoundKeyword(request.position, keywordToken, kw, request.matcher, parserFilter) {
                        cancellationPoint()
                        yield(it)
                    }
                }
            }
        }
    }

    private fun buildFilter(position: PsiElement): (KtKeywordToken) -> Boolean {
        var parent = position.parent
        var prevParent = position
        while (parent != null) {
            when (parent) {
                is KtBlockExpression -> {
                    if (
                        prevParent is KtScriptInitializer &&
                        parent.parent is KtScript &&
                        parent.allChildren.firstIsInstanceOrNull<KtScriptInitializer>() === prevParent &&
                        parent.parent.allChildren.firstIsInstanceOrNull<KtBlockExpression>() === parent
                    ) {
                        return buildFilterWithReducedContext("", null, position)
                    }

                    var prefixText = "fun foo() { "
                    if (prevParent is KtExpression) {
                        val prevLeaf = prevParent.prevLeaf { it !is PsiWhiteSpace && it !is PsiComment && it !is PsiErrorElement }
                        if (prevLeaf != null) {
                            val isAfterThen = prevLeaf.goUpWhileIsLastChild().any { it.node.elementType == KtNodeTypes.THEN }

                            var isAfterTry = false
                            var isAfterCatch = false
                            if (prevLeaf.node.elementType == RBRACE) {
                                when ((prevLeaf.parent as? KtBlockExpression)?.parent) {
                                    is KtTryExpression -> isAfterTry = true
                                    is KtCatchClause -> {
                                        isAfterTry = true; isAfterCatch = true
                                    }
                                }
                            }

                            if (isAfterThen) {
                                prefixText += if (isAfterTry) {
                                    "if (a)\n"
                                } else {
                                    "if (a) {}\n"
                                }
                            }
                            if (isAfterTry) {
                                prefixText += "try {}\n"
                            }
                            if (isAfterCatch) {
                                prefixText += "catch (e: E) {}\n"
                            }
                        }

                        return buildFilterWithContext(prefixText, prevParent, position)
                    } else {
                        val lastExpression = prevParent
                            .siblings(forward = false, withItself = false)
                            .firstIsInstanceOrNull<KtExpression>()
                        if (lastExpression != null) {
                            val contextAfterExpression = lastExpression
                                .siblings(forward = true, withItself = false)
                                .takeWhile { it != prevParent }
                                .joinToString { it.text }
                            return buildFilterWithContext(prefixText + "x" + contextAfterExpression, prevParent, position)
                        }
                    }
                }

                is KtDeclarationWithInitializer -> {
                    val initializer = parent.initializer
                    if (prevParent == initializer) {
                        return buildFilterWithContext("val v = ", initializer, position)
                    }
                }

                is KtParameter -> {
                    val default = parent.defaultValue
                    if (prevParent == default) {
                        return buildFilterWithContext("val v = ", default, position)
                    }
                }

                is KtTypeReference -> {
                    val shouldIntroduceTypeReferenceContext = when {

                        parent.isExtensionReceiverInCallableDeclaration -> false

                        parent.parent is KtConstructorCalleeExpression -> false

                        else -> true
                    }

                    if (shouldIntroduceTypeReferenceContext) {

                        val prefixText = if (parent.isTypeArgumentOfOuterKtTypeReference) {
                            "fun foo(x: X<"
                        } else {
                            "fun foo(x: "
                        }

                        return buildFilterWithContext(prefixText, contextElement = parent, position)
                    }
                }

                is KtDeclaration -> {
                    when (parent.parent) {
                        is KtClassOrObject -> {
                            return if (parent is KtPrimaryConstructor) {
                                buildFilterWithReducedContext("class X ", parent, position)
                            } else {
                                buildFilterWithReducedContext("class X { ", parent, position)
                            }
                        }

                        is KtFile -> return buildFilterWithReducedContext("", parent, position)
                    }
                }
            }


            prevParent = parent
            parent = parent.parent
        }

        return buildFilterWithReducedContext("", null, position)
    }

    private fun buildFilterWithReducedContext(
        prefixText: String,
        contextElement: PsiElement?,
        position: PsiElement
    ): (KtKeywordToken) -> Boolean {
        val builder = StringBuilder()
        buildReducedContextBefore(builder, position, contextElement)
        return buildFilterByText(prefixText + builder.toString(), position)
    }

    private fun buildReducedContextBefore(builder: StringBuilder, position: PsiElement, scope: PsiElement?) {
        if (position == scope) return

        if (position is KtCodeFragment) {
            val ktContext = position.context as? KtElement ?: return
            buildReducedContextBefore(builder, ktContext, scope)
            return
        } else if (position is PsiFile) {
            return
        }

        val parent = position.parent ?: return

        buildReducedContextBefore(builder, parent, scope)

        val prevDeclaration = position.siblings(forward = false, withItself = false).firstOrNull { it is KtDeclaration }

        var child = parent.firstChild
        while (child != position) {
            if (child is KtDeclaration) {
                if (child == prevDeclaration) {
                    builder.appendReducedText(child)
                }
            } else {
                builder.append(child!!.text)
            }

            child = child.nextSibling
        }
    }

    private fun StringBuilder.appendReducedText(element: PsiElement) {
        var child = element.firstChild
        if (child == null) {
            append(element.text!!)
        } else {
            while (child != null) {
                when (child) {
                    is KtBlockExpression, is KtClassBody -> append("{}")
                    else -> appendReducedText(child)
                }

                child = child.nextSibling
            }
        }
    }

    private fun PsiElement.goUpWhileIsLastChild(): Sequence<PsiElement> = generateSequence(this) {
        when {
            it is PsiFile -> null
            it != it.parent.lastChild -> null
            else -> it.parent
        }
    }

    private fun buildFilterWithContext(
        prefixText: String,
        contextElement: PsiElement,
        position: PsiElement
    ): (KtKeywordToken) -> Boolean {
        val offset = position.getStartOffsetInAncestor(contextElement)
        val truncatedContext = contextElement.text!!.substring(0, offset)
        return buildFilterByText(prefixText + truncatedContext, position)
    }

    private fun PsiElement.getStartOffsetInAncestor(ancestor: PsiElement): Int {
        if (ancestor == this) return 0
        return parent!!.getStartOffsetInAncestor(ancestor) + startOffsetInParent
    }

    private fun buildFilterByText(prefixText: String, position: PsiElement): (KtKeywordToken) -> Boolean {
        val psiFactory = KtPsiFactory(position.project)

        fun PsiElement.isSecondaryConstructorInObjectDeclaration(): Boolean {
            val secondaryConstructor = parentOfType<KtSecondaryConstructor>() ?: return false
            return secondaryConstructor.getContainingClassOrObject() is KtObjectDeclaration
        }

        fun isKeywordCorrectlyApplied(keywordTokenType: KtKeywordToken, file: KtFile): Boolean {
            val elementAt = file.findElementAt(prefixText.length)!!

            when {
                !elementAt.node!!.elementType.matchesKeyword(keywordTokenType) -> return false

                elementAt.getNonStrictParentOfType<PsiErrorElement>() != null -> return false

                isErrorElementBefore(elementAt) -> return false

                (keywordTokenType == VAL_KEYWORD || keywordTokenType == VAR_KEYWORD) &&
                        elementAt.parent is KtParameter &&
                        elementAt.parentOfTypes(KtNamedFunction::class, KtSecondaryConstructor::class) != null -> return false

                keywordTokenType == CONSTRUCTOR_KEYWORD && elementAt.isSecondaryConstructorInObjectDeclaration() -> return false

                keywordTokenType !is KtModifierKeywordToken -> return true

                else -> {
                    val container = (elementAt.parent as? KtModifierList)?.parent ?: return true

                    val parentTarget = when (val ownerDeclaration = container.getParentOfType<KtDeclaration>(strict = true)) {
                        null -> FILE

                        is KtClass -> {
                            when {
                                ownerDeclaration.isInterface() -> INTERFACE
                                ownerDeclaration.isEnum() -> ENUM_CLASS
                                ownerDeclaration.isAnnotation() -> ANNOTATION_CLASS
                                else -> CLASS_ONLY
                            }
                        }

                        is KtObjectDeclaration -> if (ownerDeclaration.isObjectLiteral()) OBJECT_LITERAL else OBJECT

                        else -> return keywordTokenType != CONST_KEYWORD
                    }

                    if (keywordTokenType == CONST_KEYWORD) {
                        return when (parentTarget) {
                            OBJECT -> true
                            FILE -> {
                                val prevSiblings = elementAt.parent.siblings(withItself = false, forward = false)
                                val hasLineBreak = prevSiblings
                                    .takeWhile { it is PsiWhiteSpace || it.isSemicolon() }
                                    .firstOrNull { it.text.contains("\n") || it.isSemicolon() } != null
                                hasLineBreak || prevSiblings.none {
                                    it !is PsiWhiteSpace && !it.isSemicolon() && it !is KtImportList && it !is KtPackageDirective
                                }
                            }
                            else -> false
                        }
                    }

                    return true
                }
            }
        }

        return fun(keywordTokenType): Boolean {
            val files = buildFilesWithKeywordApplication(keywordTokenType, prefixText, psiFactory)
            return files.any { file -> isKeywordCorrectlyApplied(keywordTokenType, file); }
        }
    }

    private fun buildFilesWithKeywordApplication(
        keywordTokenType: KtKeywordToken,
        prefixText: String,
        psiFactory: KtPsiFactory
    ): Sequence<KtFile> {
        return computeKeywordApplications(prefixText, keywordTokenType)
            .map { application -> psiFactory.createFile(prefixText + application) }
    }

    private fun computeKeywordApplications(prefixText: String, keyword: KtKeywordToken): Sequence<String> = when (keyword) {
        SUSPEND_KEYWORD -> sequenceOf("suspend () -> Unit>", "suspend X")
        else -> {
            if (prefixText.endsWith("@"))
                sequenceOf(keyword.value + ":X Y.Z")
            else
                sequenceOf(keyword.value + " X")
        }
    }

    private val KtTypeReference.isExtensionReceiverInCallableDeclaration: Boolean
        get() {
            val parent = parent
            return parent is KtCallableDeclaration && parent.receiverTypeReference == this
        }

    private val KtTypeReference.isTypeArgumentOfOuterKtTypeReference: Boolean
        get() {
            val typeProjection = parent as? KtTypeProjection
            val typeArgumentList = typeProjection?.parent as? KtTypeArgumentList
            val userType = typeArgumentList?.parent as? KtUserType

            return userType?.parent is KtTypeReference
        }

    private fun IElementType.matchesKeyword(keywordType: KtKeywordToken): Boolean {
        return when (this) {
            keywordType -> true
            NOT_IN -> keywordType == IN_KEYWORD
            NOT_IS -> keywordType == IS_KEYWORD
            else -> false
        }
    }

    private fun isErrorElementBefore(token: PsiElement): Boolean {
        for (leaf in token.prevLeafs) {
            if (leaf is PsiWhiteSpace || leaf is PsiComment) continue
            if (leaf.parentsWithSelf.any { it is PsiErrorElement }) return true
            if (leaf.textLength != 0) break
        }
        return false
    }

    private fun PsiElement.isSemicolon() = node.elementType == SEMICOLON

    private fun KtKeywordToken.getNextPossibleKeywords(position: PsiElement): Set<KtKeywordToken>? {
        return when {
            this == SUSPEND_KEYWORD && position.isInsideKtTypeReference -> null
            else -> getCompoundKeywords(this)
        }
    }

    private fun getCompoundKeywords(token: KtKeywordToken): Set<KtKeywordToken>? =
        mapOf<KtKeywordToken, Set<KtKeywordToken>>(
            COMPANION_KEYWORD to setOf(OBJECT_KEYWORD),
            DATA_KEYWORD to setOfNotNull(
                CLASS_KEYWORD,
                OBJECT_KEYWORD
            ),
            ENUM_KEYWORD to setOf(CLASS_KEYWORD),
            ANNOTATION_KEYWORD to setOf(CLASS_KEYWORD),
            SEALED_KEYWORD to setOf(CLASS_KEYWORD, INTERFACE_KEYWORD, FUN_KEYWORD),
            LATEINIT_KEYWORD to setOf(VAR_KEYWORD),
            CONST_KEYWORD to setOf(VAL_KEYWORD),
            SUSPEND_KEYWORD to setOf(FUN_KEYWORD)
        )[token]

    private inline fun handleCompoundKeyword(
        position: PsiElement,
        keywordToken: KtKeywordToken,
        nextKeyword: KtKeywordToken?,
        prefixMatcher: PrefixMatcher,
        parserFilter: (KtKeywordToken) -> Boolean,
        consumer: (CompletionItem) -> Unit
    ) {
        if (position.isInsideAnnotationEntryArgumentList() && keywordToken !in KEYWORDS_ALLOWED_INSIDE_ANNOTATION_ENTRY) return

        var keyword = keywordToken.value

        var applicableAsCompound = false
        if (nextKeyword != null) {
            var next = position.nextLeaf { !(it.isSpace() || it.text == "$") }?.text
            next = next?.removePrefix("$")

            if (keywordToken == SEALED_KEYWORD) {
                if (next in INCOMPATIBLE_KEYWORDS_AROUND_SEALED) return
                val prev = position.prevLeaf { !(it.isSpace() || it is PsiErrorElement) }?.text
                if (prev in INCOMPATIBLE_KEYWORDS_AROUND_SEALED) return
            }

            val nextIsNotYetPresent = keywordToken.getNextPossibleKeywords(position)?.none { it.value == next } == true
            if (nextIsNotYetPresent && keywordToken.avoidSuggestingWith(nextKeyword)) return

            if (nextIsNotYetPresent)
                keyword += " " + nextKeyword.value
            else
                applicableAsCompound = true
        }

        if (keywordToken == DYNAMIC_KEYWORD) return

        if (!ignorePrefixForKeyword(position, keywordToken) && !prefixMatcher.isStartMatch(keyword)) return

        if (!parserFilter(keywordToken)) return

        val constructText = KEYWORD_CONSTRUCTS[keywordToken]
        if (constructText != null && !applicableAsCompound) {
            consumer(
                KeywordCompletionItem(
                    label = keyword
                )
            )
        } else {
            handleTopLevelClassName(position, keyword, consumer)
            consumer(KeywordCompletionItem(keyword))
        }
    }

    private fun PsiElement.isSpace() = this is PsiWhiteSpace && '\n' !in getText()

    private inline fun handleTopLevelClassName(position: PsiElement, keyword: String, consumer: (CompletionItem) -> Unit) {
        val topLevelClassName = getTopLevelClassName(position)

        if (topLevelClassName != null) {
            if (listOf(OBJECT_KEYWORD, INTERFACE_KEYWORD).any { keyword.endsWith(it.value) }) {
                consumer(KeywordCompletionItem("$keyword $topLevelClassName"))
            }
            if (keyword.endsWith(CLASS_KEYWORD.value)) {
                if (keyword.startsWith(DATA_KEYWORD.value)) {
                    consumer(KeywordCompletionItem(keyword))
                } else {
                    consumer(KeywordCompletionItem("$keyword $topLevelClassName"))
                }
            }
        }
    }

    private fun getTopLevelClassName(position: PsiElement): String? {
        if (position.parents.any { it is KtDeclaration }) return null
        val file = position.containingFile as? KtFile ?: return null
        val name = FileUtil.getNameWithoutExtension(file.name)
        if (!Name.isValidIdentifier(name)
            || Name.identifier(name).render() != name
            || !name[0].isUpperCase()
            || file.declarations.any { it is KtClassOrObject && it.name == name }
        ) return null
        return name
    }

    private fun KtKeywordToken.avoidSuggestingWith(keywordToken: KtKeywordToken): Boolean {
        val nextKeywords = COMPOUND_KEYWORDS_NOT_SUGGEST_TOGETHER[this] ?: return false
        return keywordToken in nextKeywords
    }

    private fun ignorePrefixForKeyword(completionPosition: PsiElement, keywordToken: KtKeywordToken): Boolean =
        when (keywordToken) {
            OVERRIDE_KEYWORD -> true
            THIS_KEYWORD,
            RETURN_KEYWORD,
            BREAK_KEYWORD,
            CONTINUE_KEYWORD -> {
                completionPosition is KtExpressionWithLabel && completionPosition.getTargetLabel() != null
            }

            else -> false
        }
}

internal val PsiElement.isInsideKtTypeReference: Boolean
    get() = getNonStrictParentOfType<KtTypeReference>() != null