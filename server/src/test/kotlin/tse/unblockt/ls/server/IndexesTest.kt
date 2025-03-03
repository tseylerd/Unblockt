// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

package tse.unblockt.ls.server

import com.intellij.openapi.project.DefaultProjectFactory
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo
import org.junit.jupiter.api.assertDoesNotThrow
import org.mapdb.HTreeMap
import org.mapdb.Serializer
import org.mapdb.serializer.SerializerArrayTuple
import tse.unblockt.ls.isUUID
import tse.unblockt.ls.protocol.Document
import tse.unblockt.ls.protocol.JobWithProgressParams
import tse.unblockt.ls.protocol.SemanticTokensParams
import tse.unblockt.ls.protocol.Uri
import tse.unblockt.ls.server.analysys.AnalysisEntrypoint
import tse.unblockt.ls.server.analysys.index.LsSourceCodeIndexer
import tse.unblockt.ls.server.analysys.index.machines.JavaPackageIndexMachine
import tse.unblockt.ls.server.analysys.index.machines.KtPackageIndexMachine
import tse.unblockt.ls.server.analysys.service.SessionBasedServices
import tse.unblockt.ls.server.analysys.storage.*
import tse.unblockt.ls.server.framework.simulateClient
import tse.unblockt.ls.server.fs.cutProtocol
import tse.unblockt.ls.util.*
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import kotlin.io.path.*
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.mapdb.DB as MapDB

class IndexesTest {
    companion object {
        private const val COMMONS_LIB = "commons-lang3-3.17.0.jar!/"
        private const val COMMONS_DEP = "implementation(\"org.apache.commons:commons-lang3:3.17.0\")"
        private val ourGlobalIndexesPath = globalIndexPath.resolve(".unblockt").resolve("global")
    }

    @Test
    fun stubsAreRebuilt(info: TestInfo) = rkTest {
        init(testProjectPath)

        val defaultProject = DefaultProjectFactory.getInstance().defaultProject
        PersistentStorage.instance(defaultProject).clearCache()

        val semanticTokens = languageServer.textDocument.semanticTokensFull(
            SemanticTokensParams(
                Document(
                    Uri("file://$testProjectPath/src/main/java/tse/com/sample.kt")
                )
            )
        )

        val projected = projectTokens(Paths.get("$testProjectPath/src/main/java/tse/com/sample.kt"), semanticTokens)
        assertEqualsWithFile(projected, info)
    }

    @Test
    fun indexesAreReloaded(info: TestInfo) = rkTest {
        init(testProjectPath)
        val bs = languageServer.buildSystem
        assertNotNull(bs)

        assertDoesNotThrow {
            bs.reload(JobWithProgressParams(null))
        }
    }

    @Test
    fun commonStorageRemainsOnLibraryDeletion(info: TestInfo) = rkTest {
        init(testProjectPath)

        val metadataDB = globalIndexPath.resolve(".unblockt").resolve("global").resolve(LibrariesRouter.CATALOGUE_DB)
        MDB.makeMetaDB(metadataDB).use { mdb ->
            val librariesMap = loadLibrariesMap(mdb)

            assertFalse { librariesMap.isEmpty() }

            assertCommonsIsHere(librariesMap)

            val buildFile = testProjectPath.resolve("build.gradle.kts")
            val textBefore = buildFile.readText()
            val newText = textBefore.replace(COMMONS_DEP, "")
            try {
                buildFile.writeText(newText)
                languageServer.buildSystem?.reload(JobWithProgressParams(null))
                val libMapAfter = loadLibrariesMap(mdb)
                assertCommonsIsHere(libMapAfter)
            } finally {
                buildFile.writeText(textBefore)
            }
        }
    }

    @Test
    fun librariesAreFrozenAfterIndexing(info: TestInfo) = rkTest {
        init(testProjectPath)

        val metadataDB = globalIndexPath.resolve(".unblockt").resolve("global").resolve(LibrariesRouter.CATALOGUE_DB)
        MDB.makeMetaDB(metadataDB).use { mdb ->
            val librariesMap = loadLibrariesMap(mdb)
            assertFalse { librariesMap.isEmpty() }
            for ((key, arr) in librariesMap) {
                val completed = arr[1] as Boolean
                assertTrue("Library $key is not completed") { completed }
            }
        }
    }

