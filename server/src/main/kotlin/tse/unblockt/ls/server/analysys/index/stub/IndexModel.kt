// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

package tse.unblockt.ls.server.analysys.index.stub

import kotlinx.serialization.Serializable

@Serializable
data class IndexModel(
    val paths: Set<Entry>,
) {
    @Serializable
    data class Entry(
        val url: String,
        val properties: EntryProperties,
    )

    @Serializable
    data class EntryProperties(
        val editedAt: Long,
        val builtIns: Boolean,
        val stub: Boolean,
    )
}