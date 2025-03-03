// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

package tse.unblockt.ls.server.analysys.storage

import org.mapdb.Atomic
import java.nio.file.Path

class VersionedDB(private val path: Path, private val factory: () -> DB): CompletableDB {
    companion object {
        private const val META_DB_KEY = "version"
        private const val VERSION_KEY = "VersionedDB.version"
        private const val CREATED_TIME_KEY = "VersionedDB.createdAt"
    }


    val dbVersion: Long
        get() = version.read { it.get() }
    val createdAt: Long
        get() = creationTime.read { it.get() }

    override val isValid: Boolean
        get() = delegate.isValid
    override val isClosed: Boolean
        get() = delegate.isClosed

    private lateinit var delegate: DBWithMetadata
    private lateinit var version: SafeDBResource<Atomic.Long>
    private lateinit var creationTime: SafeDBResource<Atomic.Long>

    override fun init(): Wiped {
        if (::version.isInitialized) {
            return Wiped(false)
        }

        val result = recreate()

        val versionValue = version.read { it.get() }

        val currentVersion = IndexVersionProvider.instance().version
        if (versionValue == currentVersion) {
            return result
        }

        delegate.close()
        delegate.delete()

        recreate()
        version.writeWithoutLock { it.set(currentVersion) }
        creationTime.writeWithoutLock { it.set(System.currentTimeMillis()) }

        return Wiped(true)
    }

    private fun recreate(): Wiped {
        delegate = DBWithMetadata(META_DB_KEY, path, factory)
        val result = delegate.init()
        version = delegate.metadataDB.resource { it.atomicLong(VERSION_KEY).createOrOpen() }
        creationTime = delegate.metadataDB.resource { it.atomicLong(CREATED_TIME_KEY).createOrOpen() }
        return result
    }

    override fun complete() {
        delegate.complete()
    }

    override fun isComplete(meta: String): Boolean {
        return delegate.isComplete(meta)
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
    }
}