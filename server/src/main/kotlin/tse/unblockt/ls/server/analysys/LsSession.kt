// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

@file:Suppress("UnstableApiUsage")
@file:OptIn(KaImplementationDetail::class, KaExperimentalApi::class)

package tse.unblockt.ls.server.analysys

import com.intellij.codeInsight.ExternalAnnotationsManager
import com.intellij.codeInsight.InferredAnnotationsManager
import com.intellij.core.CoreApplicationEnvironment
import com.intellij.core.CoreJavaFileManager
import com.intellij.core.CorePackageIndex
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.mock.MockApplication
import com.intellij.mock.MockProject
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.impl.DocumentWriteAccessGuard
import com.intellij.openapi.extensions.ExtensionPoint
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.extensions.impl.ExtensionsAreaImpl
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileTypes.FileTypeExtensionPoint
import com.intellij.openapi.project.DefaultProjectFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.PackageIndex
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.pom.PomModel
import com.intellij.pom.core.impl.PomModelImpl
import com.intellij.pom.tree.TreeAspect
import com.intellij.psi.*
import com.intellij.psi.impl.BlockSupportImpl
import com.intellij.psi.impl.file.impl.JavaFileManager
import com.intellij.psi.impl.smartPointers.PsiClassReferenceTypePointerFactory
import com.intellij.psi.impl.smartPointers.SmartPointerManagerImpl
import com.intellij.psi.impl.smartPointers.SmartTypePointerManagerImpl
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.text.BlockSupport
import com.intellij.util.messages.ListenerDescriptor
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaImplementationDetail
import org.jetbrains.kotlin.analysis.api.impl.base.java.source.JavaElementSourceWithSmartPointerFactory
import org.jetbrains.kotlin.analysis.api.platform.KotlinMessageBusProvider
import org.jetbrains.kotlin.analysis.api.platform.KotlinProjectMessageBusProvider
import org.jetbrains.kotlin.analysis.api.platform.declarations.KotlinAnnotationsResolverFactory
import org.jetbrains.kotlin.analysis.api.platform.declarations.KotlinDeclarationProviderFactory
import org.jetbrains.kotlin.analysis.api.platform.declarations.KotlinDeclarationProviderMerger
import org.jetbrains.kotlin.analysis.api.platform.declarations.KotlinDirectInheritorsProvider
import org.jetbrains.kotlin.analysis.api.platform.lifetime.KaLifetimeTracker
import org.jetbrains.kotlin.analysis.api.platform.modification.KotlinGlobalModificationService
import org.jetbrains.kotlin.analysis.api.platform.modification.KotlinModificationTrackerFactory
import org.jetbrains.kotlin.analysis.api.platform.packages.KotlinPackagePartProviderFactory
import org.jetbrains.kotlin.analysis.api.platform.packages.KotlinPackageProviderFactory
import org.jetbrains.kotlin.analysis.api.platform.packages.KotlinPackageProviderMerger
import org.jetbrains.kotlin.analysis.api.platform.projectStructure.KotlinByModulesResolutionScopeProvider
import org.jetbrains.kotlin.analysis.api.platform.projectStructure.KotlinModuleDependentsProvider
import org.jetbrains.kotlin.analysis.api.platform.projectStructure.KotlinProjectStructureProvider
import org.jetbrains.kotlin.analysis.api.platform.projectStructure.KotlinResolutionScopeProvider
import org.jetbrains.kotlin.analysis.api.resolve.extensions.KaResolveExtensionProvider
import org.jetbrains.kotlin.analysis.api.standalone.base.declarations.KotlinFakeClsStubsCache
import org.jetbrains.kotlin.analysis.api.standalone.base.declarations.KotlinStandaloneAnnotationsResolverFactory
import org.jetbrains.kotlin.analysis.api.standalone.base.modification.KotlinStandaloneGlobalModificationService
import org.jetbrains.kotlin.analysis.api.standalone.base.modification.KotlinStandaloneModificationTrackerFactory
import org.jetbrains.kotlin.analysis.api.standalone.base.projectStructure.ApplicationServiceRegistration
import org.jetbrains.kotlin.analysis.api.standalone.base.projectStructure.FirStandaloneServiceRegistrar
import org.jetbrains.kotlin.analysis.api.standalone.base.projectStructure.FirStandaloneServiceRegistrar.registerApplicationServicesWithCustomClassLoader
import org.jetbrains.kotlin.analysis.api.standalone.base.projectStructure.KtStaticModuleDependentsProvider
import org.jetbrains.kotlin.analysis.api.standalone.base.projectStructure.PluginStructureProvider
import org.jetbrains.kotlin.analysis.api.standalone.base.projectStructure.StandaloneProjectFactory.createPackagePartsProvider
import org.jetbrains.kotlin.analysis.api.standalone.base.projectStructure.StandaloneProjectFactory.getAllBinaryRoots
import org.jetbrains.kotlin.analysis.api.symbols.AdditionalKDocResolutionProvider
import org.jetbrains.kotlin.analysis.decompiler.konan.KlibMetaFileType
import org.jetbrains.kotlin.analysis.decompiler.psi.BuiltinsVirtualFileProvider
import org.jetbrains.kotlin.analysis.decompiler.psi.BuiltinsVirtualFileProviderCliImpl
import org.jetbrains.kotlin.analysis.decompiler.stub.file.ClsKotlinBinaryClassCache
import org.jetbrains.kotlin.analysis.decompiler.stub.file.DummyFileAttributeService
import org.jetbrains.kotlin.analysis.decompiler.stub.file.FileAttributeService
import org.jetbrains.kotlin.cli.jvm.compiler.*
import org.jetbrains.kotlin.cli.jvm.index.JavaRoot
import org.jetbrains.kotlin.cli.jvm.index.JvmDependenciesDynamicCompoundIndex
import org.jetbrains.kotlin.cli.jvm.index.JvmDependenciesIndexImpl
import org.jetbrains.kotlin.cli.jvm.modules.CliJavaModuleFinder
import org.jetbrains.kotlin.cli.jvm.modules.CliJavaModuleResolver
import org.jetbrains.kotlin.cli.jvm.modules.JavaModuleGraph
import org.jetbrains.kotlin.config.*
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.load.java.structure.impl.source.JavaElementSourceFactory
import org.jetbrains.kotlin.load.kotlin.MetadataFinderFactory
import org.jetbrains.kotlin.load.kotlin.PackagePartProvider
import org.jetbrains.kotlin.load.kotlin.VirtualFileFinderFactory
import org.jetbrains.kotlin.resolve.jvm.KotlinCliJavaFileManager
import org.jetbrains.kotlin.resolve.jvm.KotlinJavaPsiFacade
import org.jetbrains.kotlin.resolve.jvm.modules.JavaModuleResolver
import org.picocontainer.PicoContainer
import tse.unblockt.ls.protocol.Uri
import tse.unblockt.ls.protocol.progress.report
import tse.unblockt.ls.server.analysys.files.isFolder
import tse.unblockt.ls.server.analysys.index.*
import tse.unblockt.ls.server.analysys.index.stub.IndexModel
import tse.unblockt.ls.server.analysys.index.stub.LanguageMachinery
import tse.unblockt.ls.server.analysys.navigation.*
import tse.unblockt.ls.server.analysys.project.LsDefaultProjectFactory
import tse.unblockt.ls.server.analysys.project.LsProjectStructureProvider
import tse.unblockt.ls.server.analysys.project.ProjectStructureManager
import tse.unblockt.ls.server.analysys.project.build.BuildManager
import tse.unblockt.ls.server.analysys.project.module.LsSourceModule
import tse.unblockt.ls.server.analysys.storage.PersistentStorage
import tse.unblockt.ls.server.fs.LsFileSystem
import tse.unblockt.ls.server.project.UBProjectModel
import java.net.URI
import java.nio.file.FileSystems
import java.nio.file.Path
import java.util.*
import kotlin.io.path.toPath
import kotlin.reflect.KMutableProperty
import kotlin.reflect.full.declaredMembers
import kotlin.reflect.jvm.isAccessible

