// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

package tse.unblockt.ls.server.analysys.index.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import tse.unblockt.ls.server.analysys.index.stub.IndexModel
import tse.unblockt.ls.server.analysys.storage.DB
import tse.unblockt.ls.server.analysys.storage.PersistentStorage

object ProjectModelSetup {
    val namespace = PersistentStorage.Namespace("project_model")

    val entryAttribute = DB.Attribute<String, String, IndexModel.Entry>(
        name = "url_to_entry",
        metaToString = { it },
        stringToMeta = { it },
        keyToString = { it },
        valueToString = { Json.encodeToString(it) },
        stringToKey = { _, str -> str },
        stringToValue = { _, str -> Json.decodeFromString(str) },
        config = DB.Store.Config.UNIQUE_KEY,
    )

    val versionAttribute = DB.Attribute(
        "model_version",
        metaToString = { it },
        stringToMeta = { it },
        keyToString = { it },
        valueToString = { it.toString() },
        stringToKey = { _, str -> str },
        stringToValue = { _, str -> str.toLong() },
        config = DB.Store.Config.SINGLE
    )
    const val versionKey = "version"
    const val sourceKey = "source"
}