// Copyright 2024 Dmitrii Tseiler. Licensed under the Apache 2.0 Licence

package tse.unblockt.ls.server

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo
import tse.unblockt.ls.server.framework.simulateClient
import tse.unblockt.ls.util.rkTest
import tse.unblockt.ls.util.testProjectPath

class GoToDeclarationTest {
    @Test
    fun resolveKotlinSdkSymbol(info: TestInfo) = rkTest {
        simulateClient(testProjectPath, info) {
            declaration()
        }
    }

    @Test
    fun resolveJavaSdkSymbol(info: TestInfo) = rkTest {
        simulateClient(testProjectPath, info) {
            declaration()
        }
    }

    @Test
    fun inMemoryModifications(info: TestInfo) = rkTest {
        simulateClient(testProjectPath, info) {
            highlight()
        }
    }

    @Test
    fun unresolvedReference(info: TestInfo) = rkTest {
        simulateClient(testProjectPath, info) {
            diagnose()
        }
    }

    @Test
    fun interModuleResolve(info: TestInfo) = rkTest {
        simulateClient(testProjectPath, info) {
            declaration()
        }
    }

    @Test
    fun renameFile(info: TestInfo) = rkTest {
        simulateClient(testProjectPath, info) {
            rename(testProjectPath.resolve("src/main/java/tse/com/sample.kt"), "sampleRenamed.kt")

            declaration()
        }
    }

    @Test
    fun renameFileWithChanges(info: TestInfo) = rkTest {
        simulateClient(testProjectPath, info) {
            rename(testProjectPath.resolve("src/main/java/tse/com/sample.kt"), "sampleRenamed.kt")

            declaration()
        }
    }

    @Test
    fun workspaceFileChangeNoticed(info: TestInfo) = rkTest {
        simulateClient(testProjectPath, info) {
            declaration()
        }
    }
}