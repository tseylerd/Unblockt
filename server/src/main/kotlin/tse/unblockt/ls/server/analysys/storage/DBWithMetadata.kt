// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

package tse.unblockt.ls.server.analysys.storage

import java.nio.file.Path

internal class DBWithMetadata(private val key: String, private val path: Path, private val factory: () -> DB): CompletableDB {
    internal lateinit var metadataDB: SafeDB

    private lateinit var delegate: DB

    override fun complete() {
        val curDB = delegate
        if (curDB is CompletableDB) {
            curDB.complete()
        }
    }

    override fun isComplete(meta: String): Boolean {
        val curDB = delegate
        return curDB is CompletableDB && curDB.isComplete(meta)
    }

    override val isValid: Boolean
        get() = delegate.isValid
    override val isClosed: Boolean
        get() = delegate.isClosed

    override fun init(): Wiped {
        if (::delegate.isInitialized) {
            return Wiped(false)
        }
        val lockPath = path.resolve("meta_$key")
        metadataDB = SharedDBWrapperImpl {
            MDB.openOrCreateDB(lockPath) {
                MDB.makeMetaDB(lockPath)
            }.first
        }

        delegate = factory()
        return delegate.init()
    }

    override fun init(name: String) {
        delegate.init(name)
    }

    override fun delete() {
        delegate.delete()
    }

    override fun <M : Any, K : Any, V : Any> put(
        name: String,
        attribute: DB.Attribute<M, K, V>,
        meta: M,
        key: K,
        value: V
    ) {
        delegate.put(name, attribute, meta, key, value)
    }

    override fun <M : Any, K : Any, V : Any> putAll(
        name: String,
        attribute: DB.Attribute<M, K, V>,
        triples: Set<Triple<M, K, V>>
    ) {
        delegate.putAll(name, attribute, triples)
    }

    override fun <M : Any, K : Any, V : Any> allKeys(name: String, attribute: DB.Attribute<M, K, V>): Sequence<K> {
        return delegate.allKeys(name, attribute)
    }

    override fun <M : Any, K : Any, V : Any> allValues(name: String, attribute: DB.Attribute<M, K, V>): Sequence<V> {
        return delegate.allValues(name, attribute)
    }

    override fun <M : Any, K : Any, V : Any> all(name: String, attribute: DB.Attribute<M, K, V>): Sequence<Pair<K, V>> {
        return delegate.all(name, attribute)
    }

    override fun <M : Any, K : Any, V : Any> sequence(
        name: String,
        attribute: DB.Attribute<M, K, V>
    ): Sequence<Triple<M, K, V>> {
        return delegate.sequence(name, attribute)
    }

    override fun <M : Any, K : Any, V : Any> metas(name: String, attribute: DB.Attribute<M, K, V>): Sequence<M> {
        return delegate.metas(name, attribute)
    }

    override fun <M : Any, K : Any, V : Any> values(
        name: String,
        attribute: DB.Attribute<M, K, V>,
        key: K
    ): Sequence<V> {
        return delegate.values(name, attribute, key)
    }

    override fun <M : Any, K : Any, V : Any> deleteByMeta(name: String, attribute: DB.Attribute<M, K, V>, meta: M) {
        delegate.deleteByMeta(name, attribute, meta)
    }

    override fun <M : Any, K : Any, V : Any> exists(name: String, attribute: DB.Attribute<M, K, V>, key: K): Boolean {
        return delegate.exists(name, attribute, key)
    }

    override fun <M : Any, K : Any, V : Any> mayContain(
        name: String,
        attribute: DB.Attribute<M, K, V>,
        key: K
    ): Boolean {
        return delegate.mayContain(name, attribute, key)
    }

    override fun close() {
        delegate.close()
        metadataDB.close()
    }
}