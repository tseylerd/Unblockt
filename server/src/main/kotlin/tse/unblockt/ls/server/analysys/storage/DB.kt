// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

@file:Suppress("SqlNoDataSourceInspection")

package tse.unblockt.ls.server.analysys.storage

import com.intellij.openapi.project.Project
import tse.unblockt.ls.server.util.State
import java.nio.file.Path

interface DB: AutoCloseable {
    companion object {
        fun indexesPath(path: Path): Path {
            return path.resolve("indexes")
        }
    }

    val isValid: Boolean

    val isClosed: Boolean

    fun init(): InitializationResult
    fun init(name: String)

    fun delete()

    fun <M: Any, K: Any, V: Any> putAll(name: String, attribute: Attribute<M, K, V>, triples: Set<Triple<M, K, V>>)
    fun <M: Any, K: Any, V: Any> put(name: String, attribute: Attribute<M, K, V>, meta: M, key: K, value: V)
    fun <M: Any, K: Any, V: Any> allKeys(name: String, attribute: Attribute<M, K, V>): Sequence<K>
    fun <M: Any, K: Any, V: Any> allValues(name: String, attribute: Attribute<M, K, V>): Sequence<V>
    fun <M: Any, K: Any, V: Any> all(name: String, attribute: Attribute<M, K, V>): Sequence<Pair<K, V>>

    fun <M: Any, K: Any, V: Any> sequence(name: String, attribute: Attribute<M, K, V>): Sequence<Triple<M, K, V>>
    fun <M: Any, K: Any, V: Any> metas(name: String, attribute: Attribute<M, K, V>): Sequence<M>
    fun <M: Any, K: Any, V: Any> values(name: String, attribute: Attribute<M, K, V>, key: K): Sequence<V>
    fun <M: Any, K: Any, V: Any> deleteByMeta(name: String, attribute: Attribute<M, K, V>, meta: M)
    fun <M: Any, K: Any, V: Any> exists(name: String, attribute: Attribute<M, K, V>, key: K): Boolean
    fun <M: Any, K: Any, V: Any> mayContain(name: String, attribute: Attribute<M, K, V>, key: K): Boolean

    data class Attribute<M: Any, K: Any, V: Any>(
        val name: String,
        val metaToString: (M) -> String,
        val stringToMeta: (String) -> M,
        val keyToString: (K) -> String,
        val valueToString: (V) -> String,
        val stringToKey: (Project, String) -> K?,
        val stringToValue: (Project, String) -> V?,
        val shared: State = State.UNDEFINED,
    ) {
        init {
            if (name.contains(".")) {
                throw IllegalArgumentException("Attribute $name contains dot")
            }
        }
    }
}

class InitializationResult(val wiped: Boolean, val success: Boolean)