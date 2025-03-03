// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

package tse.unblockt.ls.server.analysys.storage

import com.intellij.openapi.project.Project
import org.mapdb.Serializer
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.math.abs

class ShardedRouter(private val project: Project, private val root: Path, private val appendOnly: Boolean, private val shards: Int): RouterDB.Router {
    private val dbs = arrayOfNulls<DB>(shards)
    override val all: Collection<DB>
        get() = dbs.filterNotNull().toList()


    private lateinit var metaDB: SafeDB

    @OptIn(ExperimentalPathApi::class)
    override fun init(): InitializationResult {
        if (::metaDB.isInitialized) {
            return InitializationResult(false, success = true)
        }

        val indexesPath = DB.indexesPath(root)
        if (!Files.exists(indexesPath)) {
            indexesPath.createDirectories()
        }
        val metadataPath = indexesPath.resolve("metadata.db")
        val (mdb, result) = MDB.openOrCreateDB(metadataPath) {
            MDB.makeMetaDB(metadataPath)
        }
        if (mdb == null) {
            return result
        }
        metaDB = SharedDBWrapperImpl { mdb }
        val metadataMap = metaDB.resource {
            db -> db.hashMap("metadata", Serializer.STRING, Serializer.STRING).createOrOpen()
        }
        val shardsCount = metadataMap.read { it["shards"] }?.toInt()
        var wiped = false
        if (shardsCount != shards) {
            wiped = true
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
        metadataMap.write {
            it["shards"] = shards.toString()
        }

        for (i in 0 until shards) {
            val init = initBucket(i).init()
            wiped = init.wiped || wiped
            if (!init.success) {
                return InitializationResult(wiped = wiped, success = false)
            }
        }
        return InitializationResult(wiped, true)
    }


    @OptIn(ExperimentalPathApi::class)
    override fun delete() {
        close()

        val resolve = DB.indexesPath(root)
        if (resolve.exists()) {
            resolve.deleteRecursively()
        }
    }

    override fun close() {
        if (::metaDB.isInitialized) {
            metaDB.close()
        }
        dbs.forEach { it?.close() }
    }

    private fun initBucket(i: Int): DB {
        val bucketDir = DB.indexesPath(root).resolve(i.toString())
        if (!bucketDir.exists()) {
            bucketDir.createDirectories()
        }
        val mdb = MDB(project, bucketDir, appendOnly, false)
        dbs[i] = mdb
        return mdb
    }

    override fun dbsByMeta(meta: String): Collection<DB> {
        return all
    }

    override fun dbsToDeleteByMeta(meta: String): Collection<DB> {
        return all
    }

    override fun dbsByKey(attribute: DB.Attribute<*, *, *>, key: String): Collection<DB> {
        val bucket = abs(key.hashCode() % shards)
        return listOf(dbs[bucket]!!)
    }

    override fun dbToPut(attribute: DB.Attribute<*, *, *>, meta: String, key: String): DB {
        return dbsByKey(attribute, key).single()
    }
}