// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

package tse.unblockt.ls.server.analysys.text.ij

import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.codeStyle.MinusculeMatcher
import com.intellij.psi.codeStyle.NameUtil
import tse.unblockt.ls.server.analysys.text.PrefixMatcher

class CamelCaseMatcher(
    private val prefix: String,
    isCaseSensitive: Boolean,
    private val isTypoTolerant: Boolean
) : PrefixMatcher {
    private val myMatcher: MinusculeMatcher
    private val myCaseInsensitiveMatcher: MinusculeMatcher

    init {
        myMatcher = createMatcher(isCaseSensitive)
        myCaseInsensitiveMatcher = createMatcher(false)
    }

    override val isEmpty: Boolean
        get() = prefix.isEmpty()

    override fun isStartMatch(string: String): Boolean {
        return myMatcher.isStartMatch(string)
    }

    override fun isPrefixMatch(string: String): Boolean {
        if (string.startsWith("_")) {
            return false
        }

        return myMatcher.matches(string)
    }

    private fun createMatcher(caseSensitive: Boolean): MinusculeMatcher {
        val prefix = applyMiddleMatching(prefix)

        var builder = NameUtil.buildMatcher(prefix)
        if (caseSensitive) {
            builder = builder.withCaseSensitivity(NameUtil.MatchingCaseSensitivity.FIRST_LETTER)
        }
        if (isTypoTolerant) {
            builder = builder.typoTolerant()
        }
        return builder.build()
    }

    override fun toString(): String {
        return prefix
    }

    companion object {
        private var ourForceStartMatching = false

        fun applyMiddleMatching(prefix: String): String {
            if (prefix.isNotEmpty() && !ourForceStartMatching) {
                return "*" + StringUtil.replace(prefix, ".", ". ").trim { it <= ' ' }
            }
            return prefix
        }
    }
}