// Copyright 2024 Dmitrii Tseiler. Licensed under the Apache 2.0 Licence

package tse.unblockt.ls.server.analysys.project

import com.intellij.openapi.project.DefaultProjectFactory
import com.intellij.openapi.project.Project

class LsDefaultProjectFactory: DefaultProjectFactory() {
    lateinit var project: Project

    override fun getDefaultProject(): Project {
        return project
    }
}