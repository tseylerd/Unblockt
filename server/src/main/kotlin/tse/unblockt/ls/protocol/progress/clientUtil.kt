// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

package tse.unblockt.ls.protocol.progress

import kotlinx.coroutines.withContext
import tse.unblockt.ls.protocol.LanguageClient
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

suspend fun <T> LanguageClient.withClient(call: suspend () -> T): T {
    return withContext(coroutineContext + ClientElement(this)) {
        call()
    }
}

class ClientElement(val client: LanguageClient): CoroutineContext.Element {
    override val key: CoroutineContext.Key<*>
        get() = ClientKey
}

object ClientKey: CoroutineContext.Key<ClientElement>

suspend fun client(): LanguageClient? {
    return coroutineContext[ClientKey]?.client
}
