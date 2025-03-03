@file:OptIn(ExperimentalPathApi::class, ExperimentalPathApi::class)

package tse.unblockt.ls.server.analysys.storage

import com.intellij.openapi.project.Project
import org.apache.logging.log4j.kotlin.logger
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

class MDB(private val project: Project, private val root: Path, private val appendOnly: Boolean, private val readOnly: Boolean): DB {
    companion object {
        private fun indexesPath(path: Path): Path {
            return path.resolve("index")
        }

        private fun makeDB(dbPath: Path, transactions: Boolean = false, readOnly: Boolean = false): MapDB {
            val maker = DBMaker
                .fileDB(dbPath.toFile())
                .allocateStartSize(1 * 1024 * 1024)
                .allocateIncrement(1 * 1024 * 1024)
                .fileMmapEnable()
                .executorEnable()
                .fileLockDisable()

            return when {
                readOnly -> maker.readOnly().make()
                transactions -> maker.transactionEnable().make()
                else -> maker.fileSyncDisable().make() 
            }
        }

        fun openOrCreateDB(dbPath: Path, transaction: Boolean = false, readOnly: Boolean = false): Pair<MapDB, Wiped> {
            return openOrCreateDB(dbPath) {
                makeDB(dbPath, transaction, readOnly)
            }
        }

        fun makeMetaDB(dbPath: Path): MapDB {
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
        fun openOrCreateDB(dbPath: Path, maker: () -> MapDB): Pair<MapDB, Wiped> {
            return try {
                maker() to Wiped(false)
            } catch (t: Throwable) {
                logger.warn("Wiping mdb: path=$dbPath, message=${t.message}")

                if (dbPath.exists()) {
                    dbPath.deleteRecursively()
                }
                val parent = dbPath.parent
                if (!parent.exists()) {
                    parent.createDirectories()
                }

                maker() to Wiped(true)
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
        val (db, wiped) = openOrCreateDB(indexesPath, appendOnly, readOnly)
        this.db = db
        return wiped
    }

    override fun init(name: String) {
    }

    override fun put(key: String, value: String) {
        if (readOnly) {
            throw IllegalStateException("DB is read only")
        }
        val atomicString = db.atomicString(key).createOrOpen()
        atomicString.set(value)
    }

    override fun get(key: String): String? {
        if (!db.exists(key)) {
            return null
        }
        val atomicString = when {
            readOnly -> db.atomicString(key).open()
            else -> db.atomicString(key).createOrOpen()
        }
        return atomicString.get()
    }

    override fun <M : Any, K : Any, V : Any> put(
        name: String,
        attribute: DB.Attribute<M, K, V>,
        meta: M,
        key: K,
        value: V
    ) {
        store(name, attribute).put(meta, key, value)
    }

    override fun <M : Any, K : Any, V : Any> putAll(
        name: String,
        attribute: DB.Attribute<M, K, V>,
        triples: Set<Triple<M, K, V>>
    ) {
        store(name, attribute).putAll(triples)
    }

    override fun <M : Any, K : Any, V : Any> allValues(name: String, attribute: DB.Attribute<M, K, V>): Sequence<V> {
        return store(name, attribute).allValues()
    }

    override fun <M : Any, K : Any, V : Any> all(name: String, attribute: DB.Attribute<M, K, V>): Sequence<Pair<K, V>> {
        return store(name, attribute).all()
    }

    override fun <M : Any, K : Any, V : Any> allKeys(name: String, attribute: DB.Attribute<M, K, V>): Sequence<K> {
        return store(name, attribute).allKeys()
    }

    override fun <M : Any, K : Any, V : Any> metas(name: String, attribute: DB.Attribute<M, K, V>): Sequence<M> {
        return store(name, attribute).metas()
    }

    override fun <M : Any, K : Any, V : Any> mayContain(
        name: String,
        attribute: DB.Attribute<M, K, V>,
        key: K
    ): Boolean {
        return store(name, attribute).mayContain(key)
    }

    override fun <M : Any, K : Any, V : Any> exists(name: String, attribute: DB.Attribute<M, K, V>, key: K): Boolean {
        return store(name, attribute).exists(key)
    }

    override fun <M : Any, K : Any, V : Any> sequence(
        name: String,
        attribute: DB.Attribute<M, K, V>
    ): Sequence<Triple<M, K, V>> {
        return store(name, attribute).sequence()
    }

    override fun <M : Any, K : Any, V : Any> values(
        name: String,
        attribute: DB.Attribute<M, K, V>,
        key: K
    ): Sequence<V> {
        return store(name, attribute).values(key)
    }

    override fun <M : Any, K : Any, V : Any> deleteByMeta(name: String, attribute: DB.Attribute<M, K, V>, meta: M) {
        return store(name, attribute).deleteByMeta(meta)
    }

    override fun delete() {
        indexesPath(root).parent.deleteRecursively()
    }

    override fun close() {
        if (!isClosed) {
            db.close()
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <M: Any, K : Any, V : Any> store(name: String, attribute: DB.Attribute<M, K, V>): AbstractMapDBStore<M, K, V> {
        return stores.computeIfAbsent(name) {
            val treeSet = when {
                readOnly && !db.exists(name) -> sortedSetOf()
                else -> {
                    val treeSetBuilder = db.treeSet(name).serializer(SerializerArrayTuple(Serializer.STRING, Serializer.STRING, Serializer.STRING))
                    when {
                        readOnly -> treeSetBuilder.open()
                        else -> treeSetBuilder.createOrOpen()
                    }
                }
            }
            if (appendOnly) {
                val keysSetName = "${name}_all_keys"
                val allKeys = when {
                    readOnly && !db.exists(keysSetName) -> sortedSetOf()
                    else -> {
                        val keysSetBuilder = db.hashSet(keysSetName).serializer(Serializer.STRING)
                        when {
                            readOnly -> keysSetBuilder.open()
                            else -> keysSetBuilder.createOrOpen()        
                        }
                    }
                }
                return@computeIfAbsent MapDBAppendOnlyStore(project, treeSet, allKeys, attribute, db, readOnly)
            }
            val metaSetName = "${name}_by_meta"
            val byMetaSet: NavigableSet<Array<Any?>> = when {
                readOnly && !db.exists(metaSetName) -> sortedSetOf()
                else -> {
                    val metaSetBuilder = db.treeSet(metaSetName).serializer(SerializerArrayTuple(Serializer.STRING, Serializer.STRING, Serializer.STRING, Serializer.STRING))
                    when {
                        readOnly -> metaSetBuilder.open()
                        else -> metaSetBuilder.createOrOpen()        
                    }
                }
            }

            MapDBStore(project, attribute, byMetaSet, treeSet, readOnly)
        } as AbstractMapDBStore<M, K, V>
    }

    private abstract class AbstractMapDBStore<M: Any, K : Any, V : Any>(
        protected val project: Project,
        protected val byKeySet: NavigableSet<Array<Any?>>,
        protected val attribute: DB.Attribute<M, K, V>,
        protected val readOnly: Boolean,
    ) {

        abstract fun mayContain(key: K): Boolean
        abstract fun putAll(triples: Set<Triple<M, K, V>>)
        abstract fun put(meta: M, key: K, value: V)
        abstract fun allKeys(): Sequence<K>

        abstract fun sequence(): Sequence<Triple<M, K, V>>
        abstract fun metas(): Sequence<M>
        abstract fun deleteByMeta(meta: M)
        abstract fun exists(key: K): Boolean

        fun allValues(): Sequence<V> {
            return byKeySet.asSequence().mapNotNull { any ->
                attribute.stringToValue(project, any.valueFromPair)
            }.distinct()
        }

        fun all(): Sequence<Pair<K, V>> {
            return byKeySet.asSequence().mapNotNull { any ->
                val key = attribute.stringToKey(project, any.keyFromPair) ?: return@mapNotNull null
                val value = attribute.stringToValue(project, any.valueFromPair) ?: return@mapNotNull null
                Pair(key, value)
            }.distinct()
        }

        fun values(key: K): Sequence<V> {
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
        private val db: MapDB,
        readOnly: Boolean,
    ): AbstractMapDBStore<M, K, V>(project, byKeySet, attribute, readOnly) {
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
            if (readOnly) {
                throw IllegalStateException("DB is read-only")
            }
            val all = triples.map { (m, k, v) ->
                val keyStr = attribute.keyToString(k)
                val valueStr = attribute.valueToString(v)
                val metaStr = attribute.metaToString(m)
                arrayOf<Any?>(keyStr, valueStr, metaStr)
            }
            byKeySet.addAll(all)
            allKeys.addAll(all.map { it[0] as String })
            db.commit()
        }

        override fun put(meta: M, key: K, value: V) {
            if (readOnly) {
                throw IllegalStateException("DB is read-only")
            }
            val metaStr = attribute.keyToString(key)
            val keyStr = attribute.keyToString(key)
            val valueStr = attribute.valueToString(value)
            byKeySet.add(arrayOf(keyStr, valueStr, metaStr))
            allKeys.add(keyStr)
            db.commit()
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
        readOnly: Boolean
    ): AbstractMapDBStore<M, K, V>(project, byKeySet, attribute, readOnly) {
        override fun mayContain(key: K): Boolean {
            return true
        }

        override fun exists(key: K): Boolean {
            val keyAsStr = attribute.keyToString(key)
            return byKeySet.subSet(arrayOf(keyAsStr), arrayOf(keyAsStr, null, null)).isNotEmpty()
        }

        override fun allKeys(): Sequence<K> {
            return byKeySet.asSequence().mapNotNull { any: Array<Any?> ->
                attribute.stringToKey(project, any.keyFromPair)
            }.distinct()
        }

        override fun putAll(triples: Set<Triple<M, K, V>>) {
            if (readOnly) {
                throw IllegalStateException("DB is read-only")
            }
            for (triple in triples) {
                put(triple.first, triple.second, triple.third)
            }
        }

        override fun put(meta: M, key: K, value: V) {
            if (readOnly) {
                throw IllegalStateException("DB is read-only")
            }
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