private val LsProjectStructureProvider.allFiles: List<VirtualFile>
    get() {
        return allKtModules.filterIsInstance<LsSourceModule>().flatMap { m ->
            val scope = m.contentScope
            scope.roots.collectChildren()
        }
    }

private fun Set<VirtualFile>.collectChildren(): List<VirtualFile> {
    return buildList {
        for (root in this@collectChildren) {
            val files = when {
                root.isDirectory -> root.children.toSet().collectChildren()
                root.fileType == KotlinFileType.INSTANCE -> listOf(root)
                else -> emptyList()
            }
            for (file in files) {
                add(file)
            }
        }
    }
}

internal class LsSession(
    private val kotlinCoreProjectEnvironment: KotlinCoreProjectEnvironment,
): Disposable {
    companion object {
        val DOCUMENT_KEY = Key.create<Document>("unblockt.document.key")

        suspend fun build(model: UBProjectModel, init: suspend Builder.() -> Unit): LsSession {
            val builder = Builder(model)
            builder.init()
            return builder.build()
        }
    }

    init {
        Disposer.register(this, kotlinCoreProjectEnvironment.parentDisposable)
        LsListeners.instance(project).listen(object : LsListeners.FileStateListener {
            override suspend fun created(uri: Uri) {
                if (!uri.isFolder) {
                    return
                }
                val virtualFile = LsFileSystem.instance().getVirtualFile(uri)
                if (virtualFile != null && virtualFile.isDirectory) {
                    kotlinCoreProjectEnvironment.addSourcesToClasspath(virtualFile)
                }
            }
        })
    }

    val project: Project
        get() = kotlinCoreProjectEnvironment.project

    class Builder(private val model: UBProjectModel) {
        lateinit var storagePath: Path
        lateinit var globalStoragePath: Path

        private val projectDisposable: Disposable = Disposer.newDisposable()

        internal val kotlinCoreProjectEnvironment = createProjectEnvironment(
            projectDisposable,
            classLoader = MockProject::class.java.classLoader
        )

        init {
            kotlinCoreProjectEnvironment.project.registerService(LsListeners::class.java, LsListeners())
        }

        private val project: MockProject
            get() = kotlinCoreProjectEnvironment.project

        private lateinit var provider: LsProjectStructureProvider
        private lateinit var declarationProviderFactory: LsKotlinDeclarationProviderFactory
        private lateinit var packageProviderFactory: IndexBasedKotlinPackageProviderFactory

        internal suspend fun buildProjectStructureProvider(builder: suspend LsProjectStructureProvider.Builder.() -> Unit) {
            provider = LsProjectStructureProvider.Builder(project).apply {
                builder()
            }.build()
        }

        suspend fun build(): LsSession {
            val lsDefaultProjectFactory = ApplicationManager.getApplication().getService(DefaultProjectFactory::class.java) as LsDefaultProjectFactory
            lsDefaultProjectFactory.project = project

            project.registerService(LsStubCache::class.java, LsStubCache::class.java)
            project.registerService(LsPsiCache::class.java, LsPsiCache::class.java)
            project.registerService(LsKotlinPsiIndex::class.java, LsKotlinPsiIndexImpl::class.java)
            project.registerService(LsJavaPsiIndex::class.java, LsJavaPsiIndexImpl::class.java)
            project.registerService(LsSourceCodeIndexer::class.java, LsSourceCodeIndexerImpl::class.java)
            project.registerService(ProjectStructureManager::class.java, ProjectStructureManager(model.path))
            project.registerService(LanguageMachinery.Kotlin::class.java, LanguageMachinery.Kotlin(project))
            project.registerService(LanguageMachinery.Java::class.java, LanguageMachinery.Java(project))
            project.registerService(BuildManager::class.java, BuildManager::class.java)

            val libraryRoots: List<JavaRoot> = getAllBinaryRoots(
                provider.allKtModules,
                kotlinCoreProjectEnvironment,
            )
            val indexer = LsSourceCodeIndexer.instance(project)
            val libRoots = libraryRoots.map { it.file }
            val builtins = indexer.builtins
            val ps = PersistentStorage.create(
                storagePath,
                globalStoragePath,
                project,
                model.path,
                (libRoots + builtins).map { it.url }.toSet()
            )
            Disposer.register(projectDisposable, ps)

            project.registerService(PersistentStorage::class.java, ps)

            registerServicesForProjectEnvironment(
                kotlinCoreProjectEnvironment,
                jdkHome = model.javaHome,
            )
            declarationProviderFactory = LsKotlinDeclarationProviderFactory(project)

            packageProviderFactory = IndexBasedKotlinPackageProviderFactory(project)

            val fileSystem = LsFileSystem.instance()
            val indexModel = IndexModel(
                provider.allFiles.map { IndexModel.Entry(it.url, IndexModel.EntryProperties(fileSystem.getModificationStamp(it),
                    builtIns = false,
                    stub = false
                )) }.toSet() +
                libRoots.map {
                    IndexModel.Entry(it.url, IndexModel.EntryProperties(fileSystem.getModificationStamp(it), false, stub = true))
                } + builtins.map {
                    IndexModel.Entry(it.url, IndexModel.EntryProperties(fileSystem.getModificationStamp(it), true, stub = true))
                }
            )
            registerProjectServices(
                packageProviderFactory,
                createPackagePartsProvider(libraryRoots),
            )

            report("updating indexes...")
            indexer.updateIndexes(indexModel)
            BuildManager.instance(project).indexBuildModel(model)


            CoreApplicationEnvironment.registerExtensionPoint(
                project.extensionArea, PsiTreeChangeListener.EP.name, PsiTreeChangeAdapter::class.java
            )

            KaLifetimeTracker.getInstance(project) // fix races with cached services
            project.registerService(KotlinDirectInheritorsProvider::class.java, LsKotlinDirectInheritorsProvider::class.java)

            return LsSession(kotlinCoreProjectEnvironment)
        }

        private fun registerProjectServices(
            packageProviderFactory: IndexBasedKotlinPackageProviderFactory,
            packagePartProvider: (GlobalSearchScope) -> PackagePartProvider,
        ) {
            val project = kotlinCoreProjectEnvironment.project
            registrars.forEach { it.registerProjectServices(project) }
            registrars.forEach { it.registerProjectExtensionPoints(project) }
            registrars.forEach { it.registerProjectModelServices(project, kotlinCoreProjectEnvironment.parentDisposable) }

            project.apply {
                registerService(KotlinMessageBusProvider::class.java, KotlinProjectMessageBusProvider::class.java)
                registerService(PomModel::class.java, PomModelImpl::class.java)
                registerService(TreeAspect::class.java, TreeAspect::class.java)
                registerService(BlockSupport::class.java, BlockSupportImpl::class.java)

                FirStandaloneServiceRegistrar.registerProjectServices(project)
                FirStandaloneServiceRegistrar.registerProjectExtensionPoints(project)
                FirStandaloneServiceRegistrar.registerProjectModelServices(project, kotlinCoreProjectEnvironment.parentDisposable)

                registerService(KotlinModificationTrackerFactory::class.java, KotlinStandaloneModificationTrackerFactory::class.java)
                registerService(KotlinGlobalModificationService::class.java, KotlinStandaloneGlobalModificationService::class.java)

                registerService(KotlinResolutionScopeProvider::class.java, KotlinByModulesResolutionScopeProvider::class.java)


                registerService(
                    KotlinAnnotationsResolverFactory::class.java,
                    KotlinStandaloneAnnotationsResolverFactory(project, emptyList())
                )
                registerService(
                    KotlinDeclarationProviderFactory::class.java,
                    declarationProviderFactory
                )
                registerService(
                    KotlinDeclarationProviderMerger::class.java,
                    IncrementalDeclarationProviderMerger(this)
                )

                registerService(KotlinPackageProviderFactory::class.java, packageProviderFactory)

                registerService(KotlinPackageProviderMerger::class.java, LsPackageProviderMerger(this))

                registerService(
                    KotlinPackagePartProviderFactory::class.java,
                    LsPackagePartProviderFactory(packagePartProvider)
                )

            }
        }

        @OptIn(KaImplementationDetail::class)
        private fun registerServicesForProjectEnvironment(
            environment: KotlinCoreProjectEnvironment,
            jdkHome: Path? = null,
        ) {
            val project = environment.project

            KotlinCoreEnvironment.registerProjectExtensionPoints(project.extensionArea)
            with(project) {
                registerService(SmartTypePointerManager::class.java, SmartTypePointerManagerImpl::class.java)
                registerService(SmartPointerManager::class.java, SmartPointerManagerImpl::class.java)
                registerService(JavaElementSourceFactory::class.java, JavaElementSourceWithSmartPointerFactory::class.java)

                registerService(KotlinJavaPsiFacade::class.java, KotlinJavaPsiFacade(this))
            }

            project.registerService(KotlinProjectStructureProvider::class.java, provider)
            project.registerService(KotlinModuleDependentsProvider::class.java, KtStaticModuleDependentsProvider(emptyList()))

            initialiseVirtualFileFinderServices(
                environment,
                provider,
                jdkHome,
            )
        }

        @Suppress("UnstableApiUsage")
        private fun initialiseVirtualFileFinderServices(
            environment: KotlinCoreProjectEnvironment,
            provider: LsProjectStructureProvider,
            jdkHome: Path?,
        ) {
            val project = environment.project
            val javaFileManager = project.getService(JavaFileManager::class.java) as KotlinCliJavaFileManager
            val javaModuleFinder = CliJavaModuleFinder(jdkHome?.toFile(), null, javaFileManager, project, null)
            val javaModuleGraph = JavaModuleGraph(javaModuleFinder)

            val jdkRoots = getDefaultJdkModuleRoots(javaModuleFinder, javaModuleGraph)

            project.registerService(
                JavaModuleResolver::class.java,
                CliJavaModuleResolver(javaModuleGraph, emptyList(), javaModuleFinder.systemModules.toList(), project)
            )

            val libraryRoots = getAllBinaryRoots(provider.allKtModules, environment)
            val allSourceFileRoots = provider.allFiles
                .filter { JavaFileType.INSTANCE == it.fileType }
                .map { JavaRoot(it, JavaRoot.RootType.SOURCE) }

            val rootsWithSingleJavaFileRoots = buildList {
                addAll(libraryRoots)
                addAll(allSourceFileRoots)
                addAll(jdkRoots)
            }

            val (roots, _) =
                rootsWithSingleJavaFileRoots.partition { (file) -> file.isDirectory || file.extension != JavaFileType.INSTANCE.defaultExtension }

            val corePackageIndex = project.getService(PackageIndex::class.java) as CorePackageIndex
            val rootsIndex = JvmDependenciesDynamicCompoundIndex(shouldOnlyFindFirstClass = false).apply {
                addIndex(JvmDependenciesIndexImpl(roots, shouldOnlyFindFirstClass = false))
                indexedRoots.forEach { javaRoot ->
                    if (javaRoot.file.isDirectory) {
                        if (javaRoot.type == JavaRoot.RootType.SOURCE) {
                            corePackageIndex.addToClasspath(javaRoot.file)
                        } else {
                            environment.addSourcesToClasspath(javaRoot.file)
                        }
                    }
                }
            }

            val finderFactory = CliVirtualFileFinderFactory(rootsIndex, false)

            project.registerService(MetadataFinderFactory::class.java, finderFactory)
            project.registerService(VirtualFileFinderFactory::class.java, finderFactory)
        }

        @Suppress("UnstableApiUsage")
        private fun getDefaultJdkModuleRoots(javaModuleFinder: CliJavaModuleFinder, javaModuleGraph: JavaModuleGraph): List<JavaRoot> {
            return javaModuleGraph.getAllDependencies(javaModuleFinder.computeDefaultRootModules()).flatMap { moduleName ->
                val module = javaModuleFinder.findModule(moduleName) ?: return@flatMap emptyList<JavaRoot>()
                val result = module.getJavaModuleRoots()
                result
            }
        }
    }

    override fun dispose() {
    }
}

