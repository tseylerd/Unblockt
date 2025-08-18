// Copyright 2024 Dmitrii Tseiler. Licensed under the Apache 2.0 Licence

package tse.unblockt.ls.server.analysys.project

internal class NoSessionProjectService : LsProjectService {
    override suspend fun rebuildIndexes() { }
}