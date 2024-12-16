// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

package tse.unblockt.ls.rpc

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.serializer
import org.apache.logging.log4j.kotlin.logger
import tse.unblockt.ls.safe
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.full.callSuspendBy
import kotlin.reflect.full.declaredFunctions
import kotlin.reflect.full.instanceParameter

private val logger = logger("JsonRpc")

class JsonRpcContext {
    internal val json = Json {
        ignoreUnknownKeys = true
        useArrayPolymorphism = false
        explicitNulls = false
    }

    internal val codes = mutableMapOf<StandardError, Int>()
    internal val handlers = mutableListOf<Handler>()

    fun <T> register(impl: T) {
        handlers += Handler(null, impl as Any, name = null)
    }

    fun <T> register(implements: KClass<*>, impl: T) {
        handlers += Handler(implements, impl as Any, name = null)
    }

    fun <T> register(name: String?, impl: T) {
        handlers += Handler(null, impl as Any, name = ClassName(name))
    }

    infix fun whenever(error: StandardError): ErrorHandle {
        return ErrorHandleImpl(codes, error)
    }

    private class ErrorHandleImpl(private val map: MutableMap<StandardError, Int>, private val error: StandardError): ErrorHandle {
        override fun give(code: Int) {
            map[error] = code
        }
    }

    internal data class Handler(val implements: KClass<*>?, val instance: Any, val name: ClassName?)
    internal data class ClassName(val name: String?)
}

interface ErrorHandle {
    fun give(code: Int)
}

enum class StandardError(val defaultCode: Int) {
    METHOD_NOT_FOUND(404)
}

sealed class Transport {
    data object StdIO: Transport()
    data class Channel(val receive: kotlinx.coroutines.channels.Channel<String>, val response: kotlinx.coroutines.channels.Channel<String>): Transport()
}

data class RpcHandle(
    val start: suspend ((CoroutineScope) -> Unit) -> Unit,
    val stop: suspend () -> Unit,
)

fun jsonRpc(transport: Transport, receiveChannel: ReceiveChannel<RPCMethodCall<*, *>>, sendChannel: SendChannel<Any?>, builder: JsonRpcContext.() -> Unit): RpcHandle {
    val context = JsonRpcContext()
    context.builder()
    val internalTransport = InternalTransport.from(transport, context.json)
    internalTransport.onRequest { request ->
        process(context, request)
    }
    return RpcHandle(
        start = { callback ->
            coroutineScope {
                callback(this)
                launch {
                    while (true) {
                        val value = receiveChannel.receive()
                        val result = logger.safe {
                            internalTransport.callClientMethod(value)
                        }
                        if (result.isFailure) {
                            tse.unblockt.ls.rpc.logger.error("Client call failed", result.exceptionOrNull())
                        }
                        else if (!value.isNotification) {
                            sendChannel.send(result.getOrThrow())
                        }
                    }
                }

                internalTransport.start()
            }
        },
        stop = { internalTransport.stop() }
    )
}

private suspend fun process(context: JsonRpcContext, request: InternalRpcMethodCall): RpcResponse? {
    val method = request.method
    val params = request.params
    val typedMethodCall = context.findMethod(method, params)
    if (typedMethodCall == null && request.id != null) {
        val response = RpcResponse(
            request.id,
            null,
            RpcError(context.errorCode(StandardError.METHOD_NOT_FOUND), "Method not found")
        )
        return response
    }
    if (typedMethodCall == null) {
        logger.warn("Method not found: $method")
        return null
    }
    val (instance, handler, args) = typedMethodCall
    val paramsMap = args.toMutableMap()
    paramsMap[handler.instanceParameter!!] = instance
    val result = try {
        when {
            handler.isSuspend -> handler.callSuspendBy(paramsMap)
            else -> handler.callBy(paramsMap)
        }
    } catch (c: CancellationException) {
      throw c
    } catch (t: Throwable) {
        val ex = unwrap(t)
        if (ex !is RPCCallException || ex.code != ErrorCodes.CANCELLED_BY_SERVER) {
            logger.error(ex.message ?: "No message", ex)
        }
        if (request.id != null) {
            return errorAsResponse(request.id, ex)
        } else {
            null
        }

    }
    val isUnit = result == Unit
    val element = if (isUnit) {
        null
    } else {
        context.json.asJson(result)
    }

    return when (request.id) {
        null -> null
        else -> when {
            isUnit -> null
            else -> RpcResponse(request.id, element, null)
        }
    }
}