private val registrars = listOf(FirStandaloneServiceRegistrar, LsServiceRegistrar)

val applicationEnvironment = run {
    val env = KotlinCoreEnvironment.getOrCreateApplicationEnvironment(
        projectDisposable = Disposer.newDisposable(),
        CompilerConfiguration(),
        KotlinCoreApplicationEnvironmentMode.fromUnitTestModeFlag(false),
    )
    fixExtensions(env.application)
    applyOurPlugin(env.application)
    registerApplicationExtensionPoints(env)
    registerApplicationServices(env)
    FirStandaloneServiceRegistrar.registerApplicationServices(env.application)
    env.application.picoContainer.unregisterComponent(FileDocumentManager::class.java.name)
    env.registerApplicationService(
        FileDocumentManager::class.java, LsFileSystem.FileDocumentManager()
    )
    env.registerApplicationService(DefaultProjectFactory::class.java, LsDefaultProjectFactory())

    ApplicationServiceRegistration.registerWithCustomRegistration(env.application, registrars) {
        if (this is FirStandaloneServiceRegistrar) {
            registerApplicationServicesWithCustomClassLoader(env.application, MockProject::class.java.classLoader)
        } else {
            registerApplicationServices(env.application, data = Unit)
        }
    }
    env.application.registerService(LsFileSystem::class.java, LsFileSystem::class.java)
    CoreApplicationEnvironment.registerExtensionPoint(
        env.application.extensionArea, DocumentWriteAccessGuard.EP_NAME.name, LsDocumentWriteAccessGuard::class.java
    )
    env.registerFileType(KlibMetaFileType, "knm")
    env
}

