// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

package tse.unblockt.ls.server

import com.intellij.openapi.project.DefaultProjectFactory
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.kotlin.utils.mapToSetOrEmpty
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo
import org.junit.jupiter.api.assertDoesNotThrow
import tse.unblockt.ls.protocol.*
import tse.unblockt.ls.server.analysys.index.model.ProjectModelSetup
import tse.unblockt.ls.server.analysys.project.ProjectBuildModel
import tse.unblockt.ls.server.analysys.project.build.BuildManager
import tse.unblockt.ls.server.analysys.storage.PersistentStorage
import tse.unblockt.ls.server.fs.asPath
import tse.unblockt.ls.server.fs.uri
import tse.unblockt.ls.util.*
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.relativeTo
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BuildSystemTest {
    companion object {
        @OptIn(ExperimentalSerializationApi::class)
        private val json = Json {
            prettyPrint = true
            prettyPrintIndent = "  "
        }
    }

    @Test
    fun indexModelRead() = rkTest {
        init(testProjectPath)

        val project = DefaultProjectFactory.getInstance().defaultProject
        assertDoesNotThrow {
            PersistentStorage.instance(project).getSequence(ProjectModelSetup.namespace, ProjectModelSetup.entryAttribute).toList()
        }
    }

    @Test
    fun moduleImported() = rkTest {
        init(testProjectPath)

        val tokens = languageServer.textDocument.semanticTokensFull(
            SemanticTokensParams(
                Document(
                    uri = Uri("file://$testProjectPath/Submodule1/src/main/java/tse/com/example.kt"),
                )
            )
        )

        assertEquals(SemanticTokens(null, data = intArrayOf(0, 0, 7, 15, 0, 2, 0, 5, 15, 0, 0, 6, 3, 2, 0)), tokens)
    }

    @Test
    fun buildModelExists(info: TestInfo) = rkTest {
        init(testProjectPath)

        val buildModel = BuildManager.instance(project).getCurrentModel()
        assertNotNull(buildModel)
        assertEqualsWithFile(json.encodeToString(sanitizeBuildModel(testProjectPath, buildModel)), info)
    }

    @Test
    fun changeInBuildModelRecorded() = rkTest {
        init(testProjectPath)
        val pathToBuildFile = testProjectPath.resolve("build.gradle.kts")
        val contentBefore = Files.readString(pathToBuildFile)
        try {
            val buildFileUri = Uri("file://$pathToBuildFile")
            val newContent = """
                            plugins {
                                kotlin("jvm")
                            }

                            group = "org.example"
                            version = "1.0-SNAPSHOT"

                            repositories {
                                mavenCentral()
                            }

                            dependencies {
                                testImplementation(platform("org.junit:junit-bom:5.10.0"))
                                testImplementation("org.junit.jupiter:junit-jupiter")
                                implementation(kotlin("stdlib-jdk8"))
                                implementation("io.ktor:ktor-server-core:2.3.12")
                            }

                            tasks.test {
                                useJUnitPlatform()
                            }
                            kotlin {
                                jvmToolchain(22)
                            }
                        """.trimIndent()
            languageServer.textDocument.didOpen(DidOpenTextDocumentParams(TextDocumentItem(
                buildFileUri,
                "kotlin",
                4,
                contentBefore,
            )))
            languageServer.textDocument.didChange(
                DidChangeTextDocumentParams(
                    VersionedTextDocumentIdentifier(
                        uri = buildFileUri,
                        version = 4
                    ),
                    contentChanges = listOf(
                        TextDocumentContentChangeEvent(
                            newContent,
                            wholeText(contentBefore)
                        )
                    )
                )
            )
            Files.writeString(pathToBuildFile, newContent)
            languageServer.textDocument.didSave(
                DidSaveTextDocumentParams(
                    textDocument = TextDocumentIdentifier(buildFileUri)
                )
            )
            languageServer.service.pollAllRequests()
            val buildModel = BuildManager.instance(project).getCurrentModel()
            assertNotNull(buildModel)
            assertTrue { buildModel.changes.isNotEmpty() }

            languageServer.textDocument.didChange(
                DidChangeTextDocumentParams(
                    VersionedTextDocumentIdentifier(
                        uri = buildFileUri,
                        version = 4
                    ),
                    contentChanges = listOf(
                        TextDocumentContentChangeEvent(
                            contentBefore,
                            wholeText(newContent)
                        )
                    )
                )
            )
            Files.writeString(pathToBuildFile, contentBefore)
            languageServer.textDocument.didSave(DidSaveTextDocumentParams(
                textDocument = TextDocumentIdentifier(buildFileUri)
            ))
            languageServer.service.pollAllRequests()
            val buildModelAfter = BuildManager.instance(project).getCurrentModel()
            assertNotNull(buildModelAfter)
            assertTrue { buildModelAfter.changes.isEmpty() }
        } finally {
            Files.writeString(pathToBuildFile, contentBefore)
        }
    }

    private fun sanitizeBuildModel(root: Path, projectBuildModel: ProjectBuildModel): ProjectBuildModel {
        return projectBuildModel.copy(
            entries = projectBuildModel.entries.mapToSetOrEmpty { e ->
                e.copy(url = Uri(e.url).asPath().relativeTo(root).uri.data)
            },
            changes = projectBuildModel.changes.mapToSetOrEmpty { c ->
                c.copy(url = Uri(c.url).asPath().relativeTo(root).uri.data)
            }
        )
    }
}