package tse.unblockt.ls.server

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo
import tse.unblockt.ls.protocol.HealthStatus
import tse.unblockt.ls.server.analysys.AnalysisEntrypoint
import tse.unblockt.ls.server.framework.simulateClient
import tse.unblockt.ls.util.rkTest
import tse.unblockt.ls.util.testAndroidProjectPath
import java.nio.file.Files
import kotlin.test.assertEquals

class AndroidTest {
    @Test
    fun highlightingWorks(info: TestInfo) = rkTest {
        simulateClient(testAndroidProjectPath, info) {
            highlight()
        }
    }

    @Test
    fun diagnosticsWorks(info: TestInfo) = rkTest {
        simulateClient(testAndroidProjectPath, info) {
            diagnose()
        }
    }

    @Test
    fun versionOfGradlePluginIsChecked(info: TestInfo) = rkTest {
        val pathToToml = testAndroidProjectPath.resolve("gradle").resolve("libs.versions.toml")
        val textBefore = Files.readString(pathToToml)
        try {
            Files.writeString(pathToToml, textBefore.replace("2.1.0", "2.0.20"))
            simulateClient(testAndroidProjectPath, info) {
                val health = AnalysisEntrypoint.services.health
                assertEquals(HealthStatus.HEALTHY, health.status)
            }
        } finally {
            Files.writeString(pathToToml, textBefore)
        }
    }
}