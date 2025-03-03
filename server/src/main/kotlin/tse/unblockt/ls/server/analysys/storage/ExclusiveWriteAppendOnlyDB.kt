// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

package tse.unblockt.ls.server.analysys.storage

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.mapdb.Atomic
import tse.unblockt.ls.server.ourLaunchId
import java.nio.file.Path

class ExclusiveWriteAppendOnlyDB(path: Path, private val factory: () -> DB): CompletableDB {
    companion object {
        private const val META_DB_KEY = "lock"
        private const val OWNER_KEY = "ExclusiveAccessDB.owner"
        private const val PULSE_KEY = "ExclusiveAccessDB.pulse"
        private const val FREE_MARKER = "lock.is.free"
        private const val MAX_PULSE_DELAY = 3000
        private const val DELAY = 1000L
    }

    private var completed: Boolean = false

    private val metadata = Metadata(path, META_DB_KEY)

    private lateinit var delegate: DB
    private lateinit var lock: SafeDBResource<Atomic.String>
    private lateinit var pulse: SafeDBResource<Atomic.Long>

    override fun complete() {
        assertIsOwned()

        (delegate as? CompletableDB)?.complete()
        completed = true
    }

    override fun isComplete(meta: String): Boolean {
        return (delegate as? CompletableDB)?.isComplete(meta) ?: false
    }

    override val isValid: Boolean
        get() = delegate.isValid
    override val isClosed: Boolean
        get() = delegate.isClosed

    override fun init(): InitializationResult {
        delegate = factory()
        val result = delegate.init()

        if (result.success) {
            return result
        }

        return result
    }

    suspend fun exclusively(what: suspend () -> Unit) {
        if (completed) {
            what()
            return
        }
        if (!::lock.isInitialized) {
            val result = metadata.init()
            if (!result.success) {
                throw IllegalStateException("Failed to initialize metadata db for $META_DB_KEY")
            }
            lock = metadata.db.resource { it.atomicString(OWNER_KEY).createOrOpen() }
            pulse = metadata.db.resource { it.atomicLong(PULSE_KEY).createOrOpen() }
        }

        coroutineScope {
            while (true) {
                val currentValue = lock.read { it.get() }
                val acquired = when {
                    currentValue == FREE_MARKER || System.currentTimeMillis() - pulse.read { it.get() } > MAX_PULSE_DELAY -> lock.writeWithoutLock { it.compareAndSet(currentValue, ourLaunchId) }
                    else -> false
                }

                if (acquired) {
                    break
                }

                delay(DELAY)
            }
            launch {
                pulse()
            }

            try {
                what.invoke()
            } finally {
                lock.writeWithoutLock { it.set(FREE_MARKER) }
            }
        }
    }

    override fun init(name: String) {
        delegate.init(name)
    }

    override fun delete() {
        assertIsOwned()

        delegate.delete()
    }

    override fun <M : Any, K : Any, V : Any> put(
        name: String,
        attribute: DB.Attribute<M, K, V>,
        meta: M,
        key: K,
        value: V
    ) {
        assertIsOwned()

        delegate.put(name, attribute, meta, key, value)
    }

    override fun <M : Any, K : Any, V : Any> putAll(
        name: String,
        attribute: DB.Attribute<M, K, V>,
        triples: Set<Triple<M, K, V>>
    ) {
        assertIsOwned()

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
        assertIsOwned()

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

    private suspend fun pulse() {
        while (lock.read { it.get() } == ourLaunchId) {
            pulse.writeWithoutLock { it.set(System.currentTimeMillis()) }
            delay(DELAY)
        }
    }

    private fun assertIsOwned() {
        val currentValue = lock.read { it.get() }
        if (currentValue != ourLaunchId) {
            throw IllegalStateException("Client must acquire exclusive lock to work with DB")
        }
    }
}