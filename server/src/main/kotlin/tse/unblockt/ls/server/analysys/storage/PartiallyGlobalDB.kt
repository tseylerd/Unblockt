// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

package tse.unblockt.ls.server.analysys.storage

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.impl.jar.CoreJarFileSystem
import org.apache.logging.log4j.kotlin.logger
import org.jetbrains.kotlin.cli.jvm.modules.CoreJrtFileSystem
import org.mapdb.Atomic
import org.mapdb.DBMaker
import org.mapdb.HTreeMap
import org.mapdb.Serializer
import tse.unblockt.ls.server.fs.LsFileSystem
import tse.unblockt.ls.server.fs.cutProtocol
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively

class PartiallyGlobalDB(
    private val project: Project,
    workspaceStorage: Path,
    private val globalStorage: Path,
    projectRoot: Path,
): DB, PartiallyGlobalDBContainer {
    companion object {
        private val globalDBsKey = UniversalCache.Key<Map<String, String>>("globalDBs")

        fun makeMetaDB(dbPath: Path): org.mapdb.DB {
            return DBMaker
                .fileDB(dbPath.toFile())
                .allocateStartSize(128 * 1024)
                .allocateIncrement(128 * 1024)
                .fileMmapEnable()
                .fileLockWait(10000)
                .make()
        }
    }

    private val projectPathString = projectRoot.toString()
    private val pathToMetadata = globalStorage.resolve("metadata.db")

    private val actualGlobalDBsIdsCache = UniversalCache {
        state.get()
    }


    private val actualGlobalDBsIDs: Map<String, String>
        get() = actualGlobalDBsIdsCache.getOrCompute(globalDBsKey) {
            listGlobalDBs()
        }

    private val globalDBs = ConcurrentHashMap<String, DB>()
    override val localDB = ShardedDB(project, workspaceStorage, 10, false)
    override val all: Collection<DB>
        get() = listOf(localDB) + actualGlobalDBs().values
    private val globalMetaDB = MDB.makeOrCreateDB(pathToMetadata) {
        makeMetaDB(pathToMetadata)
    }
    private val state: Atomic.Long = globalMetaDB.atomicLong("counter").createOrOpen()

    override val isValid: Boolean
        get() = this.all.all { it.isValid }
    override val isClosed: Boolean
        get() = this.all.all { it.isClosed }

    override fun init() {
        this.all.forEach {
            it.init()
        }
    }

    override fun init(name: String, config: DB.Store.Config) {
        this.all.forEach {
            it.init(name, config)
        }
    }

    override fun tx(): DB.Tx {
        return PartiallyGlobalTX(
            this
        )
    }

    @OptIn(ExperimentalPathApi::class)
    override fun delete() {
        Files.list(globalStorage).use {
            for (path in it) {
                path.deleteRecursively()
            }
        }
        localDB.delete()
    }

    override fun close() {
        this.all.forEach {
            it.close()
        }
        kotlin.runCatching {
            globalMetaDB.close()
        }.onFailure {
            logger.error(it)
        }
    }

    private class PartiallyGlobalTX(private val dbContainer: PartiallyGlobalDBContainer): DB.Tx {
        private val txs = mutableMapOf<DB, DB.Tx>()
        private val stores = mutableMapOf<Pair<DB.Tx, DB.Attribute<*, *, *>>, DB.Store<Any, Any, Any>>()
        override fun commit(): Boolean {
            txs.values.forEach {
                it.commit()
            }
            return true
        }

        override val isFinished: Boolean
            get() = txs.values.all { it.isFinished }

        override fun <M : Any, K : Any, V : Any> store(
            name: String,
            attribute: DB.Attribute<M, K, V>
        ): DB.Store<M, K, V> {
            return PartiallyGlobalStore(object : StoreContainer<M, K, V> {
                override val all: Collection<DB.Store<M, K, V>>
                    get() = (if (attribute.forceLocal) listOf(dbContainer.localDB) else dbContainer.all).map { db ->
                        val tx = txs.computeIfAbsent(db) {
                            it.tx()
                        }
                        stores.computeIfAbsent(tx to attribute) {
                            tx.store(name, attribute) as DB.Store<Any, Any, Any>
                        } as DB.Store<M, K, V>
                    }

                override fun storeByMeta(meta: String): DB.Store<M, K, V> {
                    val tx = txs.computeIfAbsent(if (attribute.forceLocal) dbContainer.localDB else dbContainer.dbByMeta(meta)) {
                        it.tx()
                    }
                    return stores.computeIfAbsent(tx to attribute) {
                        tx.store(name, attribute) as DB.Store<Any, Any, Any>
                    } as DB.Store<M, K, V>
                }

            }, attribute)
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

    override fun dbByMeta(meta: String): DB {
        return when {
            meta.isLibraryUrl -> {
                val withoutProtocol = meta.cutProtocol
                when {
                    withoutProtocol.startsWith(projectPathString) -> localDB
                    else -> dbForLibrary(libraryRoot(meta))
                }
            }
            else -> localDB
        }
    }

    private fun actualGlobalDBs(): Map<String, DB> {
        val ids = actualGlobalDBsIDs
        for ((_, id) in ids) {
            dbById(id)
        }
        val globalDBsCopy = globalDBs.toMap()
        for ((id, _) in globalDBsCopy) {
            if (!ids.values.contains(id)) {
                val removed = globalDBs.remove(id)
                kotlin.runCatching {
                    removed?.close()
                }.onFailure {
                    logger.error(it)
                }
            }
        }
        return globalDBs
    }

    private fun dbById(id: String): DB {
        return globalDBs.computeIfAbsent(id) {
            val result = ShardedDB(project, globalStorage.resolve(id), 1, true)
            result.init()
            result
        }
    }

    private fun listGlobalDBs(): Map<String, String> {
        val librariesMap = loadLibrariesMap()
        val result = mutableMapOf<String, String>()
        for ((libraryPath, id) in librariesMap) {
            result += libraryPath to id
        }
        return result
    }

    private fun loadLibrariesMap() = globalMetaDB
        .hashMap("libraries")
        .keySerializer(Serializer.STRING)
        .valueSerializer(Serializer.STRING)
        .createOrOpen()

    private fun dbForLibrary(root: Path): DB {
        val toString = root.toString()
        val id = actualGlobalDBsIDs[toString] ?: synchronized(this) {
            val id = actualGlobalDBsIDs[toString]
            if (id != null) {
                id
            } else {
                val newID = UUID.randomUUID().toString()
                val map: HTreeMap<String, String> = loadLibrariesMap()
                map[toString] = newID
                state.incrementAndGet()
                newID
            }
        }
        val result = actualGlobalDBs()[id]
        return result!!
    }

    private fun libraryRoot(meta: String): Path {
        val vFile = LsFileSystem.instance().getVirtualFileByUrl(meta) ?: throw IllegalStateException("VirtualFile not found: $meta")
        val fs = vFile.fileSystem

        if (fs is CoreJarFileSystem || fs is CoreJrtFileSystem)  {
            val root = generateSequence(vFile) { it.parent }.last()
            return Paths.get(root.path)
        }
        throw IllegalStateException("Unknown fs ${fs::class.simpleName} for $meta")
    }

    private val String.isLibraryUrl: Boolean
        get() = startsWith("jrt://") || startsWith("jar://")

    class PartiallyGlobalStore<M : Any, K : Any, V : Any>(private val storeContainer: StoreContainer<M, K, V>, private val attribute: DB.Attribute<M, K, V>) : DB.Store<M, K, V> {
        override fun putAll(triples: Set<Triple<M, K, V>>) {
            for (triple in triples) {
                put(triple.first, triple.second, triple.third)
            }
        }

        override fun put(meta: M, key: K, value: V) {
            val metaStr = attribute.metaToString(meta)
            val store = storeContainer.storeByMeta(metaStr)
            store.put(meta, key, value)
        }

        override fun allKeys(): Sequence<K> {
            return storeContainer.all.asSequence().flatMap { it.allKeys() }
        }

        override fun allValues(): Sequence<V> {
            return storeContainer.all.asSequence().flatMap { it.allValues() }
        }

        override fun all(): Sequence<Pair<K, V>> {
            return storeContainer.all.asSequence().flatMap { it.all() }
        }

        override fun sequence(): Sequence<Triple<M, K, V>> {
            return storeContainer.all.asSequence().flatMap { it.sequence() }
        }

        override fun exists(key: K): Boolean {
            return storeContainer.all.any { it.exists(key) }
        }

        override fun mayContain(key: K): Boolean {
            return storeContainer.all.any { it.mayContain(key) }
        }

        override fun deleteByMeta(meta: M) {
            val metaStr = attribute.metaToString(meta)
            storeContainer.storeByMeta(metaStr).deleteByMeta(meta)
        }

        override fun values(key: K): Sequence<V> {
            return storeContainer.all.filter { it.mayContain(key) }.asSequence().flatMap { it.values(key) }
        }
    }


    interface StoreContainer<M : Any, K : Any, V : Any> {
        val all: Collection<DB.Store<M, K, V>>
        fun storeByMeta(meta: String): DB.Store<M, K, V>
    }
}

private interface PartiallyGlobalDBContainer {
    val all: Collection<DB>
    val localDB: DB

    fun dbByMeta(meta: String): DB
}