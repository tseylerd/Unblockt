// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

package tse.unblockt.ls.server

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer

object GlobalServerState {
    private val listeners = mutableListOf<Listener>()

    private fun subscribe(listener: Listener, disposable: Disposable) {
        listeners.add(listener)
        Disposer.register(disposable) {
            listeners.remove(listener)
        }
    }

    fun onInitialized(disposable: Disposable, initializer: suspend () -> Unit) {
        subscribe(object : Listener {
            override suspend fun onInitialized() {
                initializer()
            }

            override suspend fun onShutdown() {

            }
        }, disposable)
    }

    fun onShutdown(disposable: Disposable, handler: suspend () -> Unit) {
        subscribe(object : Listener {
            override suspend fun onInitialized() {
            }

            override suspend fun onShutdown() {
                handler()
            }
        }, disposable)
    }

    suspend fun initialized() {
        listeners.forEach { it.onInitialized() }
    }

    suspend fun shutdown() {
        val listenersCopy = listeners.toList()
        listenersCopy.forEach { it.onShutdown() }
    }

    interface Listener {
        suspend fun onInitialized()
        suspend fun onShutdown()
    }
}