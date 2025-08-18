// Copyright 2024 Dmitrii Tseiler. Licensed under the Apache 2.0 Licence

package tse.unblockt.ls.server.analysys.storage

interface CompletableDB: DB {
    fun complete()
    fun isComplete(meta: String): Boolean
}