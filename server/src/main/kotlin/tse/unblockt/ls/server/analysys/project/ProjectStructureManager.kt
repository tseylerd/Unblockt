// Copyright 2024 Dmitrii Tseiler. Licensed under the Apache 2.0 Licence

package tse.unblockt.ls.server.analysys.project

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import java.nio.file.Path

data class ProjectStructureManager(val root: Path) {
    companion object {
        fun instance(project: Project): ProjectStructureManager {
            return project.service()
        }
    }
}
