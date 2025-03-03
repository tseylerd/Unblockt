// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

package tse.unblockt.ls.server.analysys.storage

import java.io.Closeable

class RouterDB(val router: Router): FreezeableDB {
    override val isValid: Boolean
        get() = router.all.all { it.isValid }
    override val isClosed: Boolean
        get() = router.all.all { it.isClosed }

    override fun init(): Wiped {
        return router.init()
    }

    override fun init(name: String) {
        router.all.forEach {
            it.init(name)
        }
    }

    override fun delete() {
        router.delete()
    }

    override fun freeze() {
        if (router is FreezableRouter) {
            router.freeze()
        }
    }

    override fun isFrozen(meta: String): Boolean {
        if (router !is FreezableRouter) {
            return false
        }
        return router.isFrozen(meta)
    }

    override fun close() {
        router.close()
    }

    override fun put(key: String, value: String) {
        router.metadataDB.lock { db ->
            val str = db.atomicString(key).createOrOpen()
            str.set(value)
        }
    }

    override fun get(key: String): String? {
        return router.metadataDB.read { db ->
            val atomicString = db.atomicString(key).createOrOpen()
            atomicString.get()
        }
    }

    override fun <M : Any, K : Any, V : Any> put(
        name: String,
        attribute: DB.Attribute<M, K, V>,
        meta: M,
        key: K,
        value: V
    ) {
        val metaStr = attribute.metaToString(meta)
        val keyStr = attribute.keyToString(key)
        router.dbToPut(attribute, metaStr, keyStr).put(name, attribute, meta, key, value)
    }

    override fun <M : Any, K : Any, V : Any> putAll(
        name: String,
        attribute: DB.Attribute<M, K, V>,
        triples: Set<Triple<M, K, V>>
    ) {
        triples.groupBy { (meta, key, _) ->
            val metaStr = attribute.metaToString(meta)
            val keyStr = attribute.keyToString(key)
            router.dbToPut(attribute, metaStr, keyStr)
        }.forEach { (db, triples) ->
            db.putAll(name, attribute, triples.toSet())
        }
    }

    override fun <M : Any, K : Any, V : Any> allValues(name: String, attribute: DB.Attribute<M, K, V>): Sequence<V> {
        return router.all.asSequence().flatMap { it.allValues(name, attribute) }
    }

    override fun <M : Any, K : Any, V : Any> all(name: String, attribute: DB.Attribute<M, K, V>): Sequence<Pair<K, V>> {
        return router.all.asSequence().flatMap { it.all(name, attribute) }
    }

    override fun <M : Any, K : Any, V : Any> allKeys(name: String, attribute: DB.Attribute<M, K, V>): Sequence<K> {
        return router.all.asSequence().flatMap { it.allKeys(name, attribute) }
    }

    override fun <M : Any, K : Any, V : Any> values(
        name: String,
        attribute: DB.Attribute<M, K, V>,
        key: K
    ): Sequence<V> {
        val keyStr = attribute.keyToString(key)
        return router.dbsByKey(attribute, keyStr).asSequence().flatMap { it.values(name, attribute, key) }
    }

    override fun <M : Any, K : Any, V : Any> metas(name: String, attribute: DB.Attribute<M, K, V>): Sequence<M> {
        return router.all.asSequence().flatMap { it.metas(name, attribute) }
    }

    override fun <M : Any, K : Any, V : Any> mayContain(
        name: String,
        attribute: DB.Attribute<M, K, V>,
        key: K
    ): Boolean {
        return router.all.any { it.mayContain(name, attribute, key) }
    }

    override fun <M : Any, K : Any, V : Any> deleteByMeta(name: String, attribute: DB.Attribute<M, K, V>, meta: M) {
        return router.dbsToDeleteByMeta(attribute.metaToString(meta)).forEach {
            it.deleteByMeta(name, attribute, meta)
        }
    }

    override fun <M : Any, K : Any, V : Any> exists(name: String, attribute: DB.Attribute<M, K, V>, key: K): Boolean {
        return router.dbsByKey(attribute, attribute.keyToString(key)).any { it.exists(name, attribute, key) }
    }

    override fun <M : Any, K : Any, V : Any> sequence(
        name: String,
        attribute: DB.Attribute<M, K, V>
    ): Sequence<Triple<M, K, V>> {
        return router.all.asSequence().flatMap { it.sequence(name, attribute) }
    }

    interface Router: Closeable {
        val metadataDB: SafeDB

        val all: Collection<DB>

        fun init(): Wiped

        fun dbsByMeta(meta: String): Collection<DB>
        fun dbsToDeleteByMeta(meta: String): Collection<DB>
        fun dbsByKey(attribute: DB.Attribute<*, *, *>, key: String): Collection<DB>
        fun dbToPut(attribute: DB.Attribute<*, *, *>, meta: String, key: String): DB

        fun delete()
    }

    interface FreezableRouter: Router {
        fun freeze()
        fun isFrozen(meta: String): Boolean
    }
}