// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

package tse.unblockt.ls.server

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.junit.jupiter.api.Test
import tse.unblockt.ls.rpc.RPCMethodCall
import tse.unblockt.ls.util.init
import tse.unblockt.ls.util.rkTest
import tse.unblockt.ls.util.testProjectPath
import kotlin.test.assertTrue

class GradleImportTest {
    @Test
    fun testProgress() = rkTest {
        val callsList = mutableListOf<RPCMethodCall<*, *>>()
        val callsJob = scope.launch {
            for (rpcMethodCall in clientsCallChannel) {
                callsList.add(rpcMethodCall)
            }
        }
        try {
            init(testProjectPath)
            delay(200)

            assertTrue {
                callsList.isNotEmpty()
            }
        } finally {
            callsJob.cancel()
        }
    }
}