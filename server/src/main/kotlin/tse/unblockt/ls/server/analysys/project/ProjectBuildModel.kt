// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

package tse.unblockt.ls.server.analysys.project

import kotlinx.serialization.Serializable

@Serializable
data class ProjectBuildModel(
    val version: Long,
    val entries: Set<ProjectBuildEntry>,
    val changes: Set<Change>,
) {
    companion object {
        const val VERSION = 1L
    }
}

@Serializable
data class Change(
    val url: String,
    val kind: ChangeKind,
)

@Serializable
enum class ChangeKind {
    CHANGED,
    DELETED,
}

@Serializable
data class ProjectBuildEntry(
    val url: String,
    val hash: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ProjectBuildEntry

        if (url != other.url) return false
        if (!hash.contentEquals(other.hash)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = url.hashCode()
        result = 31 * result + hash.contentHashCode()
        return result
    }
}