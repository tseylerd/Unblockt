// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

package tse.unblockt.ls.server.analysys

import com.intellij.mock.MockProject
import org.jetbrains.kotlin.analysis.api.platform.KotlinDeserializedDeclarationsOrigin
import org.jetbrains.kotlin.analysis.api.platform.KotlinPlatformSettings
import org.jetbrains.kotlin.analysis.api.platform.lifetime.KotlinLifetimeTokenFactory
import org.jetbrains.kotlin.analysis.api.platform.lifetime.KotlinReadActionConfinementLifetimeTokenFactory
import org.jetbrains.kotlin.analysis.api.standalone.base.projectStructure.AnalysisApiSimpleServiceRegistrar
import org.jetbrains.kotlin.analysis.api.standalone.base.services.LLStandaloneFirElementByPsiElementChooser
import org.jetbrains.kotlin.analysis.low.level.api.fir.api.services.LLFirElementByPsiElementChooser

object LsServiceRegistrar: AnalysisApiSimpleServiceRegistrar() {
    override fun registerProjectServices(project: MockProject) {
        project.apply {
            registerService(KotlinLifetimeTokenFactory::class.java, KotlinReadActionConfinementLifetimeTokenFactory::class.java)
            registerService(KotlinPlatformSettings::class.java, LsKotlinPlatformSettings::class.java)

            registerService(LLFirElementByPsiElementChooser::class.java, LLStandaloneFirElementByPsiElementChooser::class.java)
        }
    }

    class LsKotlinPlatformSettings: KotlinPlatformSettings {
        override val deserializedDeclarationsOrigin: KotlinDeserializedDeclarationsOrigin
            get() = KotlinDeserializedDeclarationsOrigin.STUBS
    }
}