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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import org.jetbrains.kotlin.analysis.decompiler.konan.KlibMetaFileType
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
import tse.unblockt.ls.server.fs.cutProtocol
import kotlin.reflect.KClass

class LsSourceCodeIndexerImpl(private val project: Project): LsSourceCodeIndexer {
    companion object {
        private fun cleanup(machines: List<IndexMachine<*, *>>, storage: PersistentStorage, url: String) {
            for (machine in machines) {
                val attribute: DB.Attribute<String, *, *> = machine.attribute
                storage.delete(machine.namespace, attribute, url)
            }
        }

        private suspend fun indexAll(machines: List<IndexMachine<*, *>>, storage: PersistentStorage, entries: Sequence<IndexFileEntry>) {
            coroutineScope {
                val allFiles = entries.toList()
                for (machine in machines) {
                    allFiles.flatMap { entry ->
                        machine.index(entry).map { pair ->
                            Triple(entry.psiFile.virtualFile.url, pair.first, pair.second)
                        }
                    }.chunked(500).chunked(16).forEach { p: List<List<Triple<String, Any, Any>>> ->
                        p.map { list ->
                            launch(Dispatchers.IO) {
                                val set: Set<Triple<String, Any, Any>> = list.toSet()
                                @Suppress("UNCHECKED_CAST")
                                storage.putAll(machine.namespace, machine.attribute as DB.Attribute<String, Any, Any>, set)
                            }
                        }.joinAll()
                    }
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
    override val builtins: Collection<VirtualFile> by lazy {
        indexes.kotlinPsi.builtIns + indexes.javaPsi.builtIns
    }

    init {
        GlobalFileState.subscribe(object : GlobalFileState.GlobalFileStateListener {
            override suspend fun changed(uri: Uri) {
                if (!uri.isKotlin) {
                    return
                }

                val element = psiCache[uri.asIJUrl] as? KtFile ?: return
                removeByUrls(listOf(element.virtualFile.url))
                indexKtFiles(listOf(element))
            }

            override suspend fun deleted(uri: Uri) {
                if (!uri.isKotlin) {
                    return
                }

                val ijURL = uri.asIJUrl
                removeByUrls(listOf(ijURL))
            }

            override suspend fun created(uri: Uri) {
                if (!uri.isKotlin) {
                    return
                }

                val element = psiCache[uri.asIJUrl] as? KtFile ?: return
                removeByUrls(listOf(element.virtualFile.url))
                indexKtFiles(listOf(element))
            }
        }, project)
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
        report("waiting for other indexing processes to finish...")
        storage.inSession {
            report("initializing databases...")
            init()
            init(ProjectModelSetup.namespace, ProjectModelSetup.entryAttribute)
            for (ourMachine in ourMachines) {
                init(ourMachine.namespace, ourMachine.attribute)
            }

            report("reading model...")
            val savedModel = readModel()

            report("checking changes...")
            val diff = computeDiff(model, savedModel)
            for (entry in diff.delete) {
                val sequence = filesSequence(entry).toSet()
                if (sequence.isNotEmpty()) {
                    for (oneEntry in sequence) {
                        val virtualFile = oneEntry.psiFile.virtualFile
                        report("cleaning up indexes for ${virtualFile.path}")
                        cleanup(ourMachines, storage, virtualFile.url)
                    }
                } else {
                    report("cleaning up indexes for ${entry.url.cutProtocol}")
                    cleanup(ourMachines, storage, entry.url)
                }

                delete(ProjectModelSetup.namespace, ProjectModelSetup.entryAttribute, entry.url)
            }

            for (entry in diff.add) {
                if (exists(entry.url)) {
                    put(ProjectModelSetup.namespace, ProjectModelSetup.entryAttribute, entry.url, entry.url, entry)
                    continue
                }

                report("indexing ${entry.url.cutProtocol}")
                indexAll(ourMachines, storage, filesSequence(entry))
                put(ProjectModelSetup.namespace, ProjectModelSetup.entryAttribute, entry.url, entry.url, entry)
            }
        }
        loadAllStubs(model)
    }

    private fun PersistentStorage.validateModel(savedModel: IndexModel?): IndexModel? {
        savedModel ?: return null

        val libs = savedModel.paths.filter { it.properties.stub }
        val removedLibs = mutableSetOf<IndexModel.Entry>()
        for (lib in libs) {
            if (!exists(lib.url)) {
                removedLibs.add(lib)
            }
        }
        return IndexModel(paths = savedModel.paths - removedLibs)
    }

    private suspend fun loadAllStubs(modelWithBuiltIns: IndexModel) {
        report("loading dependencies...")
        modelWithBuiltIns.paths.asSequence().filter { it.properties.stub }.flatMap { filesSequence(it) }.forEach {
            stubCache[it.psiFile.virtualFile, it.builtIns]
        }
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
        if (allEntries.isEmpty()) {
            return null
        }
        return validateModel(IndexModel(allEntries.toSet()))
    }

    private fun filesSequence(root: IndexModel.Entry): Sequence<IndexFileEntry> {
        val vFile = fileSystem.getVirtualFileByUrl(root.url) ?: return emptySequence()
        return filesSequence(vFile, root.properties.builtIns, root.properties.stub)
    }

    private fun filesSequence(virtualFile: VirtualFile, builtIns: Boolean, isBinary: Boolean): Sequence<IndexFileEntry> {
        val fileType = virtualFile.fileType
        if (fileType != UnknownFileType.INSTANCE && fileType != KlibMetaFileType && fileType != JavaFileType.INSTANCE && fileType != KotlinFileType.INSTANCE && fileType != JavaClassFileType.INSTANCE) {
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
                virtualFile.extension != "a" && virtualFile.extension != "so" -> {
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

    private suspend fun indexKtFiles(files: Collection<KtFile>) {
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