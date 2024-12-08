// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

package tse.unblockt.ls.rpc

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

internal sealed interface Receivable

internal data class InternalRpcMethodCall(
    val id: RpcID?,
    val method: String,
    val params: RpcParameters?
): Receivable

internal data class InternalRpcResponse(
    val id: RpcID,
    val result: JsonElement?,
    val error: RpcError?
): Receivable

internal sealed class RpcID {
    data class Str(val id: String) : RpcID() {
        override fun asString(): String {
            return id
        }
    }

    data class Num(val id: Int) : RpcID() {
        override fun asString(): String {
            return id.toString()
        }
    }

    abstract fun asString(): String
}

internal data class RpcResponse(
    val id: RpcID,
    val result: JsonElement?,
    val error: RpcError?,
) {
    init {
        if (result == null && error == null) {
            throw IllegalArgumentException("Result or error must be provided")
        }
    }
}

internal sealed class RpcParameters

internal class RpcParametersArray(val elements: List<JsonElement?>) : RpcParameters()
internal class RpcParametersMap(val elements: Map<String, JsonElement?>) : RpcParameters()

@Serializable
internal data class RpcError(
    val code: Int,
    val message: String,
    val data: JsonElement? = null
)

