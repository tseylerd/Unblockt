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
import org.jetbrains.kotlin.analysis.api.projectStructure.KaSourceModule
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo
import org.junit.jupiter.api.assertDoesNotThrow
import tse.unblockt.ls.protocol.HealthStatus
import tse.unblockt.ls.protocol.HealthStatusInformation
import tse.unblockt.ls.protocol.JobWithProgressParams
import tse.unblockt.ls.server.analysys.AnalysisEntrypoint
import tse.unblockt.ls.server.analysys.project.LsProjectStructureProvider
import tse.unblockt.ls.server.analysys.service.NoSessionServices
import tse.unblockt.ls.server.framework.simulateClient
import tse.unblockt.ls.server.project.Dependency
import tse.unblockt.ls.server.project.GradleProject
import tse.unblockt.ls.server.project.ProjectModel
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

    @OptIn(KaExperimentalApi::class)
    @Test
    fun projectStructureIsCorrect(info: TestInfo) = rkTest {
        init(testProjectPath)

        val psp = KotlinProjectStructureProvider.getInstance(project) as LsProjectStructureProvider
        val allKtModules = psp.allKtModules
        val spm = SerializableProjectModel(
            "",
            allKtModules.map { mod ->
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
                    ""
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
                    it.method == "unblockt/status" && data is HealthStatusInformation && data.status == HealthStatus.MESSAGE
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
            type(0, "// my comment\n")
            diagnose()
        }
    }

    @Test
    fun inMemoryContentRemainsAfterReload(info: TestInfo) = rkTest {
        simulateClient(testProjectPath, info) {
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
            fun of(model: ProjectModel): SerializableProjectModel {
                return SerializableProjectModel(
                    model.path.fileName.toString(),
                    model.projects.map { SerializableGradleProject.of(it) }.sortedBy { it.name },
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
    ) {
        companion object {
            fun of(prj: GradleProject): SerializableGradleProject {
                return SerializableGradleProject(
                    prj.name,
                    prj.path.fileName.toString(),
                    prj.dependencies.map { SerializableDependency.of(it) }.sortedBy { it.name },
                    prj.buildFile.fileName.toString()
                )
            }
        }
    }

    @Serializable
    sealed class SerializableDependency {
        companion object {
            fun of(dep: Dependency): SerializableDependency {
                return when (dep) {
                    is Dependency.Module -> SerializableModule(dep.name, dep.path.fileName.toString())
                    is Dependency.Library -> SerializableLibrary(dep.name, dep.paths.map { it.fileName.toString() })
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