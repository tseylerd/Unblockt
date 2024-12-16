package tse.unblockt.ls.server

import org.junit.jupiter.api.TestInfo
import tse.unblockt.ls.protocol.HealthStatus
import tse.unblockt.ls.server.analysys.AnalysisEntrypoint
import tse.unblockt.ls.server.analysys.service.NoSessionServices
import tse.unblockt.ls.server.framework.simulateClient
import tse.unblockt.ls.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GradleTest {
    @Test
    fun emptyProjectImportIsNotExecuted() = rkTest {
        init(emptyProjectPath)

        assertTrue { AnalysisEntrypoint.services is NoSessionServices }
    }

    @Test
    fun emptyGradleFileIsHandled() = rkTest {
        init(emptyProjectWithGradlePath)

        val health = AnalysisEntrypoint.services.health
        assertEquals(HealthStatus.ERROR, health.status)
    }

    @Test
    fun nonJVMProjectIsHandled() = rkTest {
        init(emptyProjectWithGradleWithoutJVMPath)

        val health = AnalysisEntrypoint.services.health
        assertEquals(HealthStatus.HEALTHY, health.status)
    }

    @Test
    fun emptyJVMProjectIsHandled(info: TestInfo) = rkTest {
        simulateClient(emptyProjectWithGradleWithJVMPath, info, true) {
            val health = AnalysisEntrypoint.services.health
            assertEquals(HealthStatus.HEALTHY, health.status)

            diagnose()
        }
    }
}