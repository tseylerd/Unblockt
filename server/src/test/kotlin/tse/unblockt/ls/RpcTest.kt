// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

package tse.unblockt.ls

import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import tse.unblockt.ls.rpc.Transport
import tse.unblockt.ls.rpc.jsonRpc
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Suppress("unused")
class RpcTest {
    @Test
    @Ignore
    fun testMethodsCalled() = runBlocking {
        val receive = Channel<String>()
        val respond = Channel<String>()
        val instance = TestInstance()
        val impl = Init()
        val (start, stop) = jsonRpc(Transport.Channel(receive, respond), Channel(), Channel()) {
            register(instance)
            register(null, impl)
        }
        val processingJob = launch {
            start {}
        }
        val responses = mutableListOf<String>()
        val responsesJob = launch {
            try {
                while (true) {
                    val response = respond.receive()
                    responses.add(response)
                }
            } catch (ignore: Throwable) {
            }
        }

        receive.send("""{"jsonrpc":"2.0","method":"testInstance/printString","params":[]}""")
        receive.send("""{"jsonrpc":"2.0","method":"testInstance/printString","params":[1]}""")
        receive.send("""{"jsonrpc":"2.0","method":"testInstance/printString","params":["a"]}""")
        receive.send("""{"jsonrpc":"2.0","method":"testInstance/printString","params":[{"a":1}]}""")
        receive.send("""{"jsonrpc":"2.0","method":"testInstance/printString","params":[["a"]]}""")
        receive.send("""{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"processId":1}}""")
        receive.send("""{"jsonrpc":"2.0","method":"testInstance/printString/many","params":{"string":"hahaha"}}""")

        delay(100)

        assertTrue(instance.printStringCalled)
        assertTrue(instance.printStringIntCalled)
        assertTrue(instance.printStringStringCalled)
        assertTrue(instance.printStringMapCalled)
        assertTrue(instance.printStringListCalled)
        assertTrue(instance.printStringFullCalled)
        assertTrue(impl.initialized)

        receive.close()
        respond.close()
        stop()
        receive.close(null)

        processingJob.cancelAndJoin()
        responsesJob.cancelAndJoin()

        assertEquals(1, responses.size)
        assertEquals("""{"jsonrpc":"2.0","id":1,"result":{"text":"Hey!"}}""", responses[0])
    }

    class Init {
        @Volatile
        var initialized = false

        fun initialize(params: InitRequest): InitResponse {
            println(params)
            initialized = true
            return InitResponse("Hey!")
        }

        @Serializable
        data class InitRequest(val processId: Int)

        @Serializable
        data class InitResponse(val text: String)
    }

    class TestInstance {
        @Volatile var printStringCalled = false
        @Volatile var printStringIntCalled = false
        @Volatile var printStringStringCalled = false
        @Volatile var printStringMapCalled = false
        @Volatile var printStringListCalled = false
        @Volatile var printStringFullCalled = false

        fun printString() {
            printStringCalled = true
        }

        fun printString(a: Int) {
            printStringIntCalled = true
        }

        fun printString(a: String) {
            printStringStringCalled = true
        }

        fun printString(a: Map<String, Int>) {
            printStringMapCalled = true
        }

        fun printString(a: List<String>) {
            printStringListCalled = true
        }

        fun printStringMany(string: String) {
            printStringFullCalled = true
        }
    }
}