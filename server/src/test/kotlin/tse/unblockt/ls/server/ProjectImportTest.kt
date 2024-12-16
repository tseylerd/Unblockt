// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

package tse.unblockt.ls.server

import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.platform.projectStructure.KotlinProjectStructureProvider
import org.jetbrains.kotlin.analysis.api.projectStructure.KaLibraryModule
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModule
import org.jetbrains.kotlin.analysis.api.projectStructure.KaSourceModule
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo
import org.junit.jupiter.api.assertDoesNotThrow
import tse.unblockt.ls.protocol.*
import tse.unblockt.ls.server.analysys.AnalysisEntrypoint
import tse.unblockt.ls.server.analysys.project.LsProjectStructureProvider
import tse.unblockt.ls.server.analysys.service.NoSessionServices
import tse.unblockt.ls.server.framework.simulateClient
import tse.unblockt.ls.server.fs.uri
import tse.unblockt.ls.server.project.UBDependency
import tse.unblockt.ls.server.project.UBModule
import tse.unblockt.ls.server.project.UBProjectModel
import tse.unblockt.ls.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ProjectImportTest {
    private companion object {
        @Suppress("OPT_IN_USAGE")
        private val json = Json {
            prettyPrint = true
            prettyPrintIndent = "  "
        }
    }

    @Test
    fun projectModelIsCorrect(info: TestInfo) = rkTest {
        init(testProjectPath)

        val projectModel = AnalysisEntrypoint.services.projectModel
        assertNotNull(projectModel)

        val spm = SerializableProjectModel.of(projectModel)
        assertEqualsWithFile(json.encodeToString(spm), info)
    }

    @Test
    fun projectModelIsCorrectForMultiplatform(info: TestInfo) = rkTest {
        init(testMultiplatformProjectPath)

        val projectModel = AnalysisEntrypoint.services.projectModel
        assertNotNull(projectModel)

        val spm = SerializableProjectModel.of(projectModel)
        assertEqualsWithFile(json.encodeToString(spm), info)
    }

    @Test
    fun projectModelIsCorrectForAndroid(info: TestInfo) = rkTest {
        init(testAndroidProjectPath)

        val projectModel = AnalysisEntrypoint.services.projectModel
        assertNotNull(projectModel)

        val spm = SerializableProjectModel.of(projectModel)
        assertEqualsWithFile(json.encodeToString(spm), info)
    }

    @OptIn(KaExperimentalApi::class)
    @Test
    fun projectStructureIsCorrect(info: TestInfo) = rkTest {
        init(testProjectPath)

        val psp = KotlinProjectStructureProvider.getInstance(project) as LsProjectStructureProvider
        val allKtModules = psp.allKtModules
        val spm = SerializableProjectModel(
            "",
            allKtModules.map { mod: KaModule ->
                SerializableGradleProject(
                    mod.moduleDescription,
                    "",
                    mod.directRegularDependencies.map { dep ->
                        when (dep) {
                            is KaSourceModule -> SerializableDependency.SerializableModule(
                                dep.name,
                                ""
                            )

                            is KaLibraryModule -> SerializableDependency.SerializableLibrary(
                                dep.libraryName,
                                emptyList()
                            )

                            else -> throw IllegalArgumentException()
                        }
                    },
                    "",
                    mod.targetPlatform.componentPlatforms.map { it.platformName }
                )
            },
            ""
        )
        assertEqualsWithFile(json.encodeToString(spm), info)
    }

    @Test
    fun importFailsWithoutGradle() = rkTest {
        init(emptyProjectPath)
        assertTrue { AnalysisEntrypoint.services is NoSessionServices }
    }

    @Test
    fun importFailsWithTypo(info: TestInfo) = rkTest {
        simulateClient(testProjectPath, info) {
            languageServer.buildSystem?.reload(JobWithProgressParams(null))
            val health = withTimeout(10000L) {
                clientsCallChannel.consumeAsFlow().first {
                    val data = it.data
                    it.method == "unblockt/status" && data is HealthStatusInformation && data.status == HealthStatus.ERROR
                }
            }
            val hsi = health.data as HealthStatusInformation
            assertEqualsWithFile(json.encodeToString(hsi.copy(text = "", message = "")), info)
        }
    }

    @Test
    fun projectImportedTwiceWorks() = rkTest {
        init(testProjectPath)

        assertDoesNotThrow {
            init(testProjectPath)
        }
    }

    @Test
    fun featuresWorkAfterRebuildingIndexes(info: TestInfo) = rkTest {
        simulateClient(testProjectPath, info) {
            languageServer.workspace.rebuildIndexes(JobWithProgressParams(null))
            diagnose()
        }
    }

    @Test
    fun modificationWorkAfterRebuildingIndexes(info: TestInfo) = rkTest {
        simulateClient(testProjectPath, info) {
            languageServer.workspace.rebuildIndexes(JobWithProgressParams(null))
            languageServer.textDocument.didOpen(DidOpenTextDocumentParams(TextDocumentItem(uri = fileToWorkWith.uri, languageId = "kotlin", version = 4, document().text)))
            type(0, "// my comment\n")
            diagnose()
        }
    }

    @Test
    fun inMemoryContentRemainsAfterReload(info: TestInfo) = rkTest {
        simulateClient(testProjectPath, info) {
            languageServer.textDocument.didOpen(DidOpenTextDocumentParams(TextDocumentItem(uri = fileToWorkWith.uri, languageId = "kotlin", version = 4, document().text)))
            type(0, "// new comment\n")

            val text = document().text
            assertTrue { text.startsWith("// new comment\n") }

            languageServer.buildSystem?.reload(JobWithProgressParams(null))
            val textAfterReload = document().text
            assertEquals(text, textAfterReload)
        }
    }

    @Serializable
    data class SerializableProjectModel(
        val path: String,
        val projects: List<SerializableGradleProject>,
        val javaHome: String
    ) {
        companion object {
            fun of(model: UBProjectModel): SerializableProjectModel {
                return SerializableProjectModel(
                    model.path.fileName.toString(),
                    model.modules.map { SerializableGradleProject.of(it) }.sortedBy { it.name },
                    model.javaHome.fileName.toString()
                )
            }
        }
    }

    @Serializable
    data class SerializableGradleProject(
        val name: String,
        val path: String,
        val dependencies: List<SerializableDependency>,
        val buildFile: String,
        val platforms: List<String>
    ) {
        companion object {
            fun of(prj: UBModule): SerializableGradleProject {
                return SerializableGradleProject(
                    prj.name,
                    prj.path.fileName.toString(),
                    prj.dependencies.map { SerializableDependency.of(it) }.sortedBy { it.name },
                    prj.buildFile.fileName.toString(),
                    prj.platforms.map { it.name }
                )
            }
        }
    }

    @Serializable
    sealed class SerializableDependency {
        companion object {
            fun of(dep: UBDependency): SerializableDependency {
                return when (dep) {
                    is UBDependency.Module -> SerializableModule(dep.name, dep.path.fileName.toString())
                    is UBDependency.Library -> SerializableLibrary(dep.name, dep.paths.map { it.fileName.toString() })
                }
            }
        }

        abstract val name: String

        @Serializable
        data class SerializableLibrary(override val name: String, val paths: List<String>) : SerializableDependency()
        @Serializable
        data class SerializableModule(override val name: String, val path: String) : SerializableDependency()
    }
}