private fun fixExtensions(application: MockApplication) {
    val area = application.extensionArea
    val extensionPoints = area::class.declaredMembers.find { it.name == "extensionPoints" } as KMutableProperty<*>
    extensionPoints.isAccessible = true
    @Suppress("UNCHECKED_CAST")
    val map = extensionPoints.call(area) as Map<String, Any>
    val withoutDecompiler = map.filter { (key, _) ->
        key != "com.intellij.filetype.decompiler"
    }
    val newMap = Collections.unmodifiableMap(withoutDecompiler)
    extensionPoints.setter.call(area, newMap)

    area.registerExtensionPoint(
        "com.intellij.filetype.decompiler",
        FileTypeExtensionPoint::class.java.name,
        ExtensionPoint.Kind.BEAN_CLASS,
        true
    )
}

private fun applyOurPlugin(application: MockApplication) {
    val area = application.extensionArea
    val uri = LsSession::class.java.getResource("/META-INF/unblockt.server.xml")!!.toURI()

    if (uri.scheme != "file") {
        FileSystems.newFileSystem(uri, emptyMap<String, String>()).use {
            loadExtensionsFromRkPlugin(uri, area)
        }
    } else {
        loadExtensionsFromRkPlugin(uri, area)
    }
    PluginStructureProvider.registerApplicationServices(application, "unblockt.server.xml")
}

