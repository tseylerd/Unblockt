@file:OptIn(ExperimentalPathApi::class, ExperimentalPathApi::class)

package tse.unblockt.ls.server.analysys.storage

import com.intellij.openapi.project.Project
import org.eclipse.collections.impl.map.mutable.ConcurrentHashMap
import org.mapdb.DBMaker
import org.mapdb.Serializer
import org.mapdb.serializer.SerializerArrayTuple
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import org.mapdb.DB as MapDB

class MDB(private val project: Project, private val root: Path, private val appendOnly: Boolean): DB {
    companion object {
        private fun indexesPath(path: Path): Path {
            return path.resolve("index")
        }

        private fun makeDB(dbPath: Path): MapDB {
            return DBMaker
                .fileDB(dbPath.toFile())
                .allocateStartSize(1 * 1024 * 1024)
                .allocateIncrement(1 * 1024 * 1024)
                .fileMmapEnable()
                .executorEnable()
                .fileLockDisable()
                .fileSyncDisable()
                .make()
        }

        fun openOrCreateDB(dbPath: Path): MapDB {
            return openOrCreateDB(dbPath) {
                makeDB(dbPath)
            }
        }

        fun makeMetaDB(dbPath: Path): org.mapdb.DB {
            return DBMaker
                .fileDB(dbPath.toFile())
                .allocateStartSize(128 * 1024)
                .allocateIncrement(128 * 1024)
                .fileMmapEnable()
                .fileLockDisable()
                .executorEnable()
                .transactionEnable()
                .make()
        }

        @OptIn(ExperimentalPathApi::class)
        fun openOrCreateDB(dbPath: Path, maker: () -> MapDB): MapDB {
            return try {
                maker()
            } catch (t: Throwable) {
                if (dbPath.exists()) {
                    dbPath.deleteRecursively()
                }
                dbPath.parent.createDirectories()
                maker()
            }
        }
    }

    private lateinit var db: MapDB
    private val stores = ConcurrentHashMap<String, AbstractMapDBStore<*, *, *>>()

    override val isValid: Boolean
        get() = Files.exists(indexesPath(root))

    override val isClosed: Boolean
        get() = !::db.isInitialized || db.isClosed()

    override fun init(): Wiped {
        if (::db.isInitialized) {
            return Wiped(false)
        }
        val indexesPath = indexesPath(root)
        indexesPath.parent.createDirectories()
        db = openOrCreateDB(indexesPath)
        return Wiped(false)
    }

    override fun init(name: String, config: DB.Store.Config) {
    }

    override fun tx(): DB.Tx {
        return MapTx(project, db, stores, appendOnly)
    }

    override fun delete() {
        indexesPath(root).parent.deleteRecursively()
    }

    override fun close() {
        if (!isClosed) {
            db.close()
        }
    }

    private class MapTx(
        private val project: Project,
        private val db: MapDB,
        private val stores: ConcurrentHashMap<String, AbstractMapDBStore<*, *, *>>,
        private val appendOnly: Boolean,
    ): DB.Tx {
        override fun commit(): Boolean {
            finished = true
            return true
        }

        private var finished = false

        override val isFinished: Boolean
            get() = finished

        override fun put(key: String, value: String) {
            val atomicString = db.atomicString(key).createOrOpen()
            atomicString.set(value)
        }

        override fun get(key: String): String? {
            val atomicString = db.atomicString(key).createOrOpen()
            return atomicString.get()
        }

        override fun <M: Any, K : Any, V : Any> store(name: String, attribute: DB.Attribute<M, K, V>): DB.Store<M, K, V> {
            @Suppress("UNCHECKED_CAST")
            return stores.computeIfAbsent(name) {
                val hashSet: NavigableSet<Array<Any?>> = db.treeSet(name)
                    .serializer(SerializerArrayTuple(Serializer.STRING, Serializer.STRING, Serializer.STRING))
                    .createOrOpen()
                if (appendOnly) {
                    val allKeys = db.hashSet("${name}_all_keys")
                        .serializer(Serializer.STRING)
                        .createOrOpen()
                    return@computeIfAbsent MapDBAppendOnlyStore(project, hashSet, allKeys, attribute)
                }
                val byMetaSet: NavigableSet<Array<Any?>> = db.treeSet("${name}_by_meta")
                    .serializer(SerializerArrayTuple(Serializer.STRING, Serializer.STRING, Serializer.STRING, Serializer.STRING))
                    .createOrOpen()
                MapDBStore(project, attribute, byMetaSet, hashSet)
            } as DB.Store<M, K, V>
        }

        override fun revert() {
        }

        override fun abort() {
            finished = true
        }
    }

