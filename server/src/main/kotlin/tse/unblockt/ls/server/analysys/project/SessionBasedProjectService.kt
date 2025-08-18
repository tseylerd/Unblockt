// Copyright 2024 Dmitrii Tseiler. Licensed under the Apache 2.0 Licence

package tse.unblockt.ls.server.analysys.project

import tse.unblockt.ls.server.analysys.AnalysisEntrypoint

internal object SessionBasedProjectService: LsProjectService {
    override suspend fun rebuildIndexes() {
        val root = AnalysisEntrypoint.services.projectModel?.path ?: return
        val storagePath = AnalysisEntrypoint.services.serviceInformation.storagePath
        val globalStoragePath = AnalysisEntrypoint.services.serviceInformation.globalStoragePath
        AnalysisEntrypoint.services.cleanup()
        AnalysisEntrypoint.shutdown()
        AnalysisEntrypoint.init(root, storagePath, globalStoragePath)
    }
}