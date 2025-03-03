// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

package tse.unblockt.ls.server.analysys.storage

import com.intellij.openapi.project.Project
import java.nio.file.Path

class MainDBManager(
    private val project: Project,
    private val projectRoot: Path,
    private val workspaceStorage: Path,
    private val globalStorage: Path,
    private val allLibrariesRoots: Collection<String>
) {
    val database: RouterDB
        get() = db

    private lateinit var router: LocalGlobalRouter
    private lateinit var db: RouterDB

    init {
        createDB()
    }

    fun cleanup() {
        db.close()
        db.delete()

        createDB()
        initDB()
    }

    fun initDB() {
        val result = db.init()
        if (!result.success) {
            throw IllegalStateException("Failed to initialize main DB")
        }
    }

    suspend fun exclusively(what: suspend () -> Unit) {
        router.globalDB.exclusively(what)
    }

    private fun createDB() {
        router = LocalGlobalRouter(
            project,
            projectRoot,
            workspaceStorage,
            globalStorage,
            allLibrariesRoots,
        )
        db = RouterDB(router)
    }
}