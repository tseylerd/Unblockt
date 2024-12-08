// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

package tse.unblockt.ls.server.analysys.psi

import com.intellij.psi.*
import com.intellij.psi.stubs.StubElement
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.config.LanguageVersionSettingsImpl
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import tse.unblockt.ls.server.analysys.index.common.IndexFileEntry
import tse.unblockt.ls.server.analysys.index.machines.asLight
import tse.unblockt.ls.server.analysys.index.model.Location
import tse.unblockt.ls.server.analysys.index.model.PsiEntry
import tse.unblockt.ls.server.analysys.index.model.PsiRange
import tse.unblockt.ls.server.analysys.index.model.StubSteps
import kotlin.reflect.KClass

val PsiFile.packageName: String?
    get() = when (this) {
        is KtFile -> packageFqName.asString()
        is PsiClassOwner -> packageName
        else -> null
    }

val PsiFile.isKotlin: Boolean get() {
    return virtualFile.extension == "kt"
}

inline fun <reified T: Any> IndexFileEntry.all(): List<T> {
    return stub?.generateList(T::class) ?: SyntaxTraverser.psiTraverser(psiFile).filterIsInstance<T>()
}

fun <T: PsiElement> IndexFileEntry.all(clazz: KClass<T>): List<PsiEntry<T>> {
    return stub?.generateEntries(this, clazz) ?: generateEntries(this, clazz)
}

fun <T: Any> StubElement<*>.generateList(clazz: KClass<T>): List<T> {
    if (clazz.isInstance(psi)) {
        @Suppress("UNCHECKED_CAST")
        return listOf(psi as T)
    }
    return childrenStubs.flatMap { it.generateList(clazz) }
}

fun <T: PsiElement> StubElement<*>.generateEntries(indexFileEntry: IndexFileEntry, clazz: KClass<T>): List<PsiEntry<T>> {
    @Suppress("UNCHECKED_CAST") val result = when {
        clazz.isInstance(psi) -> listOf(PsiEntry(Location(indexFileEntry.asLight(), null, StubSteps(intArrayOf(StubSteps.GET))), psi as T))
        else -> emptyList()
    }

    val children = childrenStubs
    val entries = result.toMutableList()
    for (i in children.indices) {
        val generateList = children[i].generateEntries(indexFileEntry, clazz)
        entries += generateList.map { e ->
            e.copy(location = e.location.copy(steps = StubSteps(intArrayOf(i)).concat(e.location.steps!!)))
        }
    }
    return entries
}

fun <T: PsiElement> generateEntries(indexFileEntry: IndexFileEntry, clazz: KClass<T>): List<PsiEntry<T>> {
    val light = indexFileEntry.asLight()
    return SyntaxTraverser.psiTraverser(indexFileEntry.psiFile).filter(clazz.java).map {
        PsiEntry(Location(light, PsiRange(it.textRange.startOffset, it.textRange.endOffset), null), it as T)
    }.toList()
}

internal val KtElement.asReference
    get() = when (this) {
        is KtCallExpression -> calleeExpression?.mainReference
        is KtDotQualifiedExpression -> selectorExpression?.mainReference
        else -> mainReference
    }

@Suppress("UnusedReceiverParameter")
val PsiElement.languageVersionSettings: LanguageVersionSettings
    get() = LanguageVersionSettingsImpl.DEFAULT

fun PsiElement.prevSiblingSkipSpaces(): PsiElement? {
    var prev: PsiElement = prevSibling ?: return null
    while (prev is PsiWhiteSpace) {
        prev = prev.prevSibling ?: return null
    }
    return prev
}