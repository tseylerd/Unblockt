// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

package tse.unblockt.ls.server.analysys.index.machines

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import tse.unblockt.ls.server.analysys.index.LsPsiCache
import tse.unblockt.ls.server.analysys.index.LsStubCache
import tse.unblockt.ls.server.analysys.index.common.IndexFileEntry
import tse.unblockt.ls.server.analysys.index.model.Location
import tse.unblockt.ls.server.analysys.index.model.PsiEntry
import tse.unblockt.ls.server.analysys.index.model.find
import tse.unblockt.ls.server.analysys.psi.all
import tse.unblockt.ls.server.analysys.storage.DB
import tse.unblockt.ls.server.fs.LsFileSystem
import kotlin.reflect.KClass

abstract class PsiIndexMachine<K: Any, V: PsiElement>(
    private val clazz: KClass<V>,
    config: DB.Store.Config,
    attributeName: String,
    project: Project,
): IndexMachine<K, PsiEntry<V>> {
    private val psiCache: LsPsiCache = LsPsiCache.instance(project)
    private val fileSystem: LsFileSystem = LsFileSystem.instance()
    private val stubCache: LsStubCache = LsStubCache.instance(project)

    override val attribute: DB.Attribute<String, K, PsiEntry<V>> = DB.Attribute(
        name = attributeName,
        metaToString = { it },
        stringToMeta = { it },
        keyToString = {
            keyToString(it)
        },
        valueToString = { v ->
            Json.encodeToString(v.location)
        },
        stringToKey = { _, str ->
            stringToKey(str)
        },
        stringToValue = { _, str ->
            val lfe = Json.decodeFromString<Location>(str)
            val found = lfe.heavy(fileSystem, psiCache, stubCache)?.find(clazz) ?: return@Attribute null
            PsiEntry(lfe, found)
        },
        config = config,
    )

    override fun index(entry: IndexFileEntry): List<Pair<K, PsiEntry<V>>> {
        if (!support(entry)) {
            return emptyList()
        }

        val elements = entry.all(clazz)
        if (elements.isEmpty()) return emptyList()
        val producePairs = producePairs(entry, elements)
        return producePairs
    }

    abstract fun producePairs(entry: IndexFileEntry, elements: List<PsiEntry<V>>): List<Pair<K, PsiEntry<V>>>
    abstract fun support(entry: IndexFileEntry): Boolean

    protected abstract fun keyToString(key: K): String
    protected abstract fun stringToKey(string: String): K
}