private fun JsonRpcContext.errorCode(error: StandardError): Int {
    return codes[error] ?: error.defaultCode
}

private fun JsonRpcContext.findMethod(reference: String, params: RpcParameters?): TypedMethodCall? {
    val splitted = reference.split("/")
    if (splitted.isEmpty()) {
        logger.info("Invalid method reference: $reference")
        return null
    }

    val (className, methodName) = when {
        splitted.size == 2 -> splitted[0] to splitted[1]
        splitted.size > 2 -> splitted[0] to splitted[1] + splitted.drop(2).filterNot { it.isBlank() }.joinToString("") { it[0].uppercase() + it.drop(1) }
        else -> null to splitted[0]
    }
    for (handler in handlers) {
        val instance = handler.instance
        val functions = handler.implements?.declaredFunctions ?: instance::class.declaredFunctions
        val simpleClassName = when (handler.name) {
            null -> handler.implements?.simpleName ?: instance::class.simpleName
            else -> handler.name.name
        }
        if (simpleClassName.equals(className, true)) {
            for (function in functions) {
                if (function.name == methodName) {
                    val parameters = function.parameters.drop(1)
                    if (params is RpcParametersArray && parameters.size == params.elements.size) {
                        val matches = params.elements.zip(parameters).map { (rpc, method) ->
                            tryMatchParameterTypes(rpc, method)
                        }
                        if (matches.all { it.matches }) {
                            return TypedMethodCall(instance, function, matches.associate { mr ->
                                mr.parameter to mr.value
                            })
                        }
                    } else if (params is RpcParametersMap) {
                        if (parameters.size == params.elements.size) {
                            val matches = parameters.map { p ->
                                val containsKey = params.elements.containsKey(p.name)
                                if (!containsKey) {
                                    MatchResult(false, p, null)
                                }
                                else {
                                    tryMatchParameterTypes(params.elements[p.name], p)
                                }
                            }
                            if (matches.all { it.matches }) {
                                return TypedMethodCall(instance, function, matches.associate { mr ->
                                    mr.parameter to mr.value
                                })
                            }
                        }
                        if (parameters.size == 1) {
                            val jsonMap = JsonObject(params.elements.mapValues { (_, v) ->
                                v ?: JsonNull
                            })
                            val matched = tryMatchParameterTypes(jsonMap, parameters.single())
                            if (matched.matches) {
                                return TypedMethodCall(instance, function, mapOf(parameters.single() to matched.value))
                            }
                        }
                    } else if (params == null && parameters.isEmpty()) {
                        return TypedMethodCall(instance, function, emptyMap())
                    }
                }
            }
        }
    }
    return null
}

private fun JsonRpcContext.tryMatchParameterTypes(rpc: JsonElement?, methodParam: KParameter): MatchResult {
    if (rpc == null) {
        return MatchResult(methodParam.type.isMarkedNullable, methodParam,null)
    }

    val type = methodParam.type
    val serializer = serializer(type)
    val decoded = try {
        json.decodeFromJsonElement(serializer, rpc)
    } catch (t: Throwable) {
        return MatchResult(false, methodParam, null)
    }
    return MatchResult(true, methodParam, decoded)
}

private data class MatchResult(val matches: Boolean, val parameter: KParameter, val value: Any?)

internal data class TypedMethodCall(
    val instance: Any,
    val function: KFunction<*>,
    val arguments: Map<KParameter, Any?>
)