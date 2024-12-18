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
    override val supportsDeletionByMeta: Boolean
        get() = !appendOnly

    private val dbs = arrayOfNulls<DB>(shards)
    override val all: Collection<DB>
        get() = dbs.filterNotNull().toList()


    private lateinit var metaDB: SafeDB

    override val metadataDB: SafeDB
        get() = metaDB

    @OptIn(ExperimentalPathApi::class)
    override fun init(): Wiped {
        if (::metaDB.isInitialized) {
            return Wiped(false)
        }

        val indexesPath = DB.indexesPath(root)
        if (!Files.exists(indexesPath)) {
            indexesPath.createDirectories()
        }
        val metadataPath = indexesPath.resolve("metadata.db")
        metaDB = SharedDBWrapperImpl {
            MDB.openOrCreateDB(metadataPath) {
                MDB.makeMetaDB(metadataPath)
            }
        }
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
            initBucket(i).init()
        }
        return Wiped(wiped)
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
        val mdb = MDB(project, bucketDir, appendOnly)
        dbs[i] = mdb
        return mdb
    }

    override fun dbsByMeta(meta: String): Collection<DB> {
        return all
    }

    override fun dbsByKey(key: String): Collection<DB> {
        val bucket = abs(key.hashCode() % shards)
        return listOf(dbs[bucket]!!)
    }

    override fun dbToPut(attribute: DB.Attribute<*, *, *>, meta: String, key: String): DB {
        return dbsByKey(key).single()
    }
}