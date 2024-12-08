// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

package tse.unblockt.ls.server.analysys.index.model

import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.StubElement
import com.intellij.psi.util.PsiTreeUtil
import kotlinx.serialization.Serializable
import tse.unblockt.ls.server.analysys.index.LsPsiCache
import tse.unblockt.ls.server.analysys.index.LsStubCache
import tse.unblockt.ls.server.analysys.index.common.IndexFileEntry
import tse.unblockt.ls.server.analysys.index.machines.LightFileEntry
import tse.unblockt.ls.server.analysys.index.machines.asIndexFileEntry
import tse.unblockt.ls.server.fs.LsFileSystem
import kotlin.reflect.KClass

@Serializable
data class StubSteps(val steps: IntArray) {
    companion object {
        const val GET = -1
    }

    fun concat(steps: StubSteps): StubSteps {
        return StubSteps(this.steps + steps.steps)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as StubSteps

        return steps.contentEquals(other.steps)
    }

    override fun hashCode(): Int {
        return steps.contentHashCode()
    }
}

@Serializable
data class PsiRange(
    val start: Int,
    val end: Int,
)

@Serializable
data class Location(
    val location: LightFileEntry,
    val rangeInFile: PsiRange? = null,
    val steps: StubSteps? = null,
) {
    fun heavy(fileSystem: LsFileSystem, cache: LsPsiCache, stubCache: LsStubCache): HeavyLocation? {
        val ife = location.asIndexFileEntry(fileSystem, cache, stubCache) ?: return null
        return HeavyLocation(ife, rangeInFile, steps)
    }
}

data class HeavyLocation(
    val location: IndexFileEntry,
    val rangeInFile: PsiRange? = null,
    val steps: StubSteps? = null,
)

data class PsiEntry<T: PsiElement>(
    val location: Location,
    val element: T,
)

fun <T: PsiElement> HeavyLocation.find(clazz: KClass<T>): T? {
    if (steps != null) {
        var current: StubElement<*> = location.stub!!
        for (oneStep in steps.steps) {
            if (oneStep == StubSteps.GET) {
                @Suppress("UNCHECKED_CAST")
                return current.psi as T
            } else {
                current = current.childrenStubs[oneStep]
            }
        }
    }
    if (rangeInFile != null) {
        val psiFile = location.psiFile
        return PsiTreeUtil.findElementOfClassAtRange(psiFile, rangeInFile.start, rangeInFile.end, clazz.java)
    }
    throw IllegalStateException("Both steps and range are nulls")
}