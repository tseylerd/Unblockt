// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

package tse.unblockt.ls.server

import com.intellij.openapi.project.DefaultProjectFactory
import org.junit.jupiter.api.BeforeEach
import tse.unblockt.ls.server.analysys.storage.DB
import tse.unblockt.ls.server.analysys.storage.PersistentStorage
import tse.unblockt.ls.util.fastProjectPath
import tse.unblockt.ls.util.init
import tse.unblockt.ls.util.rkTest
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class PersistentStorageTest {
    companion object {
        private val ourTestNamespace = PersistentStorage.Namespace("test")
        private val ourSimpleAttribute = DB.Attribute(
            name = "simple",
            metaToString = { it },
            stringToMeta = { it },
            keyToString = { it.toString() },
            valueToString = { it.toString() },
            stringToKey = { _, str -> str.toLong() },
            stringToValue = { _, v -> v.toInt() },
        )

        private val ourDuplicatedAttribute = ourSimpleAttribute.copy(name = "duplicated")
    }

    @OptIn(ExperimentalPathApi::class)
    @BeforeEach
    fun setup() {
        fastProjectPath.resolve(".unblockt").deleteRecursively()
    }

    @Test
    fun saveSingleEntry() = rkTest {
        init(fastProjectPath)

        val storage = PersistentStorage.instance(DefaultProjectFactory.getInstance().defaultProject)
        storage.put(ourTestNamespace, ourSimpleAttribute, "a", 1, 1)
        storage.put(ourTestNamespace, ourSimpleAttribute, "a", 2, 2)
        val allValues = storage.getSequence(ourTestNamespace, ourSimpleAttribute).toList()
        assertEquals(listOf(1L to 1, 2L to 2), allValues.sortedBy { it.first })
    }

    @Test
    fun saveDuplicates() = rkTest {
        init(fastProjectPath)

        val storage = PersistentStorage.instance(DefaultProjectFactory.getInstance().defaultProject)
        storage.put(ourTestNamespace, ourDuplicatedAttribute, "a", 1, 1)
        storage.put(ourTestNamespace, ourDuplicatedAttribute, "a", 1, 2)
        val allValues = storage.getSequence(ourTestNamespace, ourDuplicatedAttribute).toList()
        assertContentEquals(listOf(1L to 1, 1L to 2), allValues)
    }

    @Test
    fun deleteBySource() = rkTest {
        init(fastProjectPath)

        val storage = PersistentStorage.instance(DefaultProjectFactory.getInstance().defaultProject)
        storage.put(ourTestNamespace, ourSimpleAttribute, "file1", 1, 1)
        storage.put(ourTestNamespace, ourSimpleAttribute, "file2", 2, 2)

        val stored = storage.getSequence(ourTestNamespace, ourSimpleAttribute).toList()
        assertEquals(listOf(1L to 1, 2L to 2), stored.sortedBy { it.first })

        storage.delete(ourTestNamespace, ourSimpleAttribute, "file1")

        val after = storage.getSequence(ourTestNamespace, ourSimpleAttribute).toList()
        assertEquals(listOf(2L to 2), after)
    }
}