// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

package tse.unblockt.ls.server.analysys.storage

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.impl.jar.CoreJarFileSystem
import org.apache.logging.log4j.kotlin.logger
import org.jetbrains.kotlin.cli.jvm.modules.CoreJrtFileSystem
import org.mapdb.HTreeMap
import org.mapdb.Serializer
import org.mapdb.serializer.SerializerArrayTuple
import tse.unblockt.ls.isUUID
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
): RouterDB.CompletableRouter {

    companion object {
        const val LIBRARIES_MAP = "libraries"
        const val CATALOGUE_DB = "metadata.db"
    }

    private val allLibrariesRoots: Set<String>? = run {
        val projectRoot = projectPath.toString()
        librariesRoots?.mapNotNull { libraryRoot(it) }?.filter { !it.startsWith(projectRoot) }?.toSet()
    }

    private lateinit var metadataDB: SafeDB

    private val librariesMap: SafeDBResource<HTreeMap<String, Array<Any>>> by lazy {
        metadataDB.resource {
            it.hashMap(LIBRARIES_MAP)
                .keySerializer(Serializer.STRING)
                .valueSerializer(SerializerArrayTuple(Serializer.STRING, Serializer.BOOLEAN))
                .createOrOpen()
        }
    }

    private val databases = ConcurrentHashMap<String, DBWithRoot>()

    override val all: Collection<DB>
        get() = databases.values.map { it.db }

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

    override fun complete() {
        librariesMap.write { map ->
            val keys = map.keys
            for (key in keys) {
                val curValue = map[key]!!
                map[key] = arrayOf(curValue[0], true)
            }
        }
    }

    override fun isCompleted(meta: String): Boolean {
        val root = libraryRoot(meta)
        val arr = librariesMap.read { it[root] } ?: return false
        return arr[1] as Boolean
    }

    @OptIn(ExperimentalPathApi::class)
    override fun delete() {
        Files.list(globalStorage).use { paths ->
            for (path in paths) {
                val fileName = path.fileName.toString()
                if (fileName.isUUID || fileName == CATALOGUE_DB || fileName == LIBRARIES_MAP) {
                    path.deleteRecursively()
                }
            }
        }
    }

    @OptIn(ExperimentalPathApi::class)
    override fun init(): InitializationResult {
        val pathToMetadata = globalStorage.resolve(CATALOGUE_DB)
        val (mdb, result) = MDB.openOrCreateDB(pathToMetadata) {
            MDB.makeMetaDB(pathToMetadata)
        }
        if (mdb == null || !result.success) {
            return InitializationResult(wiped = result.wiped, success = false)
        }
        metadataDB = SharedDBWrapperImpl { mdb }

        if (result.wiped) {
            Files.list(globalStorage).use { files ->
                for (file in files) {
                    if (Files.isDirectory(file) && file.fileName.toString().isUUID) {
                        file.deleteRecursively()
                    }
                }
            }
        }

        var wiped = false
        for (i in 0..5) {
            refreshDatabases()

            var wipedOnCurrentIteration = false
            for ((_, dbMeta) in databases) {
                val initResult = dbMeta.db.init()
                if (initResult.wiped) {
                    librariesMap.write { map ->
                        map.remove(dbMeta.root)
                    }
                    dbMeta.db.delete()
                    dbMeta.db.close()
                }
                wipedOnCurrentIteration = initResult.wiped || wipedOnCurrentIteration
            }
            wiped = wiped || wipedOnCurrentIteration

            if (!wipedOnCurrentIteration) {
                return InitializationResult(wiped, true)
            }
            databases.clear()
        }
        return InitializationResult(wiped, false)
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
            metadataDB.close()
        }.onFailure {
            logger.error(it)
        }
    }

    private fun dbForLibrary(root: String): DB {
        val meta = librariesMap.read { it[root] }?.meta() ?: synchronized(this) {
            val id = librariesMap.read { it[root] }
            if (id != null) {
                id.meta()
            } else {
                val newID = UUID.randomUUID().toString()
                val dbMeta = DBMeta(newID, false)
                librariesMap.write {
                    it[root] = arrayOf(dbMeta.file, dbMeta.completed)
                }
                dbMeta
            }
        }
        refreshDatabases()

        val synchronized = synchronized(this) {
            val result = databases[meta.file] ?: throw IllegalStateException("No db for $root: id=$meta")
            val initResult = result.db.init()
            if (!initResult.success) {
                librariesMap.write { map ->
                    map.remove(root)
                }
                result.db.delete()
                throw IllegalStateException("Failed to initialize DB for $root: id=$meta")
            }
            result
        }
        return synchronized.db
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

    private fun refreshDatabases() {
        librariesMap.read { map ->
            for ((root, arr) in map) {
                if (allLibrariesRoots == null || allLibrariesRoots.contains(root)) {
                    val meta = arr.meta()
                    val storageFile = meta.file
                    databases.computeIfAbsent(storageFile) {
                        DBWithRoot(
                            MDB(project, globalStorage.resolve(storageFile), true, meta.completed),
                            root
                        )
                    }
                }
            }
        }
        val globalDBsCopy = databases.toMap()
        val allDBFiles = librariesMap.read { it.values.mapNotNull { v -> v?.meta()?.file }.toSet() }
        for ((file, _) in globalDBsCopy) {
            if (!allDBFiles.contains(file)) {
                val removed = databases.remove(file)
                kotlin.runCatching {
                    removed?.db?.close()
                }.onFailure {
                    logger.error(it)
                }
            }
        }
    }

    private val String.isUrl: Boolean
        get() = startsWith("file:/") || startsWith("jar:/") || startsWith("jrt:/")

    private fun Array<Any>.meta(): DBMeta {
        return DBMeta(this[0] as String, this[1] as Boolean)
    }

    private data class DBMeta(
        val file: String,
        val completed: Boolean,
    )

    private data class DBWithRoot(
        val db: DB,
        val root: String,
    )
}