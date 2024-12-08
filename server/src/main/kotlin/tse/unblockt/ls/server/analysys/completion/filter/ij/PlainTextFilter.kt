// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package tse.unblockt.ls.server.analysys.completion.filter.ij

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.filters.ElementFilter

open class PlainTextFilter(private val values: List<String>) : ElementFilter {
    override fun isClassAcceptable(hintClass: Class<*>?): Boolean = true

    override fun isAcceptable(element: Any?, context: PsiElement?): Boolean {
        return element != null && values.any { v -> v == getTextByElement(element) }
    }

    protected open fun getTextByElement(element: Any?): String? {
        var elementValue: String? = null
        if (element is PsiNamedElement) {
            elementValue = element.name
        } else if (element is PsiElement) {
            elementValue = element.text
        }
        return elementValue
    }
}