    private abstract class AbstractMapDBStore<M: Any, K : Any, V : Any>(
        protected val project: Project,
        protected val byKeySet: NavigableSet<Array<Any?>>,
        protected val attribute: DB.Attribute<M, K, V>,
    ): DB.Store<M, K, V> {

        override fun allValues(): Sequence<V> {
            return byKeySet.asSequence().mapNotNull { any ->
                attribute.stringToValue(project, any.valueFromPair)
            }.distinct()
        }

        override fun all(): Sequence<Pair<K, V>> {
            return byKeySet.asSequence().mapNotNull { any ->
                val key = attribute.stringToKey(project, any.keyFromPair) ?: return@mapNotNull null
                val value = attribute.stringToValue(project, any.valueFromPair) ?: return@mapNotNull null
                Pair(key, value)
            }.distinct()
        }

        override fun values(key: K): Sequence<V> {
            if (!mayContain(key)) {
                return emptySequence()
            }

            val keyAsStr = attribute.keyToString(key)
            return byKeySet.subSet(arrayOf(keyAsStr), arrayOf(keyAsStr, null, null)).asSequence().mapNotNull { any ->
                attribute.stringToValue(project, any.valueFromPair)
            }
        }
    }

    private class MapDBAppendOnlyStore<M: Any, K : Any, V : Any>(
        project: Project,
        byKeySet: NavigableSet<Array<Any?>>,
        private val allKeys: MutableSet<String>,
        attribute: DB.Attribute<M, K, V>,
    ): AbstractMapDBStore<M, K, V>(project, byKeySet, attribute) {
        override fun exists(key: K): Boolean {
            return allKeys.contains(attribute.keyToString(key))
        }

        override fun mayContain(key: K): Boolean {
            return exists(key)
        }

        override fun allKeys(): Sequence<K> {
            return allKeys.asSequence().mapNotNull { keyStr: String ->
                attribute.stringToKey(project, keyStr)
            }.distinct()
        }

        override fun putAll(triples: Set<Triple<M, K, V>>) {
            val all = triples.map { (m, k, v) ->
                val keyStr = attribute.keyToString(k)
                val valueStr = attribute.valueToString(v)
                val metaStr = attribute.metaToString(m)
                arrayOf<Any?>(keyStr, valueStr, metaStr)
            }
            byKeySet.addAll(all)
            allKeys.addAll(all.map { it[0] as String })
        }

        override fun put(meta: M, key: K, value: V) {
            val metaStr = attribute.keyToString(key)
            val keyStr = attribute.keyToString(key)
            val valueStr = attribute.valueToString(value)
            byKeySet.add(arrayOf(keyStr, valueStr, metaStr))
            allKeys.add(keyStr)
        }

        override fun sequence(): Sequence<Triple<M, K, V>> {
            return byKeySet.asSequence().mapNotNull { arr ->
                val keyStr = arr.keyFromPair
                val valueStr = arr.valueFromPair
                val metaStr = arr[2] as String
                val meta = attribute.stringToMeta(metaStr)
                val key = attribute.stringToKey(project, keyStr) ?: return@mapNotNull null
                val value = attribute.stringToValue(project, valueStr) ?: return@mapNotNull null
                Triple(meta, key, value)
            }
        }

        override fun metas(): Sequence<M> {
            return byKeySet.asSequence().mapNotNull { arr ->
                attribute.stringToMeta(arr[2] as String)
            }
        }

        override fun deleteByMeta(meta: M) {
        }
    }

