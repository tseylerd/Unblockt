// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

package tse.unblockt.ls.server.analysys

import com.intellij.openapi.util.Disposer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.apache.logging.log4j.kotlin.logger
import tse.unblockt.ls.protocol.HealthStatus
import tse.unblockt.ls.protocol.HealthStatusInformation
import tse.unblockt.ls.protocol.progress.report
import tse.unblockt.ls.server.analysys.completion.LsCompletionMachine
import tse.unblockt.ls.server.analysys.declaration.LsGoToDeclarationProvider
import tse.unblockt.ls.server.analysys.higlighting.LsHighlightingProvider
import tse.unblockt.ls.server.analysys.service.LsServices
import tse.unblockt.ls.server.analysys.service.NoSessionServices
import tse.unblockt.ls.server.analysys.service.SessionBasedServices
import tse.unblockt.ls.server.analysys.service.memoryMessage
import tse.unblockt.ls.server.client.ClientLog
import tse.unblockt.ls.server.fs.LsFileManager
import tse.unblockt.ls.server.project.ProjectImportError
import tse.unblockt.ls.server.project.ProjectImportResult
import tse.unblockt.ls.server.project.importGradleProject
import java.nio.file.Path

object AnalysisEntrypoint {
    val services: LsServices
        get() = _services!!

    private var _services: LsServices? = null

    internal val isInitialized: Boolean
        get() = _services != null

    internal val filesManager: LsFileManager
        get() = services.filesManager

    internal val goToDeclarationProvider: LsGoToDeclarationProvider
        get() = services.goToDeclarationProvider

    internal val completionMachine: LsCompletionMachine
        get() = services.completionMachine

    internal val highlightingProvider: LsHighlightingProvider
        get() = services.highlightingProvider

    internal val health: HealthStatusInformation
        get() {
            val cur = _services
            return if (cur != null) {
                cur.health
            } else {
                val message = memoryMessage()
                HealthStatusInformation(message, message, HealthStatus.HEALTHY)
            }
        }

    suspend fun init(
        rootPath: Path,
        storagePath: Path,
    ) {
        report("Importing gradle project...")

        val import = coroutineScope {
            async(Dispatchers.IO) {
                importGradleProject(rootPath) {
                    launch {
                        report(it)
                        tse.unblockt.ls.server.client.message(ClientLog.GRADLE, it)
                    }
                }
            }.await()
        }

        if (import is ProjectImportResult.Failure) {
            shutdown()
            _services = NoSessionServices(rootPath, storagePath, import.error)
            return
        }

        import as ProjectImportResult.Success
        try {
            shutdown()
            _services = SessionBasedServices.create(rootPath, import.model, storagePath)
        } catch (e: Exception) {
            logger.error(e)
            kotlin.runCatching { shutdown() }
            _services = NoSessionServices(rootPath, storagePath, ProjectImportError.FailedToImportProject(e))
        }
    }


    fun shutdown() {
        val curServices = _services
        if (curServices != null) {
            Disposer.dispose(curServices)
            _services = null
        }
    }
}