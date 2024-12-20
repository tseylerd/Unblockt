// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

package tse.unblockt.ls.server.analysys.storage

import org.mapdb.DB
import org.mapdb.DB as MapDB

class SharedDBWrapperImpl(dbFactory: () -> MapDB): SafeDB {
    private val db = dbFactory()
    private val lock = db.atomicBoolean("exclusive_access").createOrOpen()

    override fun <T> lock(block: (MapDB) -> T): T {
        var result = lock.compareAndSet(false, true)
        while (!result) {
            Thread.sleep(100)
            result = lock.compareAndSet(false, true)
        }
        try {
            return block(db)
        } finally {
            lock.set(false)
            db.commit()
        }
    }

    override fun <T> write(block: (DB) -> T): T {
        return try {
            block(db)
        } finally {
            db.commit()
        }
    }

    override fun <T> read(block: (DB) -> T): T {
        return block(db)
    }

    override fun close() {
        db.close()
    }
}