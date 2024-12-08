// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

package tse.unblockt.ls.server.analysys.index

import com.intellij.ide.highlighter.JavaClassFileType
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.fileTypes.UnknownFileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileVisitor
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.stubs.impl.KotlinFileStubImpl
import tse.unblockt.ls.protocol.Uri
import tse.unblockt.ls.protocol.progress.report
import tse.unblockt.ls.server.analysys.files.isKotlin
import tse.unblockt.ls.server.analysys.index.common.IndexFileEntry
import tse.unblockt.ls.server.analysys.index.common.Indexes
import tse.unblockt.ls.server.analysys.index.machines.IndexMachine
import tse.unblockt.ls.server.analysys.index.model.ProjectModelSetup
import tse.unblockt.ls.server.analysys.index.stub.IndexModel
import tse.unblockt.ls.server.analysys.storage.DB
import tse.unblockt.ls.server.analysys.storage.PersistentStorage
import tse.unblockt.ls.server.fs.GlobalFileState
import tse.unblockt.ls.server.fs.LsFileSystem
import tse.unblockt.ls.server.fs.asIJUrl
import kotlin.reflect.KClass

class LsSourceCodeIndexerImpl(private val project: Project): LsSourceCodeIndexer {
    companion object {
        private fun cleanup(machines: List<IndexMachine<*, *>>, storage: PersistentStorage, url: String) {
            for (machine in machines) {
                val attribute: DB.Attribute<String, *, *> = machine.attribute
                storage.delete(machine.namespace, attribute, url)
            }
        }

        private fun indexAll(machines: List<IndexMachine<*, *>>, storage: PersistentStorage, entries: Sequence<IndexFileEntry>) {
            for (machine in machines) {
                entries.flatMap { entry ->
                    machine.index(entry).map { pair ->
                        Triple(entry.psiFile.virtualFile.url, pair.first, pair.second)
                    }
                }.chunked(500).forEach { triples: List<Triple<String, Any, Any>> ->
                    val set: Set<Triple<String, Any, Any>> = triples.toSet()
                    @Suppress("UNCHECKED_CAST")
                    storage.putAll(machine.namespace, machine.attribute as DB.Attribute<String, Any, Any>, set)
                }
            }
        }
    }

    private val ourMachines = IndexMachine.ourMachines(project)
    private val fileSystem by lazy {
        LsFileSystem.instance()
    }
    private val storage by lazy {
        PersistentStorage.instance(project)
    }
    private val indexes by lazy {
        Indexes.instance(project)
    }
    private val psiCache by lazy {
        LsPsiCache.instance(project)
    }
    private val stubCache by lazy {
        LsStubCache.instance(project)
    }

    init {
        GlobalFileState.subscribe(object : GlobalFileState.GlobalFileStateListener {
            override fun changed(uri: Uri) {
                if (!uri.isKotlin) {
                    return
                }

                val element = psiCache[uri.asIJUrl] as? KtFile ?: return
                removeByUrls(listOf(element.virtualFile.url))
                indexKtFiles(listOf(element))
            }

            override fun deleted(uri: Uri) {
                if (!uri.isKotlin) {
                    return
                }

                val ijURL = uri.asIJUrl
                removeByUrls(listOf(ijURL))
            }

            override fun created(uri: Uri) {
                if (!uri.isKotlin) {
                    return
                }

                val element = psiCache[uri.asIJUrl] as? KtFile ?: return
                removeByUrls(listOf(element.virtualFile.url))
                indexKtFiles(listOf(element))
            }
        }, project)
    }

    override fun init() {
        storage.init()
        storage.init(ProjectModelSetup.namespace, ProjectModelSetup.entryAttribute)
        storage.init(ProjectModelSetup.namespace, ProjectModelSetup.versionAttribute)
        for (ourMachine in ourMachines) {
            storage.init(ourMachine.namespace, ourMachine.attribute)
        }
    }

    override operator fun <K: Any, V: Any, I: IndexMachine<K, V>> get(clazz: KClass<I>): I {
        @Suppress("UNCHECKED_CAST")
        return ourMachines.first { clazz.isInstance(it) } as I
    }

    override suspend fun index(files: Collection<PsiFile>) {
        for (file in files) {
            addToModel(file.virtualFile)
        }

        indexKtFiles(files.filterIsInstance<KtFile>())
    }

    override suspend fun updateIndexes(model: IndexModel) {
        val builtIns = indexes.kotlinPsi.builtIns + indexes.javaPsi.builtIns
        val modelWithBuiltIns = model.copy(
            version = model.version,
            paths = model.paths + builtIns.map {
                IndexModel.Entry(
                    it.url, IndexModel.EntryProperties(
                        fileSystem.getModificationStamp(it),
                        builtIns = true,
                        stub = true
                    )
                )
            }.toSet()
        )
        if (!storage.isValid()) {
            storage.deleteAll()
        }

        storage.sync {
            report("Indexing...")
            val diff = diff(modelWithBuiltIns)
            for (entry in diff.delete) {
                val sequence = filesSequence(entry).toSet()
                if (sequence.isNotEmpty()) {
                    for (oneEntry in sequence) {
                        val virtualFile = oneEntry.psiFile.virtualFile
                        report("Cleaning up indexes for ${virtualFile.path}")
                        cleanup(ourMachines, storage, virtualFile.url)
                    }
                } else {
                    report("Cleaning up indexes for ${entry.url}")
                    cleanup(ourMachines, storage, entry.url)
                }

                delete(ProjectModelSetup.namespace, ProjectModelSetup.entryAttribute, entry.url)
            }

            for (entry in diff.add) {
                report("Indexing ${entry.url}")
                indexAll(ourMachines, storage, filesSequence(entry))
                put(ProjectModelSetup.namespace, ProjectModelSetup.entryAttribute, entry.url, entry.url, entry)
            }

            put(
                ProjectModelSetup.namespace,
                ProjectModelSetup.versionAttribute,
                ProjectModelSetup.sourceKey,
                ProjectModelSetup.versionKey,
                model.version
            )
        }
        loadAllStubs(modelWithBuiltIns)
    }