private fun loadExtensionsFromRkPlugin(uri: URI, area: ExtensionsAreaImpl) {
    val root = uri.toPath().parent.parent
    CoreApplicationEnvironment.registerExtensionPointAndExtensions(
        root,
        "unblockt.server.xml",
        area
    )
}

private fun createProjectEnvironment(
    projectDisposable: Disposable,
    classLoader: ClassLoader = MockProject::class.java.classLoader,
): KotlinCoreProjectEnvironment {
    return object : KotlinCoreProjectEnvironment(projectDisposable, applicationEnvironment) {
        init {
            registerProjectServices(project)
            registerJavaPsiFacade(project)
        }

        override fun createProject(parent: PicoContainer, parentDisposable: Disposable): MockProject {
            return object : MockProject(parent, parentDisposable) {
                @Throws(ClassNotFoundException::class)
                override fun <T> loadClass(className: String, pluginDescriptor: PluginDescriptor): Class<T> {
                    @Suppress("UNCHECKED_CAST")
                    return Class.forName(className, true, classLoader) as Class<T>
                }

                @Suppress("UnstableApiUsage")
                override fun createListener(descriptor: ListenerDescriptor): Any {
                    val listenerClass = loadClass<Any>(descriptor.listenerClassName, descriptor.pluginDescriptor)
                    val listener = listenerClass.getDeclaredConstructor(Project::class.java).newInstance(this)
                    return listener
                }
            }
        }

        override fun createCoreFileManager(): KotlinCliJavaFileManager {
            return LsJavaFileManager(project)
        }
    }
}

