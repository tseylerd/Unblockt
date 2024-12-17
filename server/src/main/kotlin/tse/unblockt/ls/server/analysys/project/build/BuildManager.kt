// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

package tse.unblockt.ls.server.analysys.project.build

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import tse.unblockt.ls.protocol.Uri
import tse.unblockt.ls.server.GlobalServerState
import tse.unblockt.ls.server.analysys.AnalysisEntrypoint
import tse.unblockt.ls.server.analysys.LsListeners
import tse.unblockt.ls.server.analysys.files.isBuildFile
import tse.unblockt.ls.server.analysys.project.Change
import tse.unblockt.ls.server.analysys.project.ChangeKind
import tse.unblockt.ls.server.analysys.project.ProjectBuildEntry
import tse.unblockt.ls.server.analysys.project.ProjectBuildModel
import tse.unblockt.ls.server.analysys.service.SessionBasedServices
import tse.unblockt.ls.server.fs.LsFileSystem
import tse.unblockt.ls.server.project.UBProjectModel
import java.io.IOException
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.security.MessageDigest

internal class BuildManager(project: Project) {
    companion object {
        fun instance(project: Project): BuildManager {
            return project.service()
        }
    }

    private var currentModel: ProjectBuildModel? = null

    private val fileSystem = LsFileSystem.instance()

    init {
        LsListeners.instance(project).listen(object : LsListeners.FileStateListener {
            override suspend fun created(uri: Uri) {
                processFileCreated(uri)
            }

            override suspend fun renamed(old: Uri, actual: Uri) {
                processFileChange(old)
                processFileCreated(actual)
            }

            override suspend fun saved(uri: Uri) {
                processFileChange(uri)
            }

            override suspend fun changed(uri: Uri) {
                processFileChange(uri)
            }

            override suspend fun deleted(uri: Uri) {
                processFileChange(uri)
            }
        })
    }

    fun indexBuildModel(projectModel: UBProjectModel) {
        val buildModel = projectToBuildModel(projectModel)
        currentModel = buildModel
    }

    fun shouldReload(): Boolean {
        val readBuildModel = currentModel ?: return false
        return readBuildModel.changes.isNotEmpty()
    }

    suspend fun reload(rootPath: Path) {
        AnalysisEntrypoint.init(rootPath, AnalysisEntrypoint.services.serviceInformation.storagePath, AnalysisEntrypoint.services.serviceInformation.globalStoragePath)
        GlobalServerState.initialized()
    }

    private fun projectToBuildModel(projectModel: UBProjectModel): ProjectBuildModel {
        val entries = mutableSetOf<ProjectBuildEntry>()
        val graph = SessionBasedServices.buildGradleProjectsGraph(projectModel.modules)
        val manager = VirtualFileManager.getInstance()
        for (gradleProject in projectModel.modules) {
            val isRoot = graph.values.none { it.contains(gradleProject) }
            val files = gradleProject.path.collectGradleBuildFiles(isRoot).toMutableSet()
            files.add(gradleProject.buildFile)

            entries += files.mapNotNull { p ->
                val vFile = manager.findFileByNioPath(p) ?: return@mapNotNull null
                val hash = hashFile(vFile)
                ProjectBuildEntry(vFile.url, hash)
            }.toSet()
        }
        return ProjectBuildModel(ProjectBuildModel.VERSION, entries, emptySet())
    }

    private fun processFileChange(uri: Uri) {
        if (!uri.isBuildFile) {
            return
        }

        val buildModel = currentModel ?: return
        val entryFromModel = buildModel.entries.firstOrNull { it.url == uri.data }
        if (entryFromModel != null) {
            val vFile = fileSystem.getVirtualFile(uri)
            if (vFile != null) {
                val fileHash = hashFile(vFile)
                val reloadNeeded = !fileHash.contentEquals(entryFromModel.hash)
                val change = Change(uri.data, ChangeKind.CHANGED)
                val newChanges = when {
                    reloadNeeded -> buildModel.changes + change
                    else -> buildModel.changes - change
                }
                currentModel = buildModel.copy(changes = newChanges)
            } else {
                currentModel = buildModel.copy(changes = buildModel.changes + Change(uri.data, ChangeKind.DELETED))
            }
        }
    }

    private fun processFileCreated(uri: Uri) {
        if (!uri.isBuildFile) {
            return
        }

        val buildModel = currentModel ?: return
        val changeFromModel = buildModel.changes.firstOrNull { it.url == uri.data }
        if (changeFromModel != null) {
            val entryFromModel = buildModel.entries.first { it.url == uri.data }
            val vFile = fileSystem.getVirtualFile(uri)!!
            val fileHash = hashFile(vFile)
            val reloadNeeded = fileHash.contentEquals(entryFromModel.hash)
            val change = Change(uri.data, ChangeKind.CHANGED)
            val deletedChange = Change(uri.data, ChangeKind.DELETED)
            val newChanges = when {
                reloadNeeded -> buildModel.changes - deletedChange + change
                else -> buildModel.changes - deletedChange - change
            }
            currentModel = buildModel.copy(changes = newChanges)
        }
    }

    private fun hashFile(virtualFile: VirtualFile): ByteArray {
        return MessageDigest.getInstance("MD5").digest(Files.readAllBytes(Paths.get(virtualFile.path)))
    }

    private fun Path.collectGradleBuildFiles(root: Boolean): Set<Path> {
        if (!root) {
            return emptySet()
        }
        val settingsFile = resolve("settings.gradle.kts")
        val propertiesFile = resolve("gradle.properties")
        val buildSrc = resolve("buildSrc")
        val buildSources = mutableSetOf<Path>()
         when {
            Files.exists(buildSrc) -> Files.walkFileTree(buildSrc, object : FileVisitor<Path> {
                override fun visitFile(file: Path, attrs: BasicFileAttributes?): FileVisitResult {
                    buildSources.add(file)
                    return FileVisitResult.CONTINUE
                }

                override fun preVisitDirectory(dir: Path?, attrs: BasicFileAttributes?): FileVisitResult {
                    return FileVisitResult.CONTINUE
                }

                override fun visitFileFailed(file: Path?, exc: IOException?): FileVisitResult {
                    return FileVisitResult.CONTINUE
                }

                override fun postVisitDirectory(dir: Path?, exc: IOException?): FileVisitResult {
                    return FileVisitResult.CONTINUE
                }
            })
        }
        return buildSources + setOf(settingsFile, propertiesFile).filter { Files.exists(it) }
    }

    fun getCurrentModel(): ProjectBuildModel? {
        return currentModel
    }
}