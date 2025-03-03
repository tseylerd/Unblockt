// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

package tse.unblockt.ls.rpc

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.*
import kotlinx.serialization.serializer
import org.apache.logging.log4j.kotlin.logger
import tse.unblockt.ls.protocol.ErrorType
import tse.unblockt.ls.rpc.reflect.*
import tse.unblockt.ls.safe
import java.io.PrintWriter
import java.io.Writer
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.concurrent.Executors
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

internal abstract class InternalTransport(private val json: Json) {
    companion object {
        private val ourReceiveDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

        const val CONTENT_LENGTH = "Content-Length: "

        fun from(transport: Transport, json: Json): InternalTransport {
            return when (transport) {
                is Transport.StdIO -> StdIO(json)
                is Transport.Channel -> ChannelTransport(transport.receive, transport.response, json)
            }
        }
    }

    private var handler: suspend (InternalRpcMethodCall) -> RpcResponse? = { null }
    private var active: Boolean = false

    private val responsesFlow = MutableSharedFlow<InternalRpcResponse>()

    private val outChannel: Channel<String?> = Channel()

    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun start() {
        active = true

        coroutineScope {
            launch(ourReceiveDispatcher) {
                while (active) {
                    logger.safe {
                        val received = try {
                            receive() ?: return@launch
                        } catch (e: ParsingError) {
                            logger.warn("Parsing error", e)
                            null
                        }

                        logger.debug("Received $received")
                        val element = when (received) {
                            null -> null
                            else -> try {
                                json.parseToJsonElement(received)
                            } catch (e: SerializationException) {
                                logger.warn("Failed to parse $received element")
                                null
                            }
                        }
                        val receivable: Receivable = when {
                            element == null -> InternalRpcMethodCall(
                                null,
                                "error",
                                RpcParametersArray(listOf(JsonPrimitive(ErrorType.PARSING_ERROR.value))),
                            )
                            else -> {
                                val obj: JsonObject = elementAsObject(element)
                                when {
                                    obj.isMethodCall -> objectAsMethodCall(obj)
                                    obj.isResponse -> objectAsResponse(obj)
                                    else -> throw IllegalStructureException("Can't parse $obj", null)
                                }
                            }
                        }
                        when (receivable) {
                            is InternalRpcResponse -> responsesFlow.emit(receivable)
                            is InternalRpcMethodCall -> {
                                launch(ourReceiveDispatcher) {
                                    supervisorScope {
                                        callServerMethod(receivable)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            launch(Dispatchers.IO.limitedParallelism(1)) {
                while (active) {
                    logger.safe {
                        val value = try {
                            outChannel.receive() ?: return@launch
                        } catch (e: CancellationException) {
                            return@launch
                        }
                        respond(value)
                    }
                }
            }
        }
    }

    private fun CoroutineScope.respondError(code: Int, message: String) {
        launch(ourReceiveDispatcher) {
            supervisorScope {
                val buildMap = buildMap(null, null, null, RpcError(code, message, null), null)
                outChannel.send(JsonObject(buildMap).toString())
            }
        }
    }

    private suspend fun callServerMethod(call: InternalRpcMethodCall) {
        val response: RpcResponse? = try {
            val id = call.id
            if (id != null) {
                withContext(coroutineContext + RequestIdContextElement(id.asString())) {
                    handler(call)
                }
            } else {
                handler(call)
            }
        }
        catch (t: CancellationException) {
            val id = call.id
            if (id != null && t is CancellationWithResult) {
                RpcResponse(id, json.asJson(t.result), null)
            } else {
                val message = t.message
                if (message == null) {
                    null
                } else {
                    val type = try {
                        CancellationType.valueOf(message)
                    } catch (e: IllegalArgumentException) {
                        null
                    }

                    if (type == null) {
                        null
                    } else {
                        errorAsResponse(id, RPCCallException("Cancelled", when(type) {
                            CancellationType.CLIENT -> ErrorCodes.CANCELLED_BY_CLIENT
                            CancellationType.SERVER -> ErrorCodes.CANCELLED_BY_SERVER
                        }, null))
                    }
                }
            }
        }
        catch (t: Throwable) {
            call.id?.let {
                errorAsResponse(it, t)
            }
        }

        if (response == null) {
            return
        }

        val content = buildMap(null, response.id, response.result, response.error, "result")
        logger.debug("Response: $content")
        outChannel.send(JsonObject(content).toString())
    }

    @OptIn(InternalSerializationApi::class)
    suspend fun <T: Any, R: Any> callClientMethod(value: RPCMethodCall<T, R>): R? {
        logger.debug("Processing call: $value")
        val id = when {
            value.isNotification -> null
            else -> RpcID.Str(UUID.randomUUID().toString())
        }
        val content = buildMap(
            value.method,
            id,
            encodeToJsonElement(value),
            null,
            "params"
        )
        outChannel.send(JsonObject(content).toString())
        if (id == null) {
            return null
        }

        val response = responsesFlow.first { response ->
            response.id == id
        }

        if (response.error != null) {
            throw RPCCallException(response.error.message, response.error.code, null)
        }

        if (response.result == null) {
            return null
        }

        return Json.decodeFromJsonElement(value.responseClazz.serializer(), response.result)
    }

    fun stop() {
        active = false
    }


    fun onRequest(handler: suspend (InternalRpcMethodCall) -> RpcResponse?) {
        this.handler = handler
    }

    protected abstract suspend fun receive(): String?
    protected abstract suspend fun respond(value: String)

    @OptIn(InternalSerializationApi::class)
    private fun <T: Any> encodeToJsonElement(call: RPCMethodCall<T, *>): JsonElement? {
        val clazz = call.clazz ?: return null
        val data = call.data ?: return null
        return json.encodeToJsonElement(clazz.serializer(), data)
    }

    private fun buildMap(method: String?, id: RpcID?, data: JsonElement?, error: RpcError?, dataKey: String?): Map<String, JsonElement> {
        val content = mutableMapOf<String, JsonElement>(
            "jsonrpc" to JsonPrimitive("2.0"),
        )
        if (method != null) {
            content["method"] = JsonPrimitive(method)
        }
        if (id != null) {
            content += "id" to when (id) {
                is RpcID.Str -> JsonPrimitive(id.id)
                is RpcID.Num -> JsonPrimitive(id.id)
            }
        }
        // todo handle rpc call parameters
        if (data != null && dataKey != null) {
            content[dataKey] = data
        }
        if (error != null) {
            content["error"] = json.encodeToJsonElement(error)
        }

        return content
    }

    class StdIO(json: Json) : InternalTransport(json) {
        private val writer: Writer = PrintWriter(System.out)

        override suspend fun receive(): String? {
            val lengthString = withContext(Dispatchers.IO) {
                readlnOrNull()
            } ?: return null

            if (!lengthString.startsWith(CONTENT_LENGTH)) {
                throw ParsingError("Received $lengthString, expected Content-Length: ")
            }
            val lengthHeader = try {
                lengthString.substringAfter(CONTENT_LENGTH).toInt()
            } catch (e: NumberFormatException) {
                throw ParsingError("Failed to extract number from $lengthString", e)
            }
            var lastLine = withContext(Dispatchers.IO) {
                readlnOrNull()
            } ?: return null
            if (lastLine.startsWith("Content-Type")) {
               lastLine = withContext(Dispatchers.IO) {
                   readlnOrNull()
               } ?: return null
            }

            if (lastLine.isNotBlank()) {
                throw ParsingError("Received $lastLine, expected empty line")
            }

            val bytes = withContext(Dispatchers.IO) {
                System.`in`.readNBytes(lengthHeader)
            }
            return String(bytes, StandardCharsets.UTF_8)
        }

        override suspend fun respond(value: String) {
            withContext(Dispatchers.IO) {
                val allValue = "$CONTENT_LENGTH${value.length}\r\n\r\n$value"
                writer.write(allValue)
                writer.flush()
            }
        }
    }

    class ChannelTransport(private val inChannel: ReceiveChannel<String>, private val outChannel: SendChannel<String>, json: Json) : InternalTransport(json) {
        override suspend fun receive(): String? {
            return try {
                inChannel.receive()
            } catch (e: ClosedReceiveChannelException) {
                null
            } catch (e: CancellationException) {
                null
            }
        }

        override suspend fun respond(value: String) {
            outChannel.send(value)
        }
    }

    class RequestIdContextElement(val id: String): CoroutineContext.Element {
        companion object: CoroutineContext.Key<RequestIdContextElement>
        override val key: CoroutineContext.Key<*>
            get() = RequestIdContextElement
    }

    class ParsingError(message: String, cause: Throwable? = null): Exception(message, cause)
}