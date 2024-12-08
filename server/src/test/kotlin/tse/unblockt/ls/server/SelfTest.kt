// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

package tse.unblockt.ls.server

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo
import org.junit.jupiter.api.assertDoesNotThrow
import tse.unblockt.ls.protocol.*
import tse.unblockt.ls.server.framework.simulateClient
import tse.unblockt.ls.server.fs.uri
import tse.unblockt.ls.util.init
import tse.unblockt.ls.util.rkTest
import tse.unblockt.ls.util.selfPath
import java.nio.file.Files
import java.nio.file.Paths

class SelfTest {
    @Test
    fun importSelf() {
        rkTest {
            init(selfPath)
        }
    }

    @Test
    fun virtualFileExistsForTestSuite() {
        rkTest {
            init(selfPath)

            assertDoesNotThrow {
                languageServer.textDocument.diagnostic(DiagnosticTextDocumentParams(
                    textDocument = TextDocumentIdentifier(
                        uri = Paths.get("$selfPath/server/src/test/kotlin/tse/unblockt/ls/server/BuildSystemTest.kt").uri
                    )
                ))
            }
        }
    }

    @Test
    fun loadDiagnosticsForDB() {
        rkTest {
            init(selfPath)

            assertDoesNotThrow {
                languageServer.textDocument.diagnostic(DiagnosticTextDocumentParams(
                    textDocument = TextDocumentIdentifier(
                        uri = Paths.get("$selfPath/server/src/main/kotlin/tse/unblockt/ls/server/analysys/storage/DB.kt").uri
                    )
                ))
            }
        }
    }

    @Test
    fun inconsistencyInCacheDoesntHappen() {
        rkTest {
            init(selfPath)

            assertDoesNotThrow {
                languageServer.textDocument.diagnostic(DiagnosticTextDocumentParams(
                    textDocument = TextDocumentIdentifier(
                        uri = Paths.get("$selfPath/server/src/main/kotlin/tse/unblockt/ls/server/analysys/storage/PersistentStorage.kt").uri
                    )
                ))
            }
        }
    }

    @Test
    fun didOpenBigFile() {
        rkTest {
            init(selfPath)

            languageServer.textDocument.didOpen(DidOpenTextDocumentParams(
                textDocument = TextDocumentItem(
                    uri = Uri("file:///$selfPath/server/src/main/kotlin/tse/unblockt/ls/server/analysys/LsSession.kt"),
                    languageId = "kotlin",
                    version = 1,
                    text = Files.readString(Paths.get("$selfPath/server/src/main/kotlin/tse/unblockt/ls/server/analysys/LsSession.kt"))
                )
            ))
        }
    }

    @Test
    fun mainReferenceAutoImported(info: TestInfo) {
        rkTest {
            simulateClient(selfPath, info) {
                complete()
            }
        }
    }

    @Test
    fun contextAutoImported(info: TestInfo) {
        rkTest {
            simulateClient(selfPath, info) {
                complete {
                    it.first { item -> item.insertText.equals("tse.unblockt.ls.server.threading.Cancellable") }
                }
            }
        }
    }
}