    @Test
    fun dbIsDeletedIfVersionChanged(info: TestInfo) = rkTest {
        init(testProjectPath)

        val localDB = localDB(testProjectPath)
        val globalDB = globalDB(testProjectPath)

        localDB.init()
        globalDB.init()

        val savedVersionLocal = localDB.dbVersion

        assertNotNull(savedVersionLocal, "Version is null")

        val savedVersionGlobal = globalDB.dbVersion

        assertNotNull(savedVersionGlobal, "Version is null")

        val currentVersion = IndexVersionProvider.instance().version

        assertEquals(currentVersion, savedVersionLocal)
        assertEquals(currentVersion, savedVersionGlobal)

        val newVersion = currentVersion + 1
        IndexVersionProvider.set(IndexVersionProvider.Simple(newVersion))

        val timeNow = System.currentTimeMillis()
        kotlin.runCatching {
            localDB.close()
        }
        kotlin.runCatching {
            globalDB.close()
        }

        languageServer.initializer.shutdown()

        init(testProjectPath)

        val localDBAfter = localDB(testProjectPath)
        val globalDBAfter = globalDB(testProjectPath)
        localDBAfter.init()
        globalDBAfter.init()
        after { localDBAfter.close() }
        after { globalDBAfter.close() }

        val savedVersionLocalAfter = localDBAfter.dbVersion
        val creationTimeLocalAfter = localDBAfter.createdAt

        assertNotNull(savedVersionLocalAfter, "Version is null")
        assertEquals(newVersion, savedVersionLocalAfter)
        assertNotNull(creationTimeLocalAfter, "CreationTime is null")
        assertTrue { creationTimeLocalAfter > timeNow }

        val savedVersionGlobalAfter = globalDBAfter.dbVersion
        val creationTimeGlobalAfter = globalDBAfter.createdAt

        assertNotNull(savedVersionGlobalAfter, "Version is null")
        assertEquals(newVersion, savedVersionGlobalAfter)
        assertNotNull(creationTimeGlobalAfter, "CreationTime is null")
        assertTrue { creationTimeGlobalAfter > timeNow }
    }

    @Test
    fun onlyLocalLibrariesAreInLocalStorage(info: TestInfo) = rkTest {
        init(testMultiplatformProjectPath)
        val indexer = LsSourceCodeIndexer.instance(project)
        val ktPackage = indexer[KtPackageIndexMachine::class]
        val javaPackage = indexer[JavaPackageIndexMachine::class]
        languageServer.initializer.shutdown()

        val localDB = localDB(testMultiplatformProjectPath)
        val init = localDB.init()

        after {
            localDB.close()
        }

        assertFalse("Storage is wiped") { init.wiped }

        val allKtPackages = localDB.metas(ktPackage.namespace.attributed(ktPackage.attribute).name, ktPackage.attribute).toList()
        assertFalse("Kotlin packages are empty") { allKtPackages.isEmpty() }

        for (meta in allKtPackages) {
            val withoutProtocol = meta.cutProtocol
            assertTrue("Non-project kotlin source found in local storage: $meta") { withoutProtocol.startsWith(testMultiplatformProjectPath.toString()) }
        }

        val allJavaPackages = localDB.sequence(javaPackage.namespace.attributed(javaPackage.attribute).name, javaPackage.attribute).toList()

        assertTrue("Java packages in local storage exist") { allJavaPackages.isEmpty() }
    }

    @Test
    fun onlyGlobalLibrariesAreInGlobalStorage(info: TestInfo) = rkTest {
        init(testMultiplatformProjectPath)

        val indexer = LsSourceCodeIndexer.instance(project)
        val ktPackage = indexer[KtPackageIndexMachine::class]
        val javaPackage = indexer[JavaPackageIndexMachine::class]

        languageServer.initializer.shutdown()

        val globalDB = globalDB(testMultiplatformProjectPath)
        val wiped = globalDB.init()
        after {
            globalDB.close()
        }

        assertFalse("Storage wiped") { wiped.wiped }

        val allKtPackages = globalDB.metas(ktPackage.namespace.attributed(ktPackage.attribute).name, ktPackage.attribute).toList()
        assertFalse("Kotlin packages are empty") { allKtPackages.isEmpty() }

        for (meta in allKtPackages) {
            val withoutProtocol = meta.cutProtocol
            assertFalse("Local kotlin source found in global storage: $meta") { withoutProtocol.startsWith(testMultiplatformProjectPath.toString()) }
        }

        val allJavaPackages = globalDB.metas(javaPackage.namespace.attributed(javaPackage.attribute).name, javaPackage.attribute).toList()
        assertFalse("Java packages are empty") { allJavaPackages.isEmpty() }
        for (meta in allJavaPackages) {
            val withoutProtocol = meta.cutProtocol
            assertFalse("Local java source found in global storage: $meta") { withoutProtocol.startsWith(testMultiplatformProjectPath.toString()) }
        }
    }

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun globalIndexRecoveryWorks(info: TestInfo) {
        ourGlobalIndexesPath.deleteRecursively()

        rkTest {
            simulateClient(testProjectPath, info) {
                assertInitialized()
            }
        }

        val indexes = getGlobalIndexesDirs()
        assertFalse { indexes.isEmpty() }

        for (index in indexes) {
            val indexesPath = MDB.indexesPath(index)
            Files.write(indexesPath, byteArrayOf(1), StandardOpenOption.TRUNCATE_EXISTING)
        }

        val timeWas = System.currentTimeMillis()
        rkTest {
            simulateClient(testProjectPath, info) {
                highlight()
            }
        }
        val dirsAfter = getGlobalIndexesDirs()
        assertEquals(indexes.size, dirsAfter.size, "Indexes folder size is different from what it was")
        for (path in dirsAfter) {
            assertTrue(path.getLastModifiedTime().toMillis() > timeWas, "File wasn't modified after we corrupted it: $path")
        }

        val timeAfter = System.currentTimeMillis()
        rkTest {
            simulateClient(testProjectPath, info) {
                assertInitialized()
            }
        }
        val thirdDirs = getGlobalIndexesDirs()
        assertEquals(indexes.size, thirdDirs.size, "Indexes folder size is different from what it was")
        for (path in thirdDirs) {
            assertTrue(path.getLastModifiedTime().toMillis() < timeAfter, "File was modified after we recovered it: $path")
        }
    }

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun globalIndexMetadataRecoveryWorks(info: TestInfo) {
        ourGlobalIndexesPath.deleteRecursively()

        rkTest {
            simulateClient(testProjectPath, info) {
                assertInitialized()
            }
        }

        val globalIndexesDirs = getGlobalIndexesDirs()
        val metadataPath = ourGlobalIndexesPath.resolve(LibrariesRouter.CATALOGUE_DB)
        assertTrue(metadataPath.exists(), "No metadata file found")

        Files.write(metadataPath, byteArrayOf(1))

        val timeWas = System.currentTimeMillis()
        rkTest {
            simulateClient(testProjectPath, info) {
                highlight()
            }
        }
        val dirsAfter = getGlobalIndexesDirs()
        assertEquals(globalIndexesDirs.size, dirsAfter.size, "Indexes folder size is different from what it was")
        for (dirAfter in dirsAfter) {
            assertFalse("Directories intersect") { globalIndexesDirs.contains(dirAfter) }
            assertTrue(dirAfter.getLastModifiedTime().toMillis() > timeWas, "File wasn't modified after we corrupted it: $dirAfter")
        }
    }

