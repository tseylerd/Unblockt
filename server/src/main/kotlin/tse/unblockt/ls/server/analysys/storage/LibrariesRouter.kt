// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

package tse.unblockt.ls.server.analysys.storage

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.impl.jar.CoreJarFileSystem
import org.apache.logging.log4j.kotlin.logger
import org.jetbrains.kotlin.cli.jvm.modules.CoreJrtFileSystem
import org.mapdb.Atomic
import org.mapdb.HTreeMap
import org.mapdb.Serializer
import org.mapdb.serializer.SerializerArrayTuple
import tse.unblockt.ls.server.fs.LsFileSystem
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively

class LibrariesRouter(
    private val project: Project,
    private val globalStorage: Path,
    private val projectPath: Path,
    private val librariesRoots: Collection<String>?,
): RouterDB.FreezableRouter {

    companion object {
        const val LIBRARIES_MAP = "libraries"
        const val CATALOGUE_DB = "metadata.db"

        private val globalDBsIDsKey = UniversalCache.Key<Map<String, Pair<String, Boolean>>>("globalDBsIDs")
        private val globalDBsKey = UniversalCache.Key<Map<String, DB>>("globalDBsKey")

    }

    private val allLibrariesRoots: Set<String>? = run {
        val toString = projectPath.toString()
        librariesRoots?.mapNotNull { libraryRoot(it) }?.filter { !it.startsWith(toString) }?.toSet()
    }

    private val pathToMetadata = globalStorage.resolve(CATALOGUE_DB)

    private val librariesMap: SafeDBResource<HTreeMap<String, Array<Any>>> by lazy {
        loadLibrariesMap()
    }

    private val cache = UniversalCache {
        state.read { counter ->
            counter.get()
        }
    }

    private val actualGlobalDBsIDs: Map<String, Pair<String, Boolean>>
        get() = cache.getOrCompute(globalDBsIDsKey) {
            listGlobalDBs()
        }

    private val globalMetaDB = SharedDBWrapperImpl {
        MDB.openOrCreateDB(pathToMetadata) {
            MDB.makeMetaDB(pathToMetadata)
        }
    }

    private val state: SafeDBResource<Atomic.Long> = globalMetaDB.resource { db ->
        db.atomicLong("counter").createOrOpen()
    }

    private val globalDBs = ConcurrentHashMap<String, DB>()
    private val actualGlobalDBs: Map<String, DB>
        get() = cache.getOrCompute(globalDBsKey) {
            actualGlobalDBs()
        }

    override val all: Collection<DB>
        get() = actualGlobalDBs.values

    override val metadataDB: SafeDB
        get() = globalMetaDB

    override fun dbsByMeta(meta: String): Collection<DB> {
        val root = libraryRoot(meta) ?: return emptyList()
        return listOf(dbForLibrary(root))
    }

    override fun dbsToDeleteByMeta(meta: String): Collection<DB> {
        return emptyList()
    }

    override fun dbsByKey(attribute: DB.Attribute<*, *, *>, key: String): Collection<DB> {
        return all
    }

    override fun dbToPut(attribute: DB.Attribute<*, *, *>, meta: String, key: String): DB {
        val root = libraryRoot(meta) ?: throw IllegalArgumentException("Cannot find DB for meta: $meta")
        return dbForLibrary(root)
    }

    override fun freeze() {
        librariesMap.writeWithLock { map ->
            val keys = map.keys
            for (key in keys) {
                val curValue = map[key]!!
                map[key] = arrayOf(curValue[0], true)
            }
        }
    }

    override fun isFrozen(meta: String): Boolean {
        val root = libraryRoot(meta)
        val arr = librariesMap.read { it[root] }
        if (arr == null) {
            return false
        }
        return arr[1] as Boolean
    }

    @OptIn(ExperimentalPathApi::class)
    override fun delete() {
        Files.list(globalStorage).use {
            for (path in it) {
                path.deleteRecursively()
            }
        }
    }

    override fun init(): Wiped {
        var result = false
        for (db in all) {
            result = db.init().value || result
        }
        return Wiped(result)
    }

    override fun close() {
        all.forEach {
            kotlin.runCatching {
                it.close()
            }.onFailure {
                logger.error(it)
            }
        }

        kotlin.runCatching {
            globalMetaDB.close()
        }.onFailure {
            logger.error(it)
        }
    }

    private fun dbForLibrary(root: String): DB {
        val id = actualGlobalDBsIDs[root] ?: synchronized(this) {
            val id = actualGlobalDBsIDs[root]
            if (id != null) {
                id
            } else {
                val newID = UUID.randomUUID().toString()
                librariesMap.writeWithLock {
                    it[root] = arrayOf(newID, false)
                }
                state.writeWithoutLock {
                    it.incrementAndGet()
                }
                newID to false
            }
        }
        val result = actualGlobalDBs[id.first] ?: throw IllegalStateException("No db for $root: id=$id")
        return result
    }

    private fun libraryRoot(meta: String): String? {
        if (!meta.isUrl) {
            return meta
        }
        val vFile = LsFileSystem.instance().getVirtualFileByUrl(meta) ?: throw IllegalStateException("VirtualFile not found: $meta")
        val fs = vFile.fileSystem

        if (fs is CoreJarFileSystem || fs is CoreJrtFileSystem)  {
            val root = generateSequence(vFile) { it.parent }.last()
            return root.path
        }
        return null
    }

    private fun actualGlobalDBs(): Map<String, DB> {
        val ids = actualGlobalDBsIDs
        for ((root, id) in ids) {
            if (allLibrariesRoots == null || allLibrariesRoots.contains(root)) {
                dbById(id.first, id.second)
            }
        }
        val globalDBsCopy = globalDBs.toMap()
        for ((id, _) in globalDBsCopy) {
            if (!ids.values.contains(id to false) && !ids.values.contains(id to true)) {
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

    private fun dbById(id: String, freezed: Boolean): DB {
        return globalDBs.computeIfAbsent(id) {
            val result = MDB(project, globalStorage.resolve(id), true, freezed)
            result.init()
            result
        }
    }

    private fun loadLibrariesMap(): SafeDBResource<HTreeMap<String, Array<Any>>> = globalMetaDB.resource { db ->
        db.hashMap(LIBRARIES_MAP)
            .keySerializer(Serializer.STRING)
            .valueSerializer(SerializerArrayTuple(Serializer.STRING, Serializer.BOOLEAN))
            .createOrOpen()
    }

    private fun listGlobalDBs(): Map<String, Pair<String, Boolean>> {
        val result = mutableMapOf<String, Pair<String, Boolean>>()
        librariesMap.read { map ->
            for ((libraryPath, id) in map) {
                result += libraryPath to Pair(id[0] as String, id[1] as Boolean)
            }
        }
        return result
    }

    private val String.isUrl: Boolean
        get() = startsWith("file:/") || startsWith("jar:/") || startsWith("jrt:/")
}