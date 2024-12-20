// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

package tse.unblockt.ls.server.analysys.storage

interface IndexVersionProvider {
    val version: Long

    companion object {
        private var current: IndexVersionProvider = Simple(9)

        fun instance(): IndexVersionProvider {
            return current
        }

        fun set(new: IndexVersionProvider) {
            current = new
        }
    }

    data class Simple(override val version: Long) : IndexVersionProvider
}