// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

package tse.unblockt.ls.rpc

import kotlinx.serialization.Serializable
import kotlin.reflect.KClass

@Serializable
data class RPCMethodCall<T: Any, R: Any>(
    val method: String,
    val data: T?,
    val clazz: KClass<T>?,
    val responseClazz: KClass<R>,
    val isNotification: Boolean = true
)

enum class CancellationType {
    SERVER,
    CLIENT
}