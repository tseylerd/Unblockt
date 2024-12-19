// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

package tse.unblockt.ls.server

import org.junit.jupiter.api.TestInfo
import tse.unblockt.ls.server.framework.simulateClient
import tse.unblockt.ls.util.rkTest
import tse.unblockt.ls.util.testMultiplatformProjectPath
import kotlin.test.Test

class MultiplatformTest {
    @Test
    fun referencesResolvedInMainModule(info: TestInfo) = rkTest {
        simulateClient(testMultiplatformProjectPath, info) {
            diagnose()
        }
    }

    @Test
    fun referencesResolvedIniOSModule(info: TestInfo) = rkTest {
        simulateClient(testMultiplatformProjectPath, info) {
            diagnose()
        }
    }

    @Test
    fun testMaterialThemeCompleted(info: TestInfo) = rkTest {
        simulateClient(testMultiplatformProjectPath, info) {
            complete()
        }
    }

    @Test
    fun materCompletionItems(info: TestInfo) = rkTest {
        simulateClient(testMultiplatformProjectPath, info) {
            completionItems()
        }
    }
}