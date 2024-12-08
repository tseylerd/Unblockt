package tse.Unblockt.ls.server

import com.intellij.psi.PsiElement
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.apache.logging.log4j.kotlin.logger

inline fun <reified T> T.debugLog(element: PsiElement) {
    logger.debug("[${element::class.simpleName}] [${element.text}] [${element.node?.elementType?.let { it::class.simpleName }}]")
}

fun <T> Flow<T>.distinct(): Flow<T> {
    val thisFlow = this
    return flow {
        val past = mutableSetOf<T>()
        thisFlow.collect {
            val isNew = past.add(it)
            if (isNew) emit(it)
        }
    }
}