    @Test
    fun localIndexRecoveryWorks(info: TestInfo) {
        rkTest {
            simulateClient(testProjectPath, info) {
                assertInitialized()
            }
        }

        val localIndexesDirs = getLocalIndexesDirs(testProjectPath)
        for (localIndexesDir in localIndexesDirs) {
            val indexFile = MDB.indexesPath(localIndexesDir)
            assertTrue(indexFile.exists(), "Local index file does not exist")
            Files.write(indexFile, byteArrayOf(1))
        }
        val timeWas = System.currentTimeMillis()

        rkTest {
            simulateClient(testProjectPath, info) {
                diagnose()
            }
        }

        val localIndexesDirsAfter = getLocalIndexesDirs(testProjectPath)
        assertEquals(localIndexesDirs.size, localIndexesDirsAfter.size, "Size of local indexes doesn't match of the size it previously was")
        for (path in localIndexesDirsAfter) {
            val lastModifiedTime = path.getLastModifiedTime().toMillis()
            assertTrue(lastModifiedTime > timeWas, "File wasn't modified after we corrupted it: $path")
        }
    }

    private fun assertInitialized() {
        assertTrue(AnalysisEntrypoint.services is SessionBasedServices, "Non successful initialization")
    }

    private fun getGlobalIndexesDirs(): List<Path> {
        return Files.list(ourGlobalIndexesPath).use { files ->
            files.filter { path ->
                Files.isDirectory(path) && path.fileName.toString().isUUID
            }.toList()
        }
    }

    private fun getLocalIndexesDirs(path: Path): List<Path> {
        return Files.list(DB.indexesPath(getLocalDBPath(path))).use { files ->
            files.filter { path ->
                Files.isDirectory(path)
            }.toList()
        }
    }

    private fun globalDB(root: Path): VersionedDB {
        val globalDB = VersionedDB(ourGlobalIndexesPath) {
            RouterDB(LibrariesRouter(project, ourGlobalIndexesPath, root, null))
        }
        return globalDB
    }

    private fun localDB(root: Path): VersionedDB {
        val localDBPath = getLocalDBPath(root)
        val localDB = VersionedDB(localDBPath) {
            RouterDB(ShardedRouter(project, localDBPath, false, 10))
        }
        return localDB
    }

    private fun getLocalDBPath(root: Path): Path = root.resolve(".unblockt").resolve("local")

    private fun assertCommonsIsHere(librariesMap: HTreeMap<String, Array<Any>>) {
        val commonsLib = librariesMap.keys.firstOrNull {
            it.endsWith(COMMONS_LIB)
        }
        assertNotNull(commonsLib)
    }

    private fun loadLibrariesMap(mdb: MapDB) = mdb.hashMap(LibrariesRouter.LIBRARIES_MAP)
        .keySerializer(Serializer.STRING)
        .valueSerializer(SerializerArrayTuple(Serializer.STRING, Serializer.BOOLEAN))
        .open()
}