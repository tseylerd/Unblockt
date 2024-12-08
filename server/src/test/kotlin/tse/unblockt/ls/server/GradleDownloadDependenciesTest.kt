// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

package tse.unblockt.ls.server

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.extension.ExtendWith
import tse.unblockt.ls.util.init
import tse.unblockt.ls.util.rkTest
import tse.unblockt.ls.util.testProjectPath
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables
import uk.org.webcompere.systemstubs.jupiter.SystemStub
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension
import java.nio.file.Files
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.test.Ignore
import kotlin.test.assertTrue

@Ignore
@ExtendWith(SystemStubsExtension::class)
class GradleDownloadDependenciesTest {
    private companion object {
        private val tempGradleHome = testProjectPath.resolve("gradleHome")
    }

    @Suppress("unused")
    @SystemStub
    private var vars: EnvironmentVariables = EnvironmentVariables(
        "GRADLE_USER_HOME", tempGradleHome.toString()
    )

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun dependenciesAreDownloaded() {
        rkTest {
            if (tempGradleHome.exists()) {
                tempGradleHome.deleteRecursively()
            }
            assertDoesNotThrow {
                init(testProjectPath)
            }

            assertTrue { Files.exists(tempGradleHome) }
        }
    }
}