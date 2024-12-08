// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

package tse.unblockt.ls.server.threading

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.CoroutineContext

interface Cancellable {
    companion object {
        context(CoroutineScope)
        operator fun invoke(): Cancellable {
            return CScopeCancellable(this@CoroutineScope)
        }

        context(CoroutineContext)
        operator fun invoke(): Cancellable {
            return CurrentContext(this@CoroutineContext)
        }

        operator fun invoke(vararg all: Cancellable): Cancellable {
            return Composite(all.toList())
        }
    }

    fun cancellationPoint()

    class CScopeCancellable(private val scope: CoroutineScope): Cancellable {
        override fun cancellationPoint() {
            scope.ensureActive()
        }
    }

    class CurrentContext(private val context: CoroutineContext): Cancellable {
        override fun cancellationPoint() {
            context.ensureActive()
        }
    }

    class Composite(private val all: Collection<Cancellable>): Cancellable {
        override fun cancellationPoint() {
            for (cancellable in all) {
                cancellable.cancellationPoint()
            }
        }
    }
}