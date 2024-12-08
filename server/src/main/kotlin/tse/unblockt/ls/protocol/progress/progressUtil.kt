// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

package tse.unblockt.ls.protocol.progress

import kotlinx.coroutines.withContext
import tse.unblockt.ls.protocol.*
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

context(WorkDoneProgress)
suspend fun <T> LanguageClient.withProgress(message: String, underProgress: suspend () -> T): T {
    val token = workDoneToken ?: return underProgress()
    return withContext(coroutineContext + ProgressElement(token, this)) {
        progress {
            data = ProgressParams(
                token,
                ProgressBegin(false, message)
            )
        }
        try {
            underProgress()
        } finally {
            progress {
                data = ProgressParams(
                    token,
                    ProgressEnd()
                )
            }
        }
    }
}

suspend fun report(message: String? = null, percentage: Int? = null) {
    val progressElement = coroutineContext[ProgressKey] ?: return
    progressElement.client.progress {
        data = ProgressParams(
            progressElement.token,
            ProgressReport(message = message, percentage = percentage)
        )
    }
}

class ProgressElement(val token: ProgressToken, val client: LanguageClient): CoroutineContext.Element {
    override val key: CoroutineContext.Key<*>
        get() = ProgressKey
}

object ProgressKey: CoroutineContext.Key<ProgressElement>