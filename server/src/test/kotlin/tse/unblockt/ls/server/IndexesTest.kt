// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

package tse.unblockt.ls.server

import com.intellij.openapi.project.DefaultProjectFactory
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo
import org.junit.jupiter.api.assertDoesNotThrow
import tse.unblockt.ls.protocol.Document
import tse.unblockt.ls.protocol.JobWithProgressParams
import tse.unblockt.ls.protocol.SemanticTokensParams
import tse.unblockt.ls.protocol.Uri
import tse.unblockt.ls.server.analysys.storage.PersistentStorage
import tse.unblockt.ls.util.*
import java.nio.file.Paths
import kotlin.test.assertNotNull

class IndexesTest {
    @Test
    fun stubsAreRebuilt(info: TestInfo) = rkTest {
        init(testProjectPath)

        val defaultProject = DefaultProjectFactory.getInstance().defaultProject
        PersistentStorage.instance(defaultProject).clearCache()

        val semanticTokens = languageServer.textDocument.semanticTokensFull(
            SemanticTokensParams(
                Document(
                    Uri("file://$testProjectPath/src/main/java/tse/com/sample.kt")
                )
            )
        )

        val projected = projectTokens(Paths.get("$testProjectPath/src/main/java/tse/com/sample.kt"), semanticTokens)
        assertEqualsWithFile(projected, info)
    }

    @Test
    fun indexesAreReloaded(info: TestInfo) = rkTest {
        init(testProjectPath)
        val bs = languageServer.buildSystem
        assertNotNull(bs)

        assertDoesNotThrow {
            bs.reload(JobWithProgressParams(null))
        }
    }
}