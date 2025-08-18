// Copyright 2024 Dmitrii Tseiler. Licensed under the Apache 2.0 Licence

package tse.unblockt.ls.server.analysys.storage

interface IndexVersionProvider {
    val version: Long

    companion object {
        private var current: IndexVersionProvider = Simple(10)

        fun instance(): IndexVersionProvider {
            return current
        }

        fun set(new: IndexVersionProvider) {
            current = new
        }
    }

    data class Simple(override val version: Long) : IndexVersionProvider
}