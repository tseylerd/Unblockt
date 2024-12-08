// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

package tse.unblockt.ls.server.fs

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import org.apache.logging.log4j.kotlin.logger
import org.jetbrains.kotlin.analysis.api.platform.modification.KotlinGlobalModificationService
import org.jetbrains.kotlin.psi.KtFile
import tse.unblockt.ls.protocol.Uri
import tse.unblockt.ls.server.analysys.AnalysisEntrypoint
import tse.unblockt.ls.server.analysys.LsListeners
import tse.unblockt.ls.server.analysys.files.isKotlin
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.readText

object GlobalFileState {
    private val actualContent = ConcurrentHashMap<Uri, String>()
    private val modificationStamps = ConcurrentHashMap<IJUrl, Long>()
    private val listeners = mutableListOf<GlobalFileStateListener>()

    @Volatile
    private var recentlySaved: Uri? = null

    fun init(project: Project) {
        LsListeners.instance(project).listen(object : LsListeners.FileChangeListener {
            override suspend fun changed(uri: Uri) {
                actualContent.remove(uri)
                modificationStamps.remove(uri.ij)
                val asPath = uri.asPath()
                changeFile(project, uri, asPath.readText())
            }

            override suspend fun changed(uri: Uri, content: String) {
                actualContent[uri] = content
                modificationStamps.compute(uri.ij) { _, old ->
                    (old ?: uri.asPath().toFile().lastModified()) + 1
                }
                changeFile(project, uri, content)
            }

            override suspend fun created(uri: Uri) {
                if (uri.isKotlin) {
                    for (listener in listeners) {
                        listener.created(uri)
                    }
                }
            }

            override suspend fun deleted(uri: Uri) {
                if (!uri.isKotlin) {
                    return
                }
                actualContent.remove(uri)
                modificationStamps.remove(uri.ij)

                for (listener in listeners) {
                    listener.deleted(uri)
                }
            }

            override suspend fun renamed(old: Uri, actual: Uri) {
                val remove = actualContent.remove(old)
                val stamp = modificationStamps.remove(old.ij)
                if (remove != null) {
                    actualContent[actual] = remove
                    changeFile(project, actual, remove, false)
                }
                if (stamp != null) {
                    modificationStamps[actual.ij] = stamp
                }

                for (listener in listeners) {
                    listener.deleted(old)
                    listener.created(actual)
                }
            }

            override suspend fun saved(uri: Uri) {
                changed(uri)
            }
        })
    }

    internal fun actualContent(uri: Uri): String? {
        return actualContent[uri]
    }

    internal fun modificationStamp(url: IJUrl): Long? {
        return modificationStamps[url]
    }

    fun subscribe(listener: GlobalFileStateListener, disposable: Disposable) {
        listeners.add(listener)
        Disposer.register(disposable) {
            listeners.remove(listener)
        }
    }

    fun isModified(uri: Uri): Boolean {
        return actualContent.containsKey(uri)
    }

    fun shutdown() {
        actualContent.clear()
        modificationStamps.clear()
    }

    private fun reloadFile(project: Project, uri: Uri) {
        val psiFile = AnalysisEntrypoint.filesManager.getPsiFile(uri) as? KtFile
        if (psiFile == null) {
            logger.info("Can't find psi file for uri $uri")
            return
        }
        invalidateFile(project, psiFile)

        val psiManager = PsiManager.getInstance(project)

        KotlinGlobalModificationService.getInstance(project).publishGlobalSourceModuleStateModification()
        psiManager.dropResolveCaches()
        psiManager.dropPsiCaches()
        psiFile.onContentReload()
    }

    private fun changeFile(project: Project, uri: Uri, content: String, fire: Boolean = true) {
        val document = AnalysisEntrypoint.filesManager.getDocument(uri) ?: return
        document.setText(content)
        PsiDocumentManager.getInstance(project).commitDocument(document)
        reloadFile(project, uri)
        if (fire) {
            for (listener in listeners) {
                listener.changed(uri)
            }
        }
    }

    interface GlobalFileStateListener {
        fun changed(uri: Uri)
        fun deleted(uri: Uri)
        fun created(uri: Uri)
    }

    @JvmInline
    internal value class IJUrl(val value: String)

    private val Uri.ij: IJUrl
        get() = IJUrl(asIJUrl)

    private fun invalidateFile(project: Project, psiFile: PsiFile): KtFile {
        PsiManager.getInstance(project).reloadFromDisk(psiFile)

        return psiFile as KtFile
    }
}