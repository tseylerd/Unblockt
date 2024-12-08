// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

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
