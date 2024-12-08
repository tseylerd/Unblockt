// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

package tse.unblockt.ls.server.analysys.index.machines

import kotlinx.serialization.Serializable
import org.jetbrains.kotlin.psi.stubs.impl.KotlinFileStubImpl
import tse.unblockt.ls.server.analysys.index.LsPsiCache
import tse.unblockt.ls.server.analysys.index.LsStubCache
import tse.unblockt.ls.server.analysys.index.common.IndexFileEntry
import tse.unblockt.ls.server.fs.LsFileSystem

@Serializable
data class LightFileEntry(
    val url: String,
    val isStub: Boolean,
    val isKotlin: Boolean,
    val isBuiltIns: Boolean
)

fun IndexFileEntry.asLight(): LightFileEntry {
    return LightFileEntry(
        psiFile.virtualFile.url,
        stub != null,
        stub is KotlinFileStubImpl,
        builtIns
    )
}

fun LightFileEntry.asIndexFileEntry(fileSystem: LsFileSystem, psiCache: LsPsiCache, stubCache: LsStubCache): IndexFileEntry? {
    val vFile = fileSystem.getVirtualFileByUrl(url) ?: return null
    val psiFile = psiCache[vFile] ?: return null
    return IndexFileEntry(psiFile, if (isStub) stubCache[vFile, isBuiltIns] else null, isKotlin, isBuiltIns)
}