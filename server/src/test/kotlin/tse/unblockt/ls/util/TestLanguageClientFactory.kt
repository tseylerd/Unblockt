// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

package tse.unblockt.ls.util

import kotlinx.coroutines.channels.SendChannel
import tse.unblockt.ls.protocol.LanguageClient
import tse.unblockt.ls.rpc.RPCMethodCall

class TestLanguageClientFactory(private val channel: SendChannel<RPCMethodCall<*, *>>): LanguageClient.Factory {
    override fun create(send: suspend (RPCMethodCall<*, *>) -> Any?): LanguageClient {
        return LanguageClient(LanguageClient.ClientData("test")) { call ->
            channel.send(call)
        }
    }
}