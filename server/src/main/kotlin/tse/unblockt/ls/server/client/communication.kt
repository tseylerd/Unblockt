// Copyright 2024 Dmitrii Tseiler. Licensed under the Apache 2.0 Licence

package tse.unblockt.ls.server.client

import tse.unblockt.ls.protocol.MessageParams
import tse.unblockt.ls.protocol.MessageType
import tse.unblockt.ls.protocol.progress.ClientKey
import kotlin.coroutines.coroutineContext

enum class ClientLog {
    GRADLE
}

suspend fun error(log: ClientLog, message: String) {
    if (message.isBlank()) return
    val clientElement = coroutineContext[ClientKey] ?: return
    val client = clientElement.client
    client.unblockt {
        messages {
            gradle {
                message {
                    data = MessageParams(message, MessageType.ERROR)
                }
            }
        }
    }
}

suspend fun message(log: ClientLog, message: String) {
    if (message.isBlank()) {
        return
    }
    val clientElement = coroutineContext[ClientKey] ?: return
    val client = clientElement.client
    client.unblockt {
        messages {
            gradle {
                message {
                    data = MessageParams(message, MessageType.INFO)
                }
            }
        }
    }
}