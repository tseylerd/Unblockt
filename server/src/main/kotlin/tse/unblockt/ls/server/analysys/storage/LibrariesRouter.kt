// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

package tse.unblockt.ls.server.analysys.storage

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.impl.jar.CoreJarFileSystem
import org.apache.logging.log4j.kotlin.logger
import org.jetbrains.kotlin.cli.jvm.modules.CoreJrtFileSystem
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
import org.mapdb.DB as MapDB

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

    private val metadataDB: MapDB = run {
        val pathToMetadata = globalStorage.resolve(CATALOGUE_DB)
        MDB.openOrCreateDB(pathToMetadata) {
            MDB.makeMetaDB(pathToMetadata)
        }.first
    }

    private val librariesMap: HTreeMap<String, Array<Any>> by lazy {
        metadataDB.hashMap(LIBRARIES_MAP)
            .keySerializer(Serializer.STRING)
            .valueSerializer(SerializerArrayTuple(Serializer.STRING, Serializer.BOOLEAN))
            .createOrOpen()
    }

    private val databases = ConcurrentHashMap<String, DB>()

    override val all: Collection<DB>
        get() = databases.values

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
        val keys = librariesMap.keys
        for (key in keys) {
            val curValue = librariesMap[key]!!
            librariesMap[key] = arrayOf(curValue[0], true)
        }
    }

    override fun isCompleted(meta: String): Boolean {
        val root = libraryRoot(meta)
        val arr = librariesMap[root] ?: return false
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
        refreshDatabases()

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
            metadataDB.close()
        }.onFailure {
            logger.error(it)
        }
    }

    private fun dbForLibrary(root: String): DB {
        val meta = librariesMap[root]?.meta() ?: synchronized(this) {
            val id = librariesMap[root]
            if (id != null) {
                id.meta()
            } else {
                val newID = UUID.randomUUID().toString()
                val dbMeta = DBMeta(newID, false)
                librariesMap[root] = arrayOf(dbMeta.file, dbMeta.completed)
                dbMeta
            }
        }
        refreshDatabases()

        val result = databases[meta.file] ?: throw IllegalStateException("No db for $root: id=$meta")
        result.init()
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

    private fun refreshDatabases() {
        for ((root, arr) in librariesMap) {
            if (allLibrariesRoots == null || allLibrariesRoots.contains(root)) {
                val meta = arr.meta()
                val storageFile = meta.file
                databases.computeIfAbsent(storageFile) {
                    MDB(project, globalStorage.resolve(storageFile), true, meta.completed)
                }
            }
        }
        val globalDBsCopy = databases.toMap()
        val allDBFiles = librariesMap.values.mapNotNull { it?.meta()?.file }.toSet()
        for ((file, _) in globalDBsCopy) {
            if (!allDBFiles.contains(file)) {
                val removed = databases.remove(file)
                kotlin.runCatching {
                    removed?.close()
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
}