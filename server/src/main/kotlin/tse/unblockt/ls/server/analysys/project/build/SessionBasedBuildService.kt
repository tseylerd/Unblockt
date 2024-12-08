// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

package tse.unblockt.ls.server.analysys.project.build

import com.intellij.openapi.project.Project
import tse.unblockt.ls.protocol.HealthStatus
import tse.unblockt.ls.protocol.HealthStatusInformation
import tse.unblockt.ls.server.analysys.service.memoryMessage
import java.nio.file.Path

internal class SessionBasedBuildService(private val root: Path, project: Project): LsBuildService {
    private val manager: BuildManager = BuildManager.instance(project)
    override val health: HealthStatusInformation?
        get() = when {
            manager.shouldReload() -> HealthStatusInformation(
                text = memoryMessage(),
                message = "Gradle project is outdated. Click on the widget and invoke Reload Gradle Project action.",
                HealthStatus.WARNING
            )
            else -> null
        }

    override suspend fun reload() {
        manager.reload(root)
    }
}