// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

package tse.unblockt.ls.server.threading

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import tse.unblockt.ls.protocol.LanguageClient
import tse.unblockt.ls.protocol.progress.withClient
import tse.unblockt.ls.rpc.CancellationType
import tse.unblockt.ls.rpc.CancellationWithResult
import tse.unblockt.ls.rpc.InternalTransport
import java.util.*
import java.util.concurrent.Executors
import kotlin.coroutines.coroutineContext

class LsCallEntrypoint(private val client: LanguageClient) {
    private val readDispatcher = Executors.newVirtualThreadPerTaskExecutor().asCoroutineDispatcher()
    private val writeMutex = Mutex()

    @Volatile
    private var writers: Int = 0
    @Volatile
    private var readers: Int = 0

    private val allRunningRequests = mutableMapOf<String, LsJob>()

    fun cancelRequest(id: String, type: CancellationType) {
        val job = allRunningRequests[id]
        job?.let {
            val operation = job.operation
            it.job.cancel(when(operation) {
                is Operation.ReadOperation.CancellableOperation<*> -> CancellationWithResult(operation.onCancelled)
                else -> CancellationException(type.toString())
            })
            allRunningRequests.remove(id)
        }
    }

    suspend fun <T> write(operation: Operation.WriteOperation, block: suspend () -> T): T {
        return withJob(operation) {
            writers++
            cancelAllCancellable()

            try {
                writeMutex.withLock {
                    while (readers > 0) {
                        delay(10)
                    }
                    client.withClient(block)
                }
            } finally {
                writers--
            }
        }
    }

    suspend fun <T> read(operation: Operation.ReadOperation, block: suspend () -> T): T {
        return withJob(operation) {
            while (writers > 0) {
                delay(10)
            }

            readers++
            try {
                withContext(readDispatcher) {
                    client.withClient(block)
                }
            } finally {
                readers--
            }
        }
    }

    sealed class Operation {
        abstract val name: String

        sealed class ReadOperation : Operation() {
            abstract val cancellable: Boolean

            data class SimpleOperation(
                override val name: String,
                override val cancellable: Boolean,
            ): ReadOperation()

            data class CancellableOperation<T: Any>(
                override val name: String,
                override val cancellable: Boolean = true,
                val onCancelled: T
            ): ReadOperation()

        }

        data class WriteOperation(
            override val name: String,
        ): Operation()
    }

    private fun cancelAllCancellable() {
        val allToCancel = mutableSetOf<Pair<String, LsJob>>()
        for ((key, value) in allRunningRequests) {
            if (value.operation is Operation.ReadOperation && value.operation.cancellable) {
                allToCancel += key to value
            }
        }
        for (s in allToCancel) {
            cancelRequest(s.first, CancellationType.SERVER)
        }
    }

    private suspend fun <T> withJob(operation: Operation, job: suspend () -> T): T {
        val ctx = coroutineContext
        val reqId = ctx[InternalTransport.RequestIdContextElement]
        val requestId = ctx[InternalTransport.RequestIdContextElement]?.id ?: UUID.randomUUID().toString()
        allRunningRequests[requestId] = LsJob(
            ctx.job,
            operation,
            System.currentTimeMillis(),
        )
        try {
            return job()
        } finally {
            if (reqId != null) {
                allRunningRequests.remove(requestId)
            }
        }
    }

    private data class LsJob(
        val job: Job,
        val operation: Operation,
        val submitTime: Long,
    )
}