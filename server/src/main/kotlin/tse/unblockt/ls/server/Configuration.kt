// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

package tse.unblockt.ls.server

object Configuration {
    val IN_MEMORY_INDEXES = System.getProperty("unblockt.in.memory.indexes", "false").toBoolean()
}