    private class MapDBStore<M: Any, K : Any, V : Any>(
        project: Project,
        attribute: DB.Attribute<M, K, V>,
        val byMetaSet: NavigableSet<Array<Any?>>,
        byKeySet: NavigableSet<Array<Any?>>,
    ): AbstractMapDBStore<M, K, V>(project, byKeySet, attribute) {
        override fun exists(key: K): Boolean {
            val keyAsStr = attribute.keyToString(key)
            return byKeySet.subSet(arrayOf(keyAsStr), arrayOf(keyAsStr, null, null)).isNotEmpty()
        }

        override fun mayContain(key: K): Boolean {
            return true
        }

        override fun allKeys(): Sequence<K> {
            return byKeySet.asSequence().mapNotNull { any: Array<Any?> ->
                attribute.stringToKey(project, any.keyFromPair)
            }.distinct()
        }

        override fun putAll(triples: Set<Triple<M, K, V>>) {
            for (triple in triples) {
                put(triple.first, triple.second, triple.third)
            }
        }

        override fun put(meta: M, key: K, value: V) {
            val metaAsStr = attribute.metaToString(meta)
            val keyAsStr = attribute.keyToString(key)
            val valueAsStr = attribute.valueToString(value)
            val id = upsert(metaAsStr, keyAsStr, valueAsStr)
            byKeySet.add(arrayOf(keyAsStr, valueAsStr, id))
        }

        private fun upsert(metaAsStr: String, keyAsStr: String, valueAsStr: String): String {
            val existing = byMetaSet.subSet(
                arrayOf(metaAsStr, keyAsStr, valueAsStr),
                arrayOf(metaAsStr, keyAsStr, valueAsStr, null),
            ).singleOrNull()
            if (existing != null) {
                return existing.idFromTriple
            }
            val id = UUID.randomUUID().toString()
            byMetaSet.add(arrayOf(metaAsStr, keyAsStr, valueAsStr, id))
            return id
        }

        override fun sequence(): Sequence<Triple<M, K, V>> {
            return byMetaSet.asSequence().mapNotNull { any ->
                val meta = attribute.stringToMeta(any.metaFromTriple)
                val key = attribute.stringToKey(project, any.keyFromTriple) ?: return@mapNotNull null
                val value = attribute.stringToValue(project, any.valueFromTriple) ?: return@mapNotNull null
                Triple(meta, key, value)
            }
        }

        override fun metas(): Sequence<M> {
            return byMetaSet.asSequence().mapNotNull { any ->
                attribute.stringToMeta(any.metaFromTriple)
            }
        }

        override fun deleteByMeta(meta: M) {
            val metaAsStr = attribute.metaToString(meta)
            val subSet = byMetaSet.subSet(arrayOf(metaAsStr), arrayOf(metaAsStr, null, null, null))
            val values = subSet.map { arr ->
                Triple(arr.keyFromTriple, arr.valueFromTriple, arr.idFromTriple)
            }
            subSet.clear()
            for ((key, value, id) in values) {
                byKeySet.subSet(arrayOf(key, value, id), true, arrayOf(key, value, id), true).clear()
            }
        }

        @Suppress("UNCHECKED_CAST")
        private val Any.metaFromTriple: String
            get() = (this as Array<Any>)[0] as String
        @Suppress("UNCHECKED_CAST")
        private val Any.keyFromTriple: String
            get() = (this as Array<Any>)[1] as String
        @Suppress("UNCHECKED_CAST")
        private val Any.valueFromTriple: String
            get() = (this as Array<Any>)[2] as String
        @Suppress("UNCHECKED_CAST")
        private val Any.idFromTriple: String
            get() = (this as Array<Any>)[3] as String

    }
}

@Suppress("UNCHECKED_CAST")
private val Any.keyFromPair: String
    get() = (this as Array<Any>)[0] as String
@Suppress("UNCHECKED_CAST")
private val Any.valueFromPair: String
    get() = (this as Array<Any>)[1] as String