private fun registerApplicationExtensionPoints(applicationEnvironment: KotlinCoreApplicationEnvironment) {
    val applicationArea = applicationEnvironment.application.extensionArea

    if (!applicationArea.hasExtensionPoint(AdditionalKDocResolutionProvider.EP_NAME)) {
        KotlinCoreEnvironment.underApplicationLock {
            if (applicationArea.hasExtensionPoint(AdditionalKDocResolutionProvider.EP_NAME)) return@underApplicationLock
            CoreApplicationEnvironment.registerApplicationExtensionPoint(
                AdditionalKDocResolutionProvider.EP_NAME,
                AdditionalKDocResolutionProvider::class.java
            )
        }
    }

    if (!applicationArea.hasExtensionPoint(ClassTypePointerFactory.EP_NAME)) {
        KotlinCoreEnvironment.underApplicationLock {
            if (applicationArea.hasExtensionPoint(ClassTypePointerFactory.EP_NAME)) return@underApplicationLock
            CoreApplicationEnvironment.registerApplicationExtensionPoint(
                ClassTypePointerFactory.EP_NAME,
                ClassTypePointerFactory::class.java
            )
            applicationArea.getExtensionPoint(ClassTypePointerFactory.EP_NAME)
                .registerExtension(PsiClassReferenceTypePointerFactory(), applicationEnvironment.application)
        }
    }
}

