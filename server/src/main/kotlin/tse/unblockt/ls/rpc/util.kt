// Copyright 2024 Dmitrii Tseiler. Licensed under the Apache 2.0 Licence

package tse.unblockt.ls.rpc

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.serializer
import kotlin.reflect.full.createType

internal fun Json.asJson(
    result: Any?
): JsonElement = when (result) {
    null -> JsonNull
    is Collection<*> -> JsonArray(result.mapNotNull { el -> asJson(el) })
    else -> encodeToJsonElement(serializer(result::class.createType()), result)
}