    private suspend fun loadAllStubs(modelWithBuiltIns: IndexModel) {
        report("Loading dependencies...")
        modelWithBuiltIns.paths.asSequence().filter { it.properties.stub }.flatMap { filesSequence(it) }.forEach {
            stubCache[it.psiFile.virtualFile, it.builtIns]
        }
    }

    private suspend fun PersistentStorage.diff(modelWithBuiltIns: IndexModel): IndexModelDiff {
        report("Reading model...")
        val savedModel = readModel()

        report("Computing difference...")
        return computeDiff(modelWithBuiltIns, savedModel)
    }

    private fun addToModel(vFile: VirtualFile) {
        val url = vFile.url
        storage.put(
            ProjectModelSetup.namespace,
            ProjectModelSetup.entryAttribute,
            url,
            url,
            IndexModel.Entry(
                url,
                IndexModel.EntryProperties(fileSystem.getModificationStamp(vFile), builtIns = false, stub = false)
            )
        )
    }

    private fun deleteFromModel(url: String) {
        storage.delete(
            ProjectModelSetup.namespace,
            ProjectModelSetup.entryAttribute,
            url
        )
    }

    private fun PersistentStorage.readModel(): IndexModel? {
        val allEntries = getSequenceOfValues(ProjectModelSetup.namespace, ProjectModelSetup.entryAttribute).map { it }.toList()
        val version = getSequenceOfValues(ProjectModelSetup.namespace, ProjectModelSetup.versionAttribute).map { it }.toList()
        if (version.size != 1) {
            return null
        }
        if (allEntries.isEmpty()) {
            return null
        }
        return IndexModel(version = version.single(), allEntries.toSet())
    }

    private fun filesSequence(root: IndexModel.Entry): Sequence<IndexFileEntry> {
        val vFile = fileSystem.getVirtualFileByUrl(root.url) ?: return emptySequence()
        return filesSequence(vFile, root.properties.builtIns, root.properties.stub)
    }

    private fun filesSequence(virtualFile: VirtualFile, builtIns: Boolean, isBinary: Boolean): Sequence<IndexFileEntry> {
        val fileType = virtualFile.fileType
        if (fileType != UnknownFileType.INSTANCE && fileType != JavaFileType.INSTANCE && fileType != KotlinFileType.INSTANCE && fileType != JavaClassFileType.INSTANCE) {
            return emptySequence()
        }
        return sequence {
            when {
                virtualFile.isDirectory -> {
                    val files = mutableSetOf<VirtualFile>()
                    VfsUtilCore.visitChildrenRecursively(virtualFile, object : VirtualFileVisitor<Void>() {
                        override fun visitFile(file: VirtualFile): Boolean {
                            if (!file.isDirectory) {
                                files.add(file)
                            }
                            return true
                        }
                    })

                    for (child in files) {
                        yieldAll(filesSequence(child, builtIns, isBinary))
                    }
                }
                else -> {
                    val stub = when {
                        isBinary -> stubCache[virtualFile, builtIns] ?: return@sequence
                        else -> null
                    }
                    val psiFile = stub?.psi ?: psiCache[virtualFile] ?: return@sequence
                    yield(IndexFileEntry(psiFile, stub, builtIns || psiFile is KtFile || stub is KotlinFileStubImpl, builtIns))
                }
            }
        }
    }

    private fun computeDiff(newModel: IndexModel, oldModel: IndexModel?): IndexModelDiff {
        oldModel ?: return IndexModelDiff(emptySet(), newModel.paths)
        if (oldModel.version != newModel.version) {
            return IndexModelDiff(oldModel.paths, newModel.paths)
        }
        val toDelete = oldModel.paths - newModel.paths
        val toAdd = newModel.paths - oldModel.paths
        return IndexModelDiff(toDelete, toAdd)
    }

    private fun removeByUrls(files: Collection<String>) {
        for (file in files) {
            cleanup(ourMachines, this.storage, file)
            deleteFromModel(file)
        }
    }

    private fun indexKtFiles(files: Collection<KtFile>) {
        indexAll(ourMachines, storage, files.asSequence().map {
            IndexFileEntry(it, null, isKotlin = true, builtIns = false)
        })
        for (file in files) {
            addToModel(file.virtualFile)
        }
    }

    private data class IndexModelDiff(
        val delete: Set<IndexModel.Entry>,
        val add: Set<IndexModel.Entry>,
    )
}