// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

package tse.unblockt.ls.server.analysys.storage

import org.mapdb.DB

interface SafeDB: AutoCloseable {
    fun <T> lock(block: (DB) -> T): T
    fun <T> write(block: (DB) -> T): T
    fun <T> read(block: (DB) -> T): T

    @Suppress("unused")
    class Transparent(private val db: DB): SafeDB {
        override fun <T> lock(block: (DB) -> T): T {
            return block(db)
        }

        override fun <T> write(block: (DB) -> T): T {
            return block(db)
        }

        override fun <T> read(block: (DB) -> T): T {
            return block(db)
        }

        override fun close() {
            db.close()
        }
    }
}

class SafeDBResource<T>(val db: SafeDB, val resource: T)

inline fun <T, R> SafeDBResource<T>.write(crossinline call: (T) -> R): R {
    return db.write {
        call(resource)
    }
}

inline fun <T, R> SafeDBResource<T>.read(call: (T) -> R): R {
    return call(resource)
}

fun <T> SafeDB.resource(block: (DB) -> T): SafeDBResource<T> {
    return SafeDBResource(this, read { block(it) })
}