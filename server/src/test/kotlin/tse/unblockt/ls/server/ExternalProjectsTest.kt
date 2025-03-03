// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

package tse.unblockt.ls.server

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.TestInfo
import tse.unblockt.ls.server.framework.simulateClient
import tse.unblockt.ls.util.rkTest
import java.nio.file.Paths
import kotlin.test.Ignore
import kotlin.test.Test

@Ignore
class ExternalProjectsTest {
    @Ignore
    @Nested
    inner class Kover {
        @Ignore
        @Test
        fun javaFileResolved(info: TestInfo) = rkTest {
            simulateClient(Paths.get("<kover_path>"), info) {
                diagnose()
            }
        }
    }
}