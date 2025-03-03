// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

package tse.unblockt.ls.server.analysys.storage

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CancellationException
import org.apache.logging.log4j.kotlin.logger
import tse.unblockt.ls.protocol.HealthStatus
import tse.unblockt.ls.protocol.HealthStatusInformation
import java.nio.file.Path

class PersistentStorage(
    project: Project,
    workspaceStorage: Path,
    globalStorage: Path,
    projectRoot: Path,
    allLibrariesRoots: Collection<String>,
): Disposable {
    private val dbManager = MainDBManager(project, projectRoot, workspaceStorage, globalStorage, allLibrariesRoots)

    companion object {
        inline fun <T> safely(call: () -> T): T? {
            return try {
                call()
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                logger.error(t.stackTraceToString(), t)
                null
            }
        }

        fun instance(project: Project): PersistentStorage {
            return project.service()
        }

        fun create(
            workspaceStoragePath: Path,
            globalStoragePath: Path,
            project: Project,
            projectRoot: Path,
            allLibrariesRoots: Set<String>,
        ): PersistentStorage {
            return PersistentStorage(
                project,
                workspaceStoragePath,
                globalStoragePath,
                projectRoot,
                allLibrariesRoots,
            )
        }
    }

    fun init() {
        dbManager.initDB()
    }

    fun init(namespace: Namespace, attribute: DB.Attribute<*, *, *>) {
        val attributed = namespace.attributed(attribute)
        dbManager.database.init(attributed.name)
    }

    fun exists(meta: String): Boolean {
        return dbManager.database.isComplete(meta)
    }

    private fun complete() {
        dbManager.database.complete()
    }

    fun health(): HealthStatusInformation? {
        if (!dbManager.database.isValid) {
            return HealthStatusInformation(
                "Indexes are corrupted",
                "Indexes are corrupted",
                HealthStatus.ERROR
            )
        }
        return null
    }

    suspend fun exclusively(modification: suspend PersistentStorage.() -> Unit) {
        dbManager.exclusively {
            this.modification()
            complete()
        }
    }

    fun <K: Any, V: Any> put(namespace: Namespace, attribute: DB.Attribute<String, K, V>, source: String, key: K, value: V) {
        val attributed = namespace.attributed(attribute)
        dbManager.database.put(attributed.name, attribute, source, key, value)
    }

    fun <K: Any, V: Any> putAll(namespace: Namespace, attribute: DB.Attribute<String, K, V>, triples: Set<Triple<String, K, V>>) {
        val attributed = namespace.attributed(attribute)
        dbManager.database.putAll(attributed.name, attribute, triples)
    }

    fun <K: Any, V: Any> getSequence(namespace: Namespace, attribute: DB.Attribute<String, K, V>): Sequence<Pair<K, V>> {
        return sequence {
            safely {
                val all = dbManager.database.all(namespace.attributed(attribute).name, attribute)
                for (p in all) {
                    yield(p)
                }
            }
        }
    }

    fun <K: Any, V: Any> getSequenceOfKeys(namespace: Namespace, attribute: DB.Attribute<String, K, V>): Sequence<K> {
        return sequence {
            safely {
                    val allKeys = dbManager.database.allKeys(namespace.attributed(attribute).name, attribute)
                    for (key in allKeys) {
                        yield(key)
                    }
            }
        }
    }

    fun <K: Any, V: Any> getSequenceOfValues(namespace: Namespace, attribute: DB.Attribute<String, K, V>): Sequence<V> {
        return sequence {
            safely {
                val seq = dbManager.database.allValues(namespace.attributed(attribute).name, attribute)
                for (v in seq) {
                    yield(v)
                }
            }
        }
    }

    fun <K: Any, V: Any> getSequence(namespace: Namespace, attribute: DB.Attribute<String, K, V>, key: K): Sequence<V> {
        return sequence {
            safely {
                val values = dbManager.database.values(namespace.attributed(attribute).name, attribute, key)
                for (value in values) {
                    yield(value)
                }
            }
        }
    }

    fun <K: Any, V: Any> exists(namespace: Namespace, attribute: DB.Attribute<String, K, V>, key: K): Boolean {
        return safely {
            dbManager.database.exists(namespace.attributed(attribute).name, attribute, key)
        } ?: false
    }

    fun delete(namespace: Namespace, attribute: DB.Attribute<String, *, *>, source: String) {
        val attributed = namespace.attributed(attribute)
        dbManager.database.deleteByMeta(attributed.name, attribute, source)
    }

    override fun dispose() {
        dbManager.database.close()
    }

    fun deleteAll() {
        dbManager.cleanup()
    }

    fun clearCache() {
    }

    fun shutdown() {
    }

    data class Namespace(val name: String) {
        init {
            if (name.contains(".")) {
                throw IllegalArgumentException("$name contains dot")
            }
        }

        fun attributed(attribute: DB.Attribute<*, *, *>): Namespace {
            return Namespace("${name}_${attribute.name}")
        }
    }
}