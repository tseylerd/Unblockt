@file:OptIn(ExperimentalPathApi::class, ExperimentalPathApi::class)

package tse.unblockt.ls.server.analysys.storage

import com.intellij.openapi.project.Project
import org.eclipse.collections.impl.map.mutable.ConcurrentHashMap
import org.mapdb.DBMaker
import org.mapdb.Serializer
import org.mapdb.serializer.SerializerArrayTuple
import java.nio.file.Path
import java.util.*
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteRecursively
import org.mapdb.DB as MapDB

class MDB(private val project: Project, private val root: Path): DB {
    companion object {
        fun indexesPath(path: Path): Path {
            return path.resolve("indexes").resolve("index")
        }
    }

    private lateinit var db: MapDB
    private val stores = ConcurrentHashMap<String, MapDBStore<*, *, *>>()

    override val isClosed: Boolean
        get() = !::db.isInitialized || db.isClosed()

    override fun init() {
        val indexesPath = indexesPath(root)
        indexesPath.parent.createDirectories()
        db = try {
            makeDB(indexesPath)
        } catch (t: Throwable) {
            indexesPath.parent.deleteRecursively()
            indexesPath.parent.createDirectories()
            makeDB(indexesPath)
        }
    }

    private fun makeDB(indexesPath: Path): MapDB {
        return DBMaker
            .fileDB(indexesPath.toFile())
            .allocateStartSize(50 * 1024 * 1024)
            .allocateIncrement(50 * 1024 * 1024)
            .fileMmapEnable()
            .executorEnable()
            .fileLockDisable()
            .fileSyncDisable()
            .make()
    }

    override fun init(name: String, config: DB.Store.Config) {
    }

    override fun tx(): DB.Tx {
        return MapTx(project, db, stores)
    }

    override fun delete() {
        indexesPath(root).parent.deleteRecursively()
    }

    override fun close() {
        if (!isClosed) {
            db.close()
        }
    }

    private class MapTx(private val project: Project, private val db: MapDB, private val stores: ConcurrentHashMap<String, MapDBStore<*, *, *>>): DB.Tx {
        override fun commit(): Boolean {
            db.commit()
            finished = true
            return true
        }

        private var finished = false

        override val isFinished: Boolean
            get() = finished

        override fun <M: Any, K : Any, V : Any> store(name: String, attribute: DB.Attribute<M, K, V>): DB.Store<M, K, V> {
            @Suppress("UNCHECKED_CAST")
            return stores.computeIfAbsent(name) {
                val byMetaSet: NavigableSet<Array<Any?>> = db.treeSet("${name}_by_meta")
                    .serializer(SerializerArrayTuple(Serializer.STRING, Serializer.STRING, Serializer.STRING, Serializer.STRING))
                    .createOrOpen()
                val hashSet: NavigableSet<Array<Any?>> = db.treeSet(name)
                    .serializer(SerializerArrayTuple(Serializer.STRING, Serializer.STRING, Serializer.STRING))
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

    private class MapDBStore<M: Any, K : Any, V : Any>(
        val project: Project,
        val attribute: DB.Attribute<M, K, V>,
        val byMetaSet: NavigableSet<Array<Any?>>,
        val byKeySet: NavigableSet<Array<Any?>>
    ): DB.Store<M, K, V> {
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

        override fun allKeys(): Sequence<K> {
            return byKeySet.asSequence().mapNotNull { any: Array<Any?> ->
                attribute.stringToKey(project, any.keyFromPair)
            }.distinct()
        }

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
            }
        }

        override fun sequence(): Sequence<Triple<M, K, V>> {
            return byMetaSet.asSequence().mapNotNull { any ->
                val meta = attribute.stringToMeta(any.metaFromTriple)
                val key = attribute.stringToKey(project, any.keyFromTriple) ?: return@mapNotNull null
                val value = attribute.stringToValue(project, any.valueFromTriple) ?: return@mapNotNull null
                Triple(meta, key, value)
            }
        }

        override fun exists(key: K): Boolean {
            val keyAsStr = attribute.keyToString(key)
            return byKeySet.subSet(arrayOf(keyAsStr), arrayOf(keyAsStr, null, null)).isNotEmpty()
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

        override fun sequenceByMeta(meta: M): Sequence<Pair<K, V>> {
            return byMetaSet.asSequence().mapNotNull { any ->
                val key = attribute.stringToKey(project, any.keyFromTriple) ?: return@mapNotNull null
                val value = attribute.stringToValue(project, any.valueFromTriple) ?: return@mapNotNull null
                Pair(key, value)
            }
        }

        override fun values(key: K): Sequence<V> {
            val keyAsStr = attribute.keyToString(key)
            return byKeySet.subSet(arrayOf(keyAsStr), arrayOf(keyAsStr, null, null)).asSequence().mapNotNull { any ->
                attribute.stringToValue(project, any.valueFromPair)
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

        @Suppress("UNCHECKED_CAST")
        private val Any.keyFromPair: String
            get() = (this as Array<Any>)[0] as String
        @Suppress("UNCHECKED_CAST")
        private val Any.valueFromPair: String
            get() = (this as Array<Any>)[1] as String
    }
}