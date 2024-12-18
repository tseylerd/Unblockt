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

        val version = delegate.inTx {
            get(VERSION_KEY)
        }?.toLongOrNull()
        val currentVersion = IndexVersionProvider.instance().version
        if (version == currentVersion) {
            return Wiped(false)
        }
        delegate.close()
        delegate.delete()

        delegate = factory()
        delegate.init()
        delegate.inTx {
            put(VERSION_KEY, currentVersion.toString())
            put(CREATED_TIME_KEY, System.currentTimeMillis().toString())
        }
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

    override fun init(name: String, config: DB.Store.Config) {
        delegate.init(name, config)
    }

    override fun tx(): DB.Tx {
        return delegate.tx()
    }

    override fun delete() {
        delegate.delete()
    }

    override fun close() {
        delegate.close()
    }
}