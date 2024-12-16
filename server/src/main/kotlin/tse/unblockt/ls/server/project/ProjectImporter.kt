// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

package tse.unblockt.ls.server.project

import java.nio.file.Path

interface ProjectImporter {
    companion object {
        val instance = GradleProjectImporter()
    }

    fun import(path: Path, progressSink: (String) -> Unit): ProjectImportResult
}