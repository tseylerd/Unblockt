// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

package tse.unblockt.ls.server.analysys.index.machines

import com.intellij.openapi.project.Project
import tse.unblockt.ls.server.analysys.index.common.IndexFileEntry
import tse.unblockt.ls.server.analysys.storage.DB
import tse.unblockt.ls.server.analysys.storage.PersistentStorage

interface IndexMachine<K: Any, V: Any> {
    companion object {
        val ourMachines: (Project) -> List<IndexMachine<*, *>> = { project ->
            listOf(
                KtFileByFqNameIndexMachine(project),
                KtReceiverToExtensionIndexMachine(project),
                KtIdentifierToTypeAliasIndexMachine(project),
                KtCallableNameToTopLevelCallableIndexMachine(project),
                KtClassIdToClassIndexMachine(project),
                KtPackageToTopLevelFunctionIndexMachine(project),
                KtPackageToTopLevelPropertyIndexMachine(project),
                KtPackageToTopLevelClassIndexMachine(project),
                KtPackageToTopLevelCallableIndexMachine(project),
                JavaPackageToTopLevelClassIndexMachine(project),
                JavaClassIdToClassIndexMachine(project),
                JavaPackageIndexMachine(),
                KtPackageIndexMachine(),
            )
        }
    }
    val attribute: DB.Attribute<String, K, V>
    val namespace: PersistentStorage.Namespace

    fun index(entry: IndexFileEntry): List<Pair<K, V>>
}