// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

package tse.unblockt.ls.server.analysys.service

import com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModule
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms
import tse.unblockt.ls.protocol.HealthStatus
import tse.unblockt.ls.protocol.HealthStatusInformation
import tse.unblockt.ls.protocol.progress.report
import tse.unblockt.ls.server.analysys.LsSession
import tse.unblockt.ls.server.analysys.completion.LsCompletionMachine
import tse.unblockt.ls.server.analysys.completion.SessionBasedCompletionMachine
import tse.unblockt.ls.server.analysys.declaration.LsGoToDeclarationProvider
import tse.unblockt.ls.server.analysys.declaration.SessionBasedGoToDeclarationProvider
import tse.unblockt.ls.server.analysys.higlighting.LsHighlightingProvider
import tse.unblockt.ls.server.analysys.higlighting.SessionBasedHighlightingProvider
import tse.unblockt.ls.server.analysys.notifications.LsNotificationsService
import tse.unblockt.ls.server.analysys.notifications.SessionBasedNotificationsService
import tse.unblockt.ls.server.analysys.parameters.ParameterHintsService
import tse.unblockt.ls.server.analysys.parameters.SessionBasedParametersHintService
import tse.unblockt.ls.server.analysys.project.LsProjectService
import tse.unblockt.ls.server.analysys.project.SessionBasedProjectService
import tse.unblockt.ls.server.analysys.project.build.LsBuildService
import tse.unblockt.ls.server.analysys.project.build.SessionBasedBuildService
import tse.unblockt.ls.server.analysys.project.module.LsLibraryModuleBuilder
import tse.unblockt.ls.server.analysys.project.module.LsSourceModuleBuilder
import tse.unblockt.ls.server.analysys.storage.PersistentStorage
import tse.unblockt.ls.server.fs.GlobalFileState
import tse.unblockt.ls.server.fs.LsFileManager
import tse.unblockt.ls.server.fs.SessionBasedFileManager
import tse.unblockt.ls.server.project.Dependency
import tse.unblockt.ls.server.project.GradleProject
import tse.unblockt.ls.server.project.ProjectModel
import tse.unblockt.ls.server.util.ServiceInformation
import java.nio.file.Path

internal class SessionBasedServices(root: Path, storagePath: Path, private val session: LsSession, private val model: ProjectModel): LsServices {
    companion object {
        suspend fun create(
            root: Path,
            projectModel: ProjectModel,
            storagePath: Path,
        ): LsServices {
            val byName = projectModel.projects.associateBy {
                it.name
            }
            val graph = buildGradleProjectsGraph(projectModel.projects)
            // TODO: read platform from gradle
            val platform = JvmPlatforms.defaultJvmPlatform

            val session = LsSession.build(projectModel) {
                this.storagePath = storagePath
                buildProjectStructureProvider {
                    val cache = mutableMapOf<GradleProject, KaModule>()
                    val moduleCache = mutableMapOf<Dependency, KaModule>()
                    val sdkModule = makeJavaSdk(projectModel.javaHome)
                    report("Building dependencies graph...")

                    for (prj in projectModel.projects) {
                        report("Configuring ${prj.name} module...")
                        val module = traverseGradleProjects(graph, cache, prj) { project, possibleDependencies ->
                            makeModule(sdkModule, byName, project, platform, possibleDependencies) { dep, func ->
                                moduleCache.computeIfAbsent(dep) {
                                    val result = func()
                                    addModule(result)
                                    result
                                }
                            }
                        }
                        addModule(module)
                    }
                    addModule(sdkModule)
                }
            }
            return SessionBasedServices(root, storagePath, session, projectModel)
        }

        fun buildGradleProjectsGraph(projects: List<GradleProject>): Map<GradleProject, List<GradleProject>> {
            val projectsMap = projects.associateBy { it.name }
            val result = mutableMapOf<GradleProject, MutableList<GradleProject>>()
            for (project in projects) {
                val projectDeps = result.computeIfAbsent(project) {
                    mutableListOf()
                }
                val modules = project.dependencies.filterIsInstance<Dependency.Module>()
                for (module in modules) {
                    projectDeps.add(projectsMap[module.name]!!)
                }
            }
            return result
        }

        private suspend fun LsSession.Builder.makeJavaSdk(javaHome: Path): KaModule {
            report("Reading JDK...")

            return LsLibraryModuleBuilder(kotlinCoreProjectEnvironment).apply {
                libraryName = "JDK"
                platform = JvmPlatforms.defaultJvmPlatform
                // todo isJre
                addBinaryRootsFromJdkHome(javaHome, false)
            }.build(true)
        }

        private fun <T> traverseGradleProjects(projects: Map<GradleProject, List<GradleProject>>, cache: MutableMap<GradleProject, T>, project: GradleProject, transformer: (GradleProject, Map<GradleProject, T>) -> T): T {
            if (cache.containsKey(project)) {
                return cache[project]!!
            }

            val dependencies = projects[project]!!
            val transformed = mutableMapOf<GradleProject, T>()
            for (dependency in dependencies) {
                transformed += dependency to traverseGradleProjects(projects, cache, dependency, transformer)
            }
            val created = transformer(project, transformed)
            cache[project] = created

            return created
        }

        private fun LsSession.Builder.makeModule(
            javaSdk: KaModule,
            projects: Map<String, GradleProject>,
            project: GradleProject,
            platform: TargetPlatform,
            possibleDependencies: Map<GradleProject, KaModule>,
            cache: (Dependency, () -> KaModule) -> KaModule
        ): KaModule {
            return LsSourceModuleBuilder(kotlinCoreProjectEnvironment).apply {
                this.platform = platform
                moduleName = project.name
                val kotlinSources = project.path.resolve("kotlin")
                val javaSources = project.path.resolve("java")
                addSourceRoot(kotlinSources)
                addSourceRoot(javaSources)
                for (entry in project.dependencies) {
                    addRegularDependency(
                        when (entry) {
                            is Dependency.Library -> makeLibrary(cache, entry, platform)
                            is Dependency.Module -> {
                                possibleDependencies[projects[entry.name]!!]!!
                            }
                        }
                    )
                }
                addRegularDependency(javaSdk)
            }.build()
        }

        private fun LsSession.Builder.makeLibrary(
            cache: (Dependency, () -> KaModule) -> KaModule,
            entry: Dependency.Library,
            platform: TargetPlatform
        ): KaModule {
            return cache(entry) {
                LsLibraryModuleBuilder(kotlinCoreProjectEnvironment).apply {
                    libraryName = entry.name
                    this.platform = platform
                    for (path in entry.paths) {
                        addBinaryRoot(path)
                    }
                }.build()
            }
        }
    }

    override val filesManager: LsFileManager = SessionBasedFileManager(session)
    override val goToDeclarationProvider: LsGoToDeclarationProvider = SessionBasedGoToDeclarationProvider(session, filesManager)
    override val completionMachine: LsCompletionMachine = SessionBasedCompletionMachine(session, filesManager)
    override val highlightingProvider: LsHighlightingProvider = SessionBasedHighlightingProvider(filesManager)
    override val buildService: LsBuildService = SessionBasedBuildService(root, session.project)
    override val notificationsService: LsNotificationsService = SessionBasedNotificationsService(session.project, filesManager)
    override val parameterHintsService: ParameterHintsService = SessionBasedParametersHintService(filesManager)
    override val projectService: LsProjectService = SessionBasedProjectService

    override val projectModel: ProjectModel
        get() = model

    override val health: HealthStatusInformation
        get() = persistentStorage.health()?.copy(text = memoryMessage()) ?: buildService.health?.copy(text = memoryMessage()) ?: HealthStatusInformation(
            text = memoryMessage(),
            message = "Click to see actions",
            status = HealthStatus.HEALTHY
        )

    override val serviceInformation: ServiceInformation = ServiceInformation(storagePath)

    override suspend fun onInitialized() {
    }

    override suspend fun cleanup() {
        PersistentStorage.instance(session.project).deleteAll()
    }

    private val persistentStorage: PersistentStorage = PersistentStorage.instance(session.project)

    init {
        Disposer.register(this, session)
        GlobalFileState.init(session.project)
    }

    override fun dispose() {
        PersistentStorage.instance(session.project).shutdown()
    }
}