// Copyright 2024 Dmitrii Tseiler. Licensed under the Apache 2.0 Licence

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