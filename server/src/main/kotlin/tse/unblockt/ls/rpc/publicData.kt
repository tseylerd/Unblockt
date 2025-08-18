// Copyright 2024 Dmitrii Tseiler. Licensed under the Apache 2.0 Licence

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