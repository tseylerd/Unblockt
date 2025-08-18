// Copyright 2024 Dmitrii Tseiler. Licensed under the Apache 2.0 Licence

package tse.unblockt.ls.server.analysys.project.build

import tse.unblockt.ls.protocol.HealthStatusInformation

interface LsBuildService {
    val health: HealthStatusInformation?

    suspend fun reload()
}