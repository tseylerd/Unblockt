// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

package tse.unblockt.ls.rpc

import kotlinx.serialization.json.JsonElement
import java.lang.reflect.InvocationTargetException

open  class RPCCallException(
    message: String,
    val code: Int,
    val data: JsonElement?
) : Exception(message)

object ErrorCodes {
    const val INTERNAL_ERROR = -32603
    const val CANCELLED_BY_SERVER = -32802
    const val CANCELLED_BY_CLIENT = -32800
    @Suppress("unused")
    const val SERVER_IS_NOT_INITIALIZED = -32002
    const val PARSE_ERROR = -32700
}

internal fun unwrap(t: Throwable): Throwable {
    return when (t) {
        is InvocationTargetException -> unwrap(t.targetException)
        else -> t
    }
}

internal fun errorAsResponse(rpcID: RpcID, ex: Throwable): RpcResponse {
    val code = when (ex) {
        is RPCCallException -> ex.code
        else -> ErrorCodes.INTERNAL_ERROR
    }
    val data = when (ex) {
        is RPCCallException -> ex.data
        else -> null
    }
    return RpcResponse(rpcID, null, RpcError(code, ex.stackTraceToString(), data))
}
