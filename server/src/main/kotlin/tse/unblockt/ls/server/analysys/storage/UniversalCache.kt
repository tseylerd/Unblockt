// Copyright 2024 Dmitrii Tseiler. Licensed under the Apache 2.0 Licence

package tse.unblockt.ls.server.analysys.storage

import org.eclipse.collections.impl.map.mutable.ConcurrentHashMap
import kotlin.reflect.KClass

class UniversalCache(private val counter: () -> Long) {
    companion object {
        inline fun <reified T: Any> Key(name: String): Key<T> {
            return Key(name, T::class)
        }
    }

    private val map = ConcurrentHashMap<String, VersionedValue<Any>>()

    data class Key<T: Any>(val name: String, val clazz: KClass<T>)

    fun <T: Any> getOrCompute(key: Key<T>, computeFn: () -> T): T {
        return map.compute(key.name) { _, vv ->
            if (vv == null) {
                val curVersion = counter()
                VersionedValue(curVersion, computeFn())
            } else {
                val version = vv.version
                val curVersion = counter()
                if (version == curVersion) {
                    vv
                } else {
                    VersionedValue(curVersion, computeFn() as Any)
                }
            }
        }!!.value as T
    }

    private data class VersionedValue<T>(val version: Long, val value: T)
}