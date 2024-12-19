// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

package tse.unblockt.ls.server.analysys.storage

class VersionedDB(private val factory: () -> DB): FreezeableDB {
    companion object {
        val VERSION_KEY = "VersionedDB.version"
        val CREATED_TIME_KEY = "VersionedDB.createdAt"
    }

    private lateinit var delegate: DB
    internal val db: DB
        get() = delegate

    override val isValid: Boolean
        get() = delegate.isValid
    override val isClosed: Boolean
        get() = delegate.isClosed

    override fun init(): Wiped {
        if (::delegate.isInitialized) {
            return Wiped(false)
        }

        delegate = factory()
        delegate.init()

        val version = delegate.get(VERSION_KEY)?.toLongOrNull()
        val currentVersion = IndexVersionProvider.instance().version
        if (version == currentVersion) {
            return Wiped(false)
        }
        delegate.close()
        delegate.delete()

        delegate = factory()
        delegate.init()
        delegate.put(VERSION_KEY, currentVersion.toString())
        delegate.put(CREATED_TIME_KEY, System.currentTimeMillis().toString())

        return Wiped(true)
    }

    override fun freeze() {
        val curDB = delegate
        if (curDB is FreezeableDB) {
            curDB.freeze()
        }
    }

    override fun isFrozen(meta: String): Boolean {
        val curDB = delegate
        if (curDB is FreezeableDB) {
            return curDB.isFrozen(meta)
        }
        return false
    }

    override fun init(name: String) {
        delegate.init(name)
    }

    override fun delete() {
        delegate.delete()
    }

    override fun put(key: String, value: String) {
        delegate.put(key, value)
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

    override fun get(key: String): String? {
        return delegate.get(key)
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