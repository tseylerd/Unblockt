// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

package tse.unblockt.ls.server.analysys.storage

import java.nio.file.Path

class Metadata(private val path: Path, private val key: String) {
    internal lateinit var db: SafeDB

    fun init(): InitializationResult {
        val lockPath = path.resolve("meta_$key")
        val (mdb, result) = MDB.openOrCreateDB(lockPath) {
            MDB.makeMetaDB(lockPath)
        }
        if (mdb == null) {
            return result
        }

        db = SharedDBWrapperImpl {
            mdb
        }

        return result
    }
}