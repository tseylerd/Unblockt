// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

package tse.unblockt.ls.server.analysys.storage

import com.intellij.openapi.project.Project
import org.apache.logging.log4j.kotlin.logger
import tse.unblockt.ls.server.fs.cutProtocol
import tse.unblockt.ls.server.util.State
import java.nio.file.Path

class LocalGlobalRouter(
    project: Project,
    projectPath: Path,
    localStoragePath: Path,
    globalStoragePath: Path,
    allLibrariesRoots: Collection<String>,
): RouterDB.CompletableRouter {
    private val projectPathString = projectPath.toString()
    private val localDB = VersionedDB(localStoragePath) { RouterDB(ShardedRouter(project, localStoragePath, false, 10)) }

    internal val globalDB = ExclusiveWriteAppendOnlyDB(globalStoragePath)  {
        VersionedDB(globalStoragePath) {
            RouterDB(LibrariesRouter(project, globalStoragePath, projectPath, allLibrariesRoots))
        }
    }

    override val all: Collection<DB> = listOf(localDB, globalDB)

    override fun init(): InitializationResult {
        val localInitResult = localDB.init()
        if (!localInitResult.success) {
            return localInitResult
        }

        val globalInitResult = globalDB.init()
        if (!globalInitResult.success) {
            return globalInitResult
        }

        return InitializationResult(
            wiped = localInitResult.wiped || globalInitResult.wiped,
            success = true
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

    private fun dbByMeta(meta: String): CompletableDB {
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

    override fun dbsByKey(attribute: DB.Attribute<*, *, *>, key: String): Collection<DB> {
        if (attribute.shared == State.NO) {
            return listOf(localDB)
        }
        if (attribute.shared == State.YES) {
            return listOf(globalDB)
        }
        return all
    }

    override fun dbToPut(attribute: DB.Attribute<*, *, *>, meta: String, key: String): DB {
        if (attribute.shared == State.NO) {
            return localDB
        }
        if (attribute.shared == State.YES) {
            return globalDB
        }
        return dbsByMeta(meta).single()
    }

    override fun dbsToDeleteByMeta(meta: String): Collection<DB> {
        return dbsByMeta(meta)
    }

    override fun complete() {
        globalDB.complete()
    }

    override fun isCompleted(meta: String): Boolean {
        val db = dbByMeta(meta)
        if (db !== globalDB) {
            return false
        }
        return db.isComplete(meta)
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