// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

package tse.unblockt.ls.server.analysys.project

import com.intellij.openapi.project.DefaultProjectFactory
import com.intellij.openapi.project.Project

class LsDefaultProjectFactory: DefaultProjectFactory() {
    lateinit var project: Project

    override fun getDefaultProject(): Project {
        return project
    }
}