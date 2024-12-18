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

    override fun init(name: String, config: DB.Store.Config) {
        router.all.forEach {
            it.init(name, config)
        }
    }

    override fun tx(): DB.Tx {
        return RouterTx(
            router
        )
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

    private class RouterTx(private val router: Router): DB.Tx {
        private val txs = mutableMapOf<DB, DB.Tx>()
        private val stores = mutableMapOf<Pair<DB.Tx, DB.Attribute<*, *, *>>, DB.Store<Any, Any, Any>>()

        override val isFinished: Boolean
            get() = txs.values.all { it.isFinished }

        override fun commit(): Boolean {
            txs.values.forEach {
                it.commit()
            }
            return true
        }

        override fun put(key: String, value: String) {
            router.metadataDB.exclusively { db ->
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

        @Suppress("UNCHECKED_CAST")
        override fun <M : Any, K : Any, V : Any> store(
            name: String,
            attribute: DB.Attribute<M, K, V>
        ): DB.Store<M, K, V> {
            return RouterStore(
                router = object : StoreRouter<M, K, V> {
                    override val dbRouter: Router
                        get() = router

                    override val all: Collection<DB.Store<M, K, V>>
                        get() = dbsToStores(router.all)

                    override fun storesByKey(key: String): Collection<DB.Store<M, K, V>> {
                        val dbsByMeta = router.dbsByKey(key)
                        return dbsToStores(dbsByMeta)
                    }

                    override fun storesByMeta(meta: String): Collection<DB.Store<M, K, V>> {
                        val dbsByMeta = router.dbsByMeta(meta)
                        return dbsToStores(dbsByMeta)
                    }

                    override fun storeToPut(attribute: DB.Attribute<M, K, V>, meta: String, key: String): DB.Store<M, K, V> {
                        return dbsToStores(listOf(router.dbToPut(attribute, meta, key))).single()
                    }

                    private fun dbsToStores(dbsByMeta: Collection<DB>) =
                        dbsByMeta.map { db ->
                            val tx = txs.computeIfAbsent(db) {
                                it.tx()
                            }
                            stores.computeIfAbsent(tx to attribute) {
                                tx.store(name, attribute) as DB.Store<Any, Any, Any>
                            } as DB.Store<M, K, V>
                        }
                },
                attribute = attribute,
            )
        }

        override fun revert() {
            txs.values.forEach {
                it.revert()
            }
        }

        override fun abort() {
            txs.values.forEach {
                it.abort()
            }
        }
    }

    private class RouterStore<M: Any, K: Any, V: Any>(
        private val router: StoreRouter<M, K, V>,
        private val attribute: DB.Attribute<M, K, V>,
    ): DB.Store<M, K, V> {
        override fun putAll(triples: Set<Triple<M, K, V>>) {
            val triplesByStores = triples.groupBy { triple ->
                val meta = triple.first
                val key = triple.second
                val metaStr = attribute.metaToString(meta)
                val keyStr = attribute.keyToString(key)
                val stores = router.storeToPut(attribute, metaStr, keyStr)
                stores
            }
            for ((store, tpls) in triplesByStores) {
                store.putAll(tpls.toSet())
            }
        }

        override fun put(meta: M, key: K, value: V) {
            val metaStr = attribute.metaToString(meta)
            val keyStr = attribute.keyToString(key)
            val store = router.storeToPut(attribute, metaStr, keyStr)
            store.put(meta, key, value)
        }

        override fun allKeys(): Sequence<K> {
            return router.all.asSequence().flatMap { it.allKeys() }
        }

        override fun allValues(): Sequence<V> {
            return router.all.asSequence().flatMap { it.allValues() }
        }

        override fun all(): Sequence<Pair<K, V>> {
            return router.all.asSequence().flatMap { it.all() }
        }

        override fun sequence(): Sequence<Triple<M, K, V>> {
            return router.all.asSequence().flatMap { it.sequence() }
        }

        override fun metas(): Sequence<M> {
            return router.all.asSequence().flatMap { it.metas() }
        }

        override fun exists(key: K): Boolean {
            return router.storesByKey(attribute.keyToString(key)).any { it.exists(key) }
        }

        override fun mayContain(key: K): Boolean {
            return router.all.any { it.mayContain(key) }
        }

        override fun deleteByMeta(meta: M) {
            if (!router.dbRouter.supportsDeletionByMeta) {
                return
            }

            val metaStr = attribute.metaToString(meta)
            router.storesByMeta(metaStr).forEach { it.deleteByMeta(meta) }
        }

        override fun values(key: K): Sequence<V> {
            return router.storesByKey(attribute.keyToString(key)).asSequence().flatMap { it.values(key) }
        }
    }

    private interface StoreRouter<M: Any, K: Any, V: Any> {
        val dbRouter: Router
        val all: Collection<DB.Store<M, K, V>>

        fun storesByMeta(meta: String): Collection<DB.Store<M, K, V>>
        fun storeToPut(attribute: DB.Attribute<M, K, V>, meta: String, key: String): DB.Store<M, K, V>
        fun storesByKey(key: String): Collection<DB.Store<M, K, V>>
    }

    interface Router: Closeable {
        val metadataDB: SafeDB

        val supportsDeletionByMeta: Boolean
            get() = true

        val all: Collection<DB>

        fun init(): Wiped

        fun dbsByMeta(meta: String): Collection<DB>
        fun dbsByKey(key: String): Collection<DB>
        fun dbToPut(attribute: DB.Attribute<*, *, *>, meta: String, key: String): DB

        fun delete()
    }

    interface FreezableRouter: Router {
        fun freeze()
        fun isFrozen(meta: String): Boolean
    }
}