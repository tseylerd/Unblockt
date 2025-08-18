// Copyright 2024 Dmitrii Tseiler. Licensed under the Apache 2.0 Licence

package tse.unblockt.ls.server.analysys.storage

import java.nio.file.Path

class Metadata(private val path: Path, private val key: String) {
    internal lateinit var db: SafeDB

    fun init(): InitializationResult {
        val dbPath = path.resolve("meta_$key")
        val (mdb, result) = MDB.openOrCreateDB(dbPath) {
            MDB.makeMetaDB(dbPath)
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