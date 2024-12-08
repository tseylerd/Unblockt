package tse.unblockt.ls.server.analysys

import com.intellij.mock.MockProject

fun testProject(project: MockProject) {
    with(project) {
        registerService(MockProject::class.java, MockProject::class.java)
    }
}

//server/src/main/kotlin/tse/unblockt/ls/server/analysys