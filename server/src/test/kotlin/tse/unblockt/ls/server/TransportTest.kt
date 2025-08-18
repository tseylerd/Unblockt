// Copyright 2024 Dmitrii Tseiler. Licensed under the Apache 2.0 Licence

package tse.unblockt.ls.server

import kotlinx.coroutines.CancellationException
import tse.unblockt.ls.rpc.RPCCallException
import tse.unblockt.ls.util.rkTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TransportTest {
    @Test
    fun errorIsReturnedOnFailedToParse() {
        try {
            rkTest {
                sendChannel.send("Incorrect json")
            }
            assertFalse(true, "Exception was not thrown")
        } catch (e: CancellationException) {
            val cause = e.cause
            assertTrue(cause is RPCCallException, "Unexpected exception thrown: $cause")
        }
    }
}