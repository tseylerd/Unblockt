// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

package tse.unblockt.ls.rpc.reflect

import kotlinx.serialization.json.*
import tse.unblockt.ls.rpc.*

class IllegalStructureException(message: String, ex: Throwable?) : Exception(message, ex)

internal val JsonObject.isMethodCall: Boolean
    get() {
        return this["method"]?.safeExtractElement("method") {
            it.jsonPrimitive.content
        } != null
    }

internal val JsonObject.isResponse: Boolean
    get() {
        return !isMethodCall && (this["result"]?.safeExtractElement("result") {
            it as? JsonNull ?: it.jsonObject
        } != null || this["error"]?.safeExtractElement("error") {
            it.jsonObject
        } != null)
    }

internal fun elementAsObject(element: JsonElement): JsonObject {
    return element.safeExtractElement("object") {
        it.jsonObject
    }
}

internal fun objectAsMethodCall(element: JsonObject): InternalRpcMethodCall {
    verifyRpcVersion(element)
    val id = element["id"]
    val idPrimitive: JsonPrimitive? = id?.safeExtractElement("primitive") {
        it.jsonPrimitive
    }
    val idExtracted = when {
        idPrimitive == null -> null
        idPrimitive.isString -> RpcID.Str(idPrimitive.content)
        else -> idPrimitive.intOrNull?.let { RpcID.Num(it) } ?: throw IllegalStructureException("Invalid id", null)
    }
    val method = element["method"]?.safeExtractElement("method") {
        it.jsonPrimitive.content
    } ?: throw IllegalStructureException("Missing method", null)

    val paramsJson = element["params"]
    val rpcParameters: RpcParameters? = paramsJson?.let { elementAsRpcParameters(it) }
    return InternalRpcMethodCall(idExtracted, method, rpcParameters)
}

internal fun objectAsResponse(element: JsonObject): InternalRpcResponse {
    verifyRpcVersion(element)

    val id = element["id"]
    val idPrimitive: JsonPrimitive? = id?.safeExtractElement("primitive") {
        it.jsonPrimitive
    }
    val idExtracted: RpcID = when {
        idPrimitive == null -> null
        idPrimitive.isString -> RpcID.Str(idPrimitive.content)
        else -> idPrimitive.intOrNull?.let { RpcID.Num(it) } ?: throw IllegalStructureException("Invalid id", null)
    } ?: throw IllegalStructureException("Invalid null id", null)
    val error = element["error"]?.safeExtractElement("error") {
        it.jsonObject
    }?.let { Json.decodeFromJsonElement(RpcError.serializer(), it) }

    val result = element["result"]?.safeExtractElement("result") {
        it as? JsonNull ?: it.jsonObject
    }.takeIf { it !is JsonNull }
    return InternalRpcResponse(idExtracted, result, error)
}

private fun verifyRpcVersion(element: JsonObject) {
    element["jsonrpc"]?.let {
        if (it.safeExtractElement("version") { it.jsonPrimitive }.content != "2.0") {
            throw IllegalStructureException("Invalid jsonrpc version", null)
        }
    }
}

private fun elementAsRpcParameters(params: JsonElement?): RpcParameters = when (params) {
    is JsonObject -> RpcParametersMap(params.mapValues { (_, value) ->
        if (value is JsonNull) null else value
    })
    is JsonArray -> RpcParametersArray(params.map { element ->
        if (element is JsonNull) null else element
    })
    else -> throw IllegalStructureException("Invalid params: $params", null)
}

private fun <E> JsonElement.safeExtractElement(process: String, extractor: (JsonElement) -> E): E {
    return try {
        extractor(this)
    } catch (t: Throwable) {
        throw IllegalStructureException("Failed to extract $process", t)
    }
}