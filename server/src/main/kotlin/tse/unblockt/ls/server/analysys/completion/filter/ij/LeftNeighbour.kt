// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package tse.unblockt.ls.server.analysys.completion.filter.ij

import com.intellij.psi.PsiElement
import com.intellij.psi.filters.ElementFilter
import com.intellij.psi.filters.position.PositionElementFilter
import com.intellij.psi.util.PsiTreeUtil

class LeftNeighbour(filter: ElementFilter?) : PositionElementFilter() {
    init {
        setFilter(filter)
    }

    override fun isAcceptable(element: Any, context: PsiElement?): Boolean {
        if (element !is PsiElement) return false
        val previous: PsiElement? = searchNonSpaceNonCommentBack(element)
        if (previous != null) {
            return filter.isAcceptable(previous, context)
        }
        return false
    }

    override fun toString(): String {
        return "left($filter)"
    }

    private fun searchNonSpaceNonCommentBack(element: PsiElement?): PsiElement? {
        return if (element == null) null else PsiTreeUtil.prevCodeLeaf(element)
    }
}
