// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

@file:Suppress("UnstableApiUsage")

package tse.unblockt.ls.server.analysys.completion.imports

import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.ImportPath
import org.jetbrains.kotlin.resolve.PlatformDependentAnalyzerServices
import org.jetbrains.kotlin.resolve.jvm.platform.JvmPlatformAnalyzerServices
import tse.unblockt.ls.server.analysys.psi.languageVersionSettings
import tse.unblockt.ls.server.util.platform

class CalculatedImports(originalKtFile: KtFile) {
    private val analyzerServices: PlatformDependentAnalyzerServices = findAnalyzerServices(originalKtFile.platform)
    val defaultImports: Set<ImportPath> = (analyzerServices.getDefaultImports(originalKtFile.languageVersionSettings, includeLowPriorityImports = true) + ImportPath(originalKtFile.packageFqName, false)).toSet()
    val excludedImports: List<FqName> = analyzerServices.excludedImports

    private fun findAnalyzerServices(@Suppress("UNUSED_PARAMETER") platform: TargetPlatform): PlatformDependentAnalyzerServices = JvmPlatformAnalyzerServices
}