private fun registerApplicationServices(applicationEnvironment: KotlinCoreApplicationEnvironment) {
    val application = applicationEnvironment.application
    if (application.getServiceIfCreated(KotlinFakeClsStubsCache::class.java) != null) {
        return
    }
    KotlinCoreEnvironment.underApplicationLock {
        if (application.getServiceIfCreated(KotlinFakeClsStubsCache::class.java) != null) {
            return
        }
        application.apply {
            registerService(KotlinFakeClsStubsCache::class.java, KotlinFakeClsStubsCache::class.java)
            registerService(ClsKotlinBinaryClassCache::class.java)
            registerService(
                BuiltinsVirtualFileProvider::class.java,
                BuiltinsVirtualFileProviderCliImpl()
            )
            registerService(FileAttributeService::class.java, DummyFileAttributeService::class.java)
        }
    }
}

private fun registerProjectServices(project: MockProject) {
    CoreApplicationEnvironment.registerExtensionPoint(
        project.extensionArea,
        KaResolveExtensionProvider.EP_NAME.name,
        KaResolveExtensionProvider::class.java
    )
}

private fun registerJavaPsiFacade(project: MockProject) {
    with(project) {
        registerService(
            CoreJavaFileManager::class.java,
            this.getService(JavaFileManager::class.java) as CoreJavaFileManager
        )

        registerService(ExternalAnnotationsManager::class.java, MockExternalAnnotationsManager())
        registerService(InferredAnnotationsManager::class.java, MockInferredAnnotationsManager())

        setupHighestLanguageLevel()
    }
}