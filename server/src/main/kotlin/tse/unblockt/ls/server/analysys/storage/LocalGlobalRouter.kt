// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

package tse.unblockt.ls.server.analysys.storage

import com.intellij.openapi.project.Project
import org.apache.logging.log4j.kotlin.logger
import tse.unblockt.ls.server.fs.cutProtocol
import java.nio.file.Path

class LocalGlobalRouter(
    project: Project,
    projectPath: Path,
    localStoragePath: Path,
    globalStoragePath: Path
): RouterDB.FreezableRouter {
    override val metadataDB: SafeDB
        get() = throw UnsupportedOperationException()

    private val projectPathString = projectPath.toString()
    private val localDB = VersionedDB { RouterDB(ShardedRouter(project, localStoragePath, false, 10)) }
    internal val globalDB = VersionedDB { RouterDB(LibrariesRouter(project, globalStoragePath)) }

    override val all: Collection<DB> = listOf(localDB, globalDB)

    fun deleteLocal() {
        kotlin.runCatching {
            localDB.close()
        }.onFailure {
            logger.error(it)
        }
        kotlin.runCatching {
            localDB.delete()
        }.onFailure {
            logger.error(it)
        }
    }

    override fun init(): Wiped {
        return Wiped(
            all.map {
                it.init()
            }.any { it.value }
        )
    }

    override fun dbsByMeta(meta: String): Collection<DB> {
        return when {
            meta.isLibraryUrl -> {
                val withoutProtocol = meta.cutProtocol
                when {
                    withoutProtocol.startsWith(projectPathString) -> listOf(localDB)
                    else -> listOf(globalDB)
                }
            }
            else -> listOf(localDB)
        }
    }

    private fun dbByMeta(meta: String): VersionedDB {
        return when {
            meta.isLibraryUrl -> {
                val withoutProtocol = meta.cutProtocol
                when {
                    withoutProtocol.startsWith(projectPathString) -> localDB
                    else -> globalDB
                }
            }
            else -> localDB
        }
    }

    override fun dbsByKey(key: String): Collection<DB> {
        return all
    }

    override fun dbToPut(attribute: DB.Attribute<*, *, *>, meta: String, key: String): DB {
        if (attribute.forceLocal) {
            return localDB
        }
        return dbsByMeta(meta).single()
    }

    override fun freeze() {
        globalDB.freeze()
    }

    override fun isFrozen(meta: String): Boolean {
        val db = dbByMeta(meta)
        if (db !== globalDB) {
            return false
        }
        return db.isFrozen(meta)
    }

    override fun delete() {
        all.forEach {
            it.delete()
        }
    }

    private val String.isLibraryUrl: Boolean
        get() = startsWith("jrt://") || startsWith("jar://")

    override fun close() {
        kotlin.runCatching {
            localDB.close()
        }.onFailure {
            logger.error(it)
        }

        kotlin.runCatching {
            globalDB.close()
        }.onFailure {
            logger.error(it)
        }
    }
}