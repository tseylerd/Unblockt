// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

package tse.unblockt.ls.server.analysys.storage

import com.intellij.openapi.project.Project
import org.mapdb.Serializer
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.*
import kotlin.math.abs
import org.mapdb.DB as MapDB

class ShardedDB(private val project: Project, private val root: Path, private val shards: Int): DB, DBContainer {
    private val dbs = arrayOfNulls<DB>(shards)
    override val all: Collection<DB>
        get() = dbs.filterNotNull().toList()

    private lateinit var metadataDB: MapDB

    override val isClosed: Boolean
        get() = dbs.all { it == null || it.isClosed }

    override val isValid: Boolean
        get() = dbs.all { it == null || it.isValid }

    @OptIn(ExperimentalPathApi::class)
    override fun init() {
        val indexesPath = DB.indexesPath(root)
        if (!Files.exists(indexesPath)) {
            indexesPath.createDirectories()
        }
        val metadataPath = indexesPath.resolve("metadata.db")
        metadataDB = try {
            MDB.makeDB(metadataPath)
        } catch (t: Throwable) {
            metadataPath.deleteExisting()
            MDB.makeDB(metadataPath)
        }
        val metadataMap = metadataDB.hashMap("metadata", Serializer.STRING, Serializer.STRING).createOrOpen()
        val shardsCount = metadataMap["shards"]?.toInt()
        if (shardsCount != shards) {
            for (i in 0 until shards) {
                Files.list(DB.indexesPath(root)).use { list ->
                    list.forEach { p ->
                        if (Files.isDirectory(p) || p.fileName.toString() == "index") {
                            p.deleteRecursively()
                        }
                    }
                }
            }
        }
        metadataMap["shards"] = shards.toString()

        for (i in 0 until shards) {
            initBucket(i).init()
        }
    }

    override fun init(name: String, config: DB.Store.Config) {
        for (db in dbs) {
            db?.init(name, config)
        }
    }

    override fun tx(): DB.Tx {
        return ShardedDBTx(this)
    }

    @OptIn(ExperimentalPathApi::class)
    override fun delete() {
        val resolve = DB.indexesPath(root)
        if (resolve.exists()) {
            resolve.deleteRecursively()
        }
    }

    override fun close() {
        if (::metadataDB.isInitialized) {
            metadataDB.close()
        }
        dbs.forEach { it?.close() }
    }

    private fun initBucket(i: Int): DB {
        val bucketDir = DB.indexesPath(root).resolve(i.toString())
        if (!bucketDir.exists()) {
            bucketDir.createDirectories()
        }
        val mdb = MDB(project, bucketDir)
        dbs[i] = mdb
        return mdb
    }

    private class ShardedDBTx(private val dbContainer: DBContainer): DB.Tx {
        private val dbs = mutableMapOf<String, DB>()
        private val transactions = mutableMapOf<DB, DB.Tx>()
        private val stores = mutableMapOf<Pair<DB.Tx, DB.Attribute<*, *, *>>, DB.Store<Any, Any, Any>>()

        override fun commit(): Boolean {
            transactions.values.forEach { it.commit() }
            return true
        }

        override val isFinished: Boolean
            get() = transactions.isEmpty() || transactions.values.all { it.isFinished }

        override fun <M : Any, K : Any, V : Any> store(
            name: String,
            attribute: DB.Attribute<M, K, V>
        ): DB.Store<M, K, V> {
            return ShardedStore(object : StoreContainer<M, K, V> {
                override val all: List<DB.Store<M, K, V>>
                    get() = dbContainer.all.map { db ->
                        transactions.computeIfAbsent(db) {
                            db.tx()
                        }.store(name, attribute)
                    }

                override fun storeBy(key: String): DB.Store<M, K, V> {
                    return storeBy(name, key, attribute)
                }
            }, attribute)
        }

        override fun revert() {
            transactions.values.forEach { it.revert() }
        }

        override fun abort() {
            transactions.values.forEach { it.abort() }
        }

        private fun <M: Any, K: Any, V: Any> storeBy(name: String, key: String, attribute: DB.Attribute<M, K, V>): DB.Store<M, K, V> {
            val db = dbs.computeIfAbsent(key) {
                dbContainer.dbBy(key)
            }
            val tx = transactions.computeIfAbsent(db) {
                it.tx()
            }
            return stores.computeIfAbsent(tx to attribute) {
                tx.store(name, attribute) as DB.Store<Any, Any, Any>
            } as DB.Store<M, K, V>
        }
    }

    private class ShardedStore<M: Any, K: Any, V: Any>(
        private val container: StoreContainer<M, K, V>,
        private val attribute: DB.Attribute<M, K, V>
    ): DB.Store<M, K, V> {
        override fun putAll(triples: Set<Triple<M, K, V>>) {
            triples.groupBy { (_, k, _) ->
                container.storeBy(attribute.keyToString(k))
            }.forEach { (store: DB.Store<M, K, V>, triples: List<Triple<M, K, V>>) ->
                store.putAll(triples.toSet())
            }
        }

        override fun put(meta: M, key: K, value: V) {
            container.storeBy(attribute.keyToString(key)).put(meta, key, value)
        }

        override fun allKeys(): Sequence<K> {
            return container.all.asSequence().flatMap {
                it.allKeys()
            }
        }

        override fun allValues(): Sequence<V> {
            return container.all.asSequence().flatMap {
                it.allValues()
            }
        }

        override fun all(): Sequence<Pair<K, V>> {
            return container.all.asSequence().flatMap {
                it.all()
            }
        }

        override fun sequence(): Sequence<Triple<M, K, V>> {
            return container.all.asSequence().flatMap { it.sequence() }
        }

        override fun exists(key: K): Boolean {
            return container.storeBy(attribute.keyToString(key)).exists(key)
        }

        override fun deleteByMeta(meta: M) {
            container.all.forEach { s ->
                s.deleteByMeta(meta)
            }
        }

        override fun values(key: K): Sequence<V> {
            return container.storeBy(attribute.keyToString(key)).values(key)
        }
    }

    interface StoreContainer<M: Any, K: Any, V: Any> {
        val all: List<DB.Store<M, K, V>>

        fun storeBy(key: String): DB.Store<M, K, V>
    }

    override fun dbBy(key: String): DB {
        val bucket = abs(key.hashCode() % shards)
        return dbs[bucket]!!
    }
}

interface DBContainer {
    val all: Collection<DB>
    fun dbBy(key: String): DB
}
