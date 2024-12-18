// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

package tse.unblockt.ls.server

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo
import tse.unblockt.ls.protocol.*
import tse.unblockt.ls.server.framework.simulateClient
import tse.unblockt.ls.server.fs.asPath
import tse.unblockt.ls.server.fs.uri
import tse.unblockt.ls.util.*
import java.nio.file.Files

class ChangesTest {
    @Test
    fun incrementalChangesWork(info: TestInfo) = rkTest {
        init(testProjectPath)

        val uri = Uri("file://$testProjectPath/src/main/java/tse/com/test.kt")
        languageServer.textDocument.didOpen(DidOpenTextDocumentParams(TextDocumentItem(uri = uri, languageId = "kotlin", 4, Files.readString(uri.asPath()))))
        languageServer.textDocument.didChange(
            DidChangeTextDocumentParams(
                VersionedTextDocumentIdentifier(
                    uri = uri, version = 4
                ), contentChanges = listOf(
                    TextDocumentContentChangeEvent(
                        "",
                        Range(Position(4, 19), Position(4, 34)),
                    )
                )
            )
        )

        val report = languageServer.textDocument.diagnostic(
            DiagnosticTextDocumentParams(
                TextDocumentIdentifier(
                    uri
                )
            )
        )

        val content = """
            package tse.com

            fun main(args: Array<String>) {
                val entry = SomeEnum.FIRST
                val second = Se
            }

            suspend fun foo() {
                run { }
            }
        """.trimIndent()
        val projected = projectDiagnostics(content, report)
        assertEqualsWithFile(projected, info)
    }

    @Test
    fun lineMovedCorrectly(info: TestInfo) = rkTest {
        simulateClient(testProjectPath, info) {
            val uri = fileToWorkWith.uri
            languageServer.textDocument.didOpen(DidOpenTextDocumentParams(TextDocumentItem(uri = uri, languageId = "kotlin", 4, Files.readString(uri.asPath()))))
            languageServer.textDocument.didChange(DidChangeTextDocumentParams(
                VersionedTextDocumentIdentifier(uri, version = 1),
                contentChanges = listOf(
                    TextDocumentContentChangeEvent(text="", range=Range(start=Position(line=2, character=13), end=Position(line=3, character=12))),
                    TextDocumentContentChangeEvent(text="    ", range=Range(start=Position(line=2, character=0), end=Position(line=2, character=0))),
                    TextDocumentContentChangeEvent(text="fun main() {\n", range=Range(start=Position(line=2, character=0), end=Position(line=2, character=0)))
                )
            ))
            languageServer.service.pollAllRequests()
            assertContentIsExpected()
        }
    }
}