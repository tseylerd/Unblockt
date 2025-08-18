// Copyright 2024 Dmitrii Tseiler. Licensed under the Apache 2.0 Licence

package tse.unblockt.ls.server.project

import java.nio.file.Path

interface ProjectImporter {
    companion object {
        val instance = GradleProjectImporter()
    }

    fun import(path: Path, progressSink: (String) -> Unit): ProjectImportResult
}