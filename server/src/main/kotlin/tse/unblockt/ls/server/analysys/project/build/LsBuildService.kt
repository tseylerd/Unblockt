// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

package tse.unblockt.ls.server.analysys.project.build

import tse.unblockt.ls.protocol.HealthStatusInformation

interface LsBuildService {
    val health: HealthStatusInformation?

    suspend fun reload()
}