// Copyright 2024 Dmitrii Tseiler. Licensed under the Apache 2.0 Licence

package tse.unblockt.ls.server

object Configuration {
    val IN_MEMORY_INDEXES = System.getProperty("unblockt.in.memory.indexes", "false").toBoolean()
}