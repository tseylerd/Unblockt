// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

@file:Suppress("SqlNoDataSourceInspection")

package tse.unblockt.ls.server.analysys.storage

import com.intellij.openapi.project.Project
import java.nio.file.Path

interface DB: AutoCloseable {
    companion object {
        fun indexesPath(path: Path): Path {
            return path.resolve("indexes")
        }
    }

    val isValid: Boolean

    val isClosed: Boolean
    fun init()
    fun init(name: String, config: Store.Config)

    fun tx(): Tx

    fun delete()

    interface Tx {
        fun commit(): Boolean
        val isFinished: Boolean

        fun <M: Any, K: Any, V: Any> store(name: String, attribute: Attribute<M, K, V>): Store<M, K, V>
        fun revert()
        fun abort()
    }

    interface Store<M, K: Any, V: Any> {
        fun putAll(triples: Set<Triple<M, K, V>>)
        fun put(meta: M, key: K, value: V)
        fun allKeys(): Sequence<K>
        fun allValues(): Sequence<V>
        fun all(): Sequence<Pair<K, V>>

        fun sequence(): Sequence<Triple<M, K, V>>
        fun values(key: K): Sequence<V>
        fun deleteByMeta(meta: M)
        fun exists(key: K): Boolean
        fun mayContain(key: K): Boolean

        enum class Config {
            UNIQUE_KEY_VALUE,
            UNIQUE_KEY,
            UNIQUE_RECORD,
            SINGLE
        }
    }

    data class Attribute<M: Any, K: Any, V: Any>(
        val name: String,
        val metaToString: (M) -> String,
        val stringToMeta: (String) -> M,
        val keyToString: (K) -> String,
        val valueToString: (V) -> String,
        val stringToKey: (Project, String) -> K?,
        val stringToValue: (Project, String) -> V?,
        val config: Store.Config,
        val forceLocal: Boolean = false
    ) {
        init {
            if (name.contains(".")) {
                throw IllegalArgumentException("Attribute $name contains dot")
            }
        }
    }
}

inline fun <T> DB.inTx(call: DB.Tx.() -> T): T {
    val tx = tx()
    try {
        while (true) {
            val result = tx.call()
            if (tx.commit()) {
                return result
            }
            tx.revert()
        }
    } finally {
        if (!tx.isFinished) {
            tx.abort()
        }
    }
}