// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

package tse.unblockt.ls.server.analysys.storage

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CancellationException
import tse.unblockt.ls.logger
import tse.unblockt.ls.protocol.HealthStatus
import tse.unblockt.ls.protocol.HealthStatusInformation
import java.nio.file.Files
import java.nio.file.Path

class PersistentStorage(private val project: Project, private val root: Path): Disposable {
    private var db = MDB(project, root)
    private var forciblyValid: Boolean = false

    companion object {
        inline fun <T> safely(call: () -> T): T? {
            return try {
                call()
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                logger.error(t)
                null
            }
        }

        fun instance(project: Project): PersistentStorage {
            return project.service()
        }

        fun create(root: Path, project: Project): PersistentStorage {
            return PersistentStorage(
                project,
                root
            )
        }
    }

    fun init() {
        db.init()

        init(Internals.namespace, Internals.attribute)
    }

    fun init(namespace: Namespace, attribute: DB.Attribute<*, *, *>) {
        val attributed = namespace.attributed(attribute)
        db.init(attributed.name, attribute.config)
    }

    fun health(): HealthStatusInformation? {
        if (forciblyValid) {
            return null
        }
        if (!Files.exists(MDB.indexesPath(root))) {
            return HealthStatusInformation(
                "Indexes are corrupted",
                "Indexes are corrupted",
                HealthStatus.ERROR
            )
        }
        if (!isValid()) {
            return HealthStatusInformation(
                "Indexes are corrupted",
                "Indexes are corrupted",
                HealthStatus.ERROR
            )
        }
        return null
    }

    suspend fun together(modification: suspend PersistentStorage.() -> Unit) {
        val before = forciblyValid
        forciblyValid = true

        try {
            setInProgress(true)
            this.modification()
            setInProgress(false)
        } finally {
            forciblyValid = before
        }
    }

    suspend fun sync(modification: suspend PersistentStorage.() -> Unit) {
        together {
            modification()
        }
    }

    fun isValid(): Boolean {
        if (forciblyValid) {
            return true
        }
        if (db.isClosed) {
            return false
        }
        return forciblyValid || !getInProgress()
    }

    fun <K: Any, V: Any> put(namespace: Namespace, attribute: DB.Attribute<String, K, V>, source: String, key: K, value: V) {
        db.inTx {
            val attributed = namespace.attributed(attribute)
            val store = store(attributed.name, attribute)
            store.put(source, key, value)
        }
    }

    fun <K: Any, V: Any> putAll(namespace: Namespace, attribute: DB.Attribute<String, K, V>, triples: Set<Triple<String, K, V>>) {
        db.inTx {
            val attributed = namespace.attributed(attribute)
            val store = store(attributed.name, attribute)
            store.putAll(triples)
        }
    }

    fun <K: Any, V: Any> getSequence(namespace: Namespace, attribute: DB.Attribute<String, K, V>): Sequence<Pair<K, V>> {
        return sequence {
            safely {
                db.inTx {
                    val store = store(namespace.attributed(attribute).name, attribute)
                    val all = store.all()
                    for (p in all) {
                        yield(p)
                    }
                }
            }
        }
    }

    fun <K: Any, V: Any> getSequenceOfKeys(namespace: Namespace, attribute: DB.Attribute<String, K, V>): Sequence<K> {
        return sequence {
            safely {
                db.inTx {
                    val store = store(namespace.attributed(attribute).name, attribute)
                    val allKeys = store.allKeys()
                    for (key in allKeys) {
                        yield(key)
                    }
                }
            }
        }
    }

    fun <K: Any, V: Any> getSequenceOfValues(namespace: Namespace, attribute: DB.Attribute<String, K, V>): Sequence<V> {
        return sequence {
            safely {
                db.inTx {
                    val seq = store(namespace.attributed(attribute).name, attribute).allValues()
                    for (v in seq) {
                        yield(v)
                    }
                }
            }
        }
    }

    fun <K: Any, V: Any> getSequence(namespace: Namespace, attribute: DB.Attribute<String, K, V>, key: K): Sequence<V> {
        return sequence {
            safely {
                db.inTx {
                    val store = store(namespace.attributed(attribute).name, attribute)
                    for (value in store.values(key)) {
                        yield(value)
                    }
                }
            }
        }
    }

    fun <K: Any, V: Any> exists(namespace: Namespace, attribute: DB.Attribute<String, K, V>, key: K): Boolean {
        return safely {
            db.inTx {
                val store = store(namespace.attributed(attribute).name, attribute)
                store.exists(key)
            }
        } ?: false
    }

    fun delete(namespace: Namespace, attribute: DB.Attribute<String, *, *>, source: String) {
        db.inTx {
            val attributed = namespace.attributed(attribute)
            val store = store(attributed.name, attribute)
            store.deleteByMeta(source)
        }
    }

    override fun dispose() {
        db.close()
    }

    fun deleteAll() {
        db.close()
        db.delete()
        db = MDB(project, root)
    }

    fun clearCache() {
    }

    private fun setInProgress(boolean: Boolean) {
        put(Internals.namespace, Internals.attribute, Internals.SOURCE, Internals.KEY, boolean)
    }

    fun shutdown() {
    }

    private fun getInProgress(): Boolean {
        val got = getSequence(Internals.namespace, Internals.attribute, Internals.KEY).toList()
        return got.size == 1 && got.single()
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

    private object Internals {
        val namespace = Namespace("internal")
        val attribute = DB.Attribute(
            name = "internals",
            metaToString = { it },
            stringToMeta = { it },
            keyToString = { it },
            valueToString = { it.toString() },
            stringToKey = { _, str -> str },
            stringToValue = { _, str -> str.toBoolean() },
            config = DB.Store.Config.SINGLE,
        )
        const val KEY = "inProgress"
        const val SOURCE = "PROGRESS"
    }
}