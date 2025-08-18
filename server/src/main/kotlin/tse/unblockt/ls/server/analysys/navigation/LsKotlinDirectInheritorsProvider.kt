// Copyright 2024 Dmitrii Tseiler. Licensed under the Apache 2.0 Licence

package tse.unblockt.ls.server.analysys.navigation

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.analysis.api.platform.declarations.KotlinDirectInheritorsProvider
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject

internal class LsKotlinDirectInheritorsProvider(private val project: Project) : KotlinDirectInheritorsProvider {
    override fun getDirectKotlinInheritors(
        ktClass: KtClass,
        scope: GlobalSearchScope,
        includeLocalInheritors: Boolean,
    ): Iterable<KtClassOrObject> {
        return emptyList()
    }
}
