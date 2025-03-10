// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

package tse.unblockt.ls.server.analysys.index.machines

import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.parentOrNull
import tse.unblockt.ls.server.analysys.index.common.IndexFileEntry
import tse.unblockt.ls.server.analysys.psi.packageName
import tse.unblockt.ls.server.analysys.storage.DB
import tse.unblockt.ls.server.analysys.storage.PersistentStorage

class JavaPackageIndexMachine : IndexMachine<String, Boolean>{
    companion object {
        private val ourAttribute = DB.Attribute(
            name = "java_package",
            metaToString = { it },
            stringToMeta = { it },
            keyToString = { it },
            valueToString = { it.toString() },
            stringToKey = { _, s -> s },
            stringToValue = { _, s -> s.toBoolean() },
        )
    }
    override val attribute: DB.Attribute<String, String, Boolean>
        get() = ourAttribute

    override val namespace: PersistentStorage.Namespace
        get() = Namespaces.ourJavaNamespace

    override fun index(entry: IndexFileEntry): List<Pair<String, Boolean>> {
        if (entry.isKotlin) {
            return emptyList()
        }

        val packageName = entry.psiFile.packageName ?: return emptyList()
        val fqName = FqName(packageName)
        val list = mutableListOf<String>()
        var current: FqName? = fqName
        while (current != null && !current.isRoot) {
            list.add(current.asString())
            current = current.parentOrNull()
        }
        return list.map { it to true }
    }
}