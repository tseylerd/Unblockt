// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

package tse.unblockt.ls.server.analysys.index.machines

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClassOwner
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtFile
import tse.unblockt.ls.server.analysys.index.LsPsiCache
import tse.unblockt.ls.server.analysys.index.LsStubCache
import tse.unblockt.ls.server.analysys.index.common.IndexFileEntry
import tse.unblockt.ls.server.analysys.storage.DB
import tse.unblockt.ls.server.fs.LsFileSystem

abstract class BaseFileByFqNameIndexMachine(project: Project) : IndexMachine<String, IndexFileEntry> {
    private val psiCache: LsPsiCache = LsPsiCache.instance(project)
    private val fileSystem: LsFileSystem = LsFileSystem.instance()
    private val stubCache: LsStubCache = LsStubCache.instance(project)

    override val attribute: DB.Attribute<String, String, IndexFileEntry> = DB.Attribute(
        "package_to_file",
        metaToString = { it },
        stringToMeta = { it },
        keyToString = {
            it
        },
        valueToString = {
            val lightFileEntry = it.asLight()
            Json.encodeToString(lightFileEntry)
        },
        stringToKey = { _, str ->
            str
        },
        stringToValue = { _, str ->
            val decoded = Json.decodeFromString<LightFileEntry>(str)
            decoded.asIndexFileEntry(fileSystem, psiCache, stubCache)
        },
    )

    override fun index(entry: IndexFileEntry): List<Pair<String, IndexFileEntry>> {
        val packageName = when (entry.psiFile) {
            is KtFile -> entry.psiFile.packageFqName
            is PsiClassOwner -> FqName(entry.psiFile.packageName)
            else -> null
        } ?: return emptyList()
        return listOf(packageName.asString() to entry)
    }
}