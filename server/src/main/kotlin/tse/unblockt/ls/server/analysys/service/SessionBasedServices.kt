// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

@file:OptIn(ExperimentalLibraryAbiReader::class)

package tse.unblockt.ls.server.analysys.service

import com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModule
import org.jetbrains.kotlin.library.abi.ExperimentalLibraryAbiReader
import org.jetbrains.kotlin.library.abi.LibraryAbiReader
import org.jetbrains.kotlin.platform.CommonPlatforms
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.js.JsPlatforms
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms
import org.jetbrains.kotlin.platform.jvm.isJvm
import org.jetbrains.kotlin.platform.konan.NativePlatforms
import org.jetbrains.kotlin.platform.wasm.WasmPlatforms
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
import tse.unblockt.ls.server.project.Platform
import tse.unblockt.ls.server.project.UBDependency
import tse.unblockt.ls.server.project.UBModule
import tse.unblockt.ls.server.project.UBProjectModel
import tse.unblockt.ls.server.util.ServiceInformation
import java.nio.file.Path
import kotlin.io.path.extension

internal class SessionBasedServices(root: Path, storagePath: Path, globalStoragePath: Path, private val session: LsSession, private val model: UBProjectModel): LsServices {
    companion object {
        suspend fun create(
            root: Path,
            projectModel: UBProjectModel,
            storagePath: Path,
            globalStoragePath: Path,
        ): LsServices {
            val byName = projectModel.modules.associateBy {
                it.name
            }
            val graph = buildGradleProjectsGraph(projectModel.modules)
            val session = LsSession.build(projectModel) {
                this.storagePath = storagePath
                this.globalStoragePath = globalStoragePath
                buildProjectStructureProvider {
                    val cache = mutableMapOf<UBModule, KaModule>()
                    val moduleCache = mutableMapOf<UBDependency, KaModule>()
                    val sdkModule = makeJavaSdk(projectModel.javaHome)
                    report("building dependencies graph...")

                    for (prj in projectModel.modules) {
                        report("configuring ${prj.name} module...")
                        val module = traverseGradleProjects(graph, cache, prj) { project, possibleDependencies ->
                            makeModule(sdkModule, byName, project, possibleDependencies) { dep, func ->
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
            return SessionBasedServices(root, storagePath, globalStoragePath, session, projectModel)
        }

        fun buildGradleProjectsGraph(projects: List<UBModule>): Map<UBModule, List<UBModule>> {
            val projectsMap = projects.associateBy { it.name }
            val result = mutableMapOf<UBModule, MutableList<UBModule>>()
            for (project in projects) {
                val projectDeps = result.computeIfAbsent(project) {
                    mutableListOf()
                }
                val modules = project.dependencies.filterIsInstance<UBDependency.Module>()
                for (module in modules) {
                    projectDeps.add(projectsMap[module.name]!!)
                }
            }
            return result
        }

        private suspend fun LsSession.Builder.makeJavaSdk(javaHome: Path): KaModule {
            report("reading JDK...")

            return LsLibraryModuleBuilder(kotlinCoreProjectEnvironment).apply {
                libraryName = "JDK"
                platform = JvmPlatforms.defaultJvmPlatform
                // todo isJre
                addBinaryRootsFromJdkHome(javaHome, false)
            }.build(true)
        }

        private fun <T> traverseGradleProjects(projects: Map<UBModule, List<UBModule>>, cache: MutableMap<UBModule, T>, project: UBModule, transformer: (UBModule, Map<UBModule, T>) -> T): T {
            if (cache.containsKey(project)) {
                return cache[project]!!
            }

            val dependencies = projects[project]!!
            val transformed = mutableMapOf<UBModule, T>()
            for (dependency in dependencies) {
                transformed += dependency to traverseGradleProjects(projects, cache, dependency, transformer)
            }
            val created = transformer(project, transformed)
            cache[project] = created

            return created
        }

        private fun LsSession.Builder.makeModule(
            javaSdk: KaModule,
            projects: Map<String, UBModule>,
            project: UBModule,
            possibleDependencies: Map<UBModule, KaModule>,
            cache: (UBDependency, () -> KaModule) -> KaModule
        ): KaModule {
            val platformFromModule = platformFromPlatforms(project.platforms)
            return LsSourceModuleBuilder(kotlinCoreProjectEnvironment).apply {
                this.platform = platformFromModule
                moduleName = project.name
                val kotlinSources = project.path.resolve("kotlin")
                if (project.platforms.contains(Platform.jvm) || project.platforms.contains(Platform.androidJvm)) {
                    val javaSources = project.path.resolve("java")
                    addSourceRoot(javaSources)
                }
                addSourceRoot(kotlinSources)
                for (entry in project.dependencies) {
                    addRegularDependency(
                        when (entry) {
                            is UBDependency.Library -> makeLibrary(cache, entry, platformFromModule)
                            is UBDependency.Module -> {
                                possibleDependencies[projects[entry.name]!!]!!
                            }
                        }
                    )
                }
                if (platformFromModule.isJvm()) {
                    addRegularDependency(javaSdk)
                }
            }.build()
        }

        private fun platformFromPlatforms(platforms: Collection<Platform>): TargetPlatform {
            return when {
                platforms.isEmpty() -> JvmPlatforms.defaultJvmPlatform
                platforms.contains(Platform.common) -> CommonPlatforms.defaultCommonPlatform
                platforms.contains(Platform.jvm) || platforms.contains(Platform.androidJvm) -> JvmPlatforms.defaultJvmPlatform
                platforms.contains(Platform.native) -> NativePlatforms.unspecifiedNativePlatform
                platforms.contains(Platform.wasm) -> WasmPlatforms.unspecifiedWasmPlatform
                platforms.contains(Platform.js) -> JsPlatforms.defaultJsPlatform
                else -> throw IllegalArgumentException("Unsupported platform: ${platforms.joinToString { it.name }}")
            }
        }

        private fun LsSession.Builder.makeLibrary(
            cache: (UBDependency, () -> KaModule) -> KaModule,
            entry: UBDependency.Library,
            modulePlatform: TargetPlatform,
        ): KaModule {
            return cache(entry) {
                LsLibraryModuleBuilder(kotlinCoreProjectEnvironment).apply {
                    libraryName = entry.name
                    this.platform = platformFromPaths(modulePlatform, entry)
                    for (path in entry.paths) {
                        addBinaryRoot(path)
                    }
                }.build()
            }
        }

        private fun platformFromPaths(modulePlatform: TargetPlatform, entry: UBDependency.Library): TargetPlatform {
            if (entry.paths.isEmpty()) {
                return modulePlatform
            }
            val first = entry.paths.first()
            return when(first.extension) {
                "jar" -> JvmPlatforms.defaultJvmPlatform
                "aar" -> JvmPlatforms.defaultJvmPlatform
                "klib" -> {
                    try {
                        val readAbiInfo = LibraryAbiReader.readAbiInfo(first.toFile())
                        val platform = readAbiInfo.manifest.platform
                        if (platform == null) {
                            modulePlatform
                        } else {
                            platformFromPlatforms(listOf(Platform.valueOf(platform.lowercase())))
                        }
                    } catch (ignore: Throwable) {
                        modulePlatform
                    }
                }
                else -> modulePlatform
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

    override val projectModel: UBProjectModel
        get() = model

    override val health: HealthStatusInformation
        get() = persistentStorage.health()?.copy(text = memoryMessage()) ?: buildService.health?.copy(text = memoryMessage()) ?: HealthStatusInformation(
            text = memoryMessage(),
            message = "Click to see actions",
            status = HealthStatus.HEALTHY
        )

    override val serviceInformation: ServiceInformation = ServiceInformation(storagePath, globalStoragePath)

    override suspend fun cleanup() {
        PersistentStorage.instance(session.project).exclusively {
            deleteAll()
        }
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