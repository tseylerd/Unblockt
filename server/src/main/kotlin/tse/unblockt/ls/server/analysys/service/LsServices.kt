// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

package tse.unblockt.ls.server.analysys.service

import com.intellij.openapi.Disposable
import tse.unblockt.ls.protocol.HealthStatusInformation
import tse.unblockt.ls.server.analysys.completion.LsCompletionMachine
import tse.unblockt.ls.server.analysys.declaration.LsGoToDeclarationProvider
import tse.unblockt.ls.server.analysys.higlighting.LsHighlightingProvider
import tse.unblockt.ls.server.analysys.notifications.LsNotificationsService
import tse.unblockt.ls.server.analysys.parameters.ParameterHintsService
import tse.unblockt.ls.server.analysys.project.LsProjectService
import tse.unblockt.ls.server.analysys.project.build.LsBuildService
import tse.unblockt.ls.server.fs.LsFileManager
import tse.unblockt.ls.server.project.ProjectModel
import tse.unblockt.ls.server.util.ServiceInformation

interface LsServices: Disposable {
    val filesManager: LsFileManager
    val goToDeclarationProvider: LsGoToDeclarationProvider
    val completionMachine: LsCompletionMachine
    val highlightingProvider: LsHighlightingProvider
    val buildService: LsBuildService
    val notificationsService: LsNotificationsService
    val parameterHintsService: ParameterHintsService
    val projectService: LsProjectService

    val projectModel: ProjectModel?
        get() = null

    val health: HealthStatusInformation

    val serviceInformation: ServiceInformation

    suspend fun onInitialized()
    suspend fun cleanup()
}