// Copyright 2024 Dmitrii Tseiler. Licensed under the Apache 2.0 Licence

package tse.unblockt.ls.server

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo
import tse.unblockt.ls.protocol.*
import tse.unblockt.ls.server.framework.simulateClient
import tse.unblockt.ls.server.fs.uri
import tse.unblockt.ls.util.*
import java.nio.file.Paths
import kotlin.io.path.readText

class HighlightingTest {
    @Test
    fun fileHighlighting2(info: TestInfo) = rkTest {
        init(testProjectPath)

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
    fun extensionFunctionResolved(info: TestInfo) = rkTest {
        simulateClient(testProjectPath, info) {
            diagnose()
        }
    }

    @Test
    fun fileDeletionNoticed(info: TestInfo) = rkTest {
        simulateClient(testProjectPath, info) {
            delete(testProjectPath.resolve("src/main/java/tse/com/sample.kt"))
            diagnose()
        }
    }

    @Test
    fun folderDeletionNoticed(info: TestInfo) = rkTest {
        simulateClient(testProjectPath, info) {
            delete(testProjectPath.resolve("src/main/java/tse/com/root"))
            diagnose()
        }
    }

    @Test
    fun rangeTokens(info: TestInfo) = rkTest {
        simulateClient(testProjectPath, info) {
            highlightOnRange()
        }
    }

    @Test
    fun registerServices(info: TestInfo) {
        rkTest {
            simulateClient(selfPath, info) {
                diagnose()
            }
        }
    }

    @Test
    fun manyParametersOverloads(info: TestInfo) = rkTest {
        simulateClient(testProjectPath, info) {
            diagnose()
        }
    }

    @Test
    fun testFileIsResolved(info: TestInfo) = rkTest {
        simulateClient(testProjectPath, info) {
            diagnose()
        }
    }

    @Test
    fun wrongTypeHighlighted(info: TestInfo) = rkTest {
        simulateClient(testProjectPath, info) {
            diagnose()
        }
    }

    @Test
    fun packageTypedWithoutException(info: TestInfo) = rkTest {
        simulateClient(emptyProjectWithGradleWithJVMPath, info, true) {
            val text = "package tse"
            languageServer.textDocument.didOpen(DidOpenTextDocumentParams(TextDocumentItem(fileToWorkWith.uri, "kotlin", 4, fileToWorkWith.readText())))
            type(0, text)
            diagnostics(fileToWorkWith)
            type(text.length, ".com")
            diagnose()
        }
    }
}