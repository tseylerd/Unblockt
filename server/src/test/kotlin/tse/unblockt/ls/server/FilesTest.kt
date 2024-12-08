// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

package tse.unblockt.ls.server

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo
import tse.unblockt.ls.server.framework.LsClientSimulator
import tse.unblockt.ls.server.framework.simulateClient
import tse.unblockt.ls.util.rkTest
import tse.unblockt.ls.util.testProjectPath
import java.nio.file.Files
import kotlin.io.path.readText
import kotlin.test.assertNotNull

class FilesTest {
    @Test
    fun renameIsNoticed(info: TestInfo) = rkTest {
        simulateClient(testProjectPath, info) {
            val myClassFile = modifiedFiles.single()
            val content = Files.readString(myClassFile)
            val newClassFile = rename(myClassFile, "MyClassRenamed.kt")
            val newContent = content.replace("MyClass", "MyClassRenamed")
            changeDocument(newClassFile, newContent, LsClientSimulator.ChangeType.DOCUMENT)

            val curContent = Files.readString(fileToWorkWith)
            val newContentForCurFile = curContent.replace("MyClass", "MyClassRenamed")
            changeDocument(fileToWorkWith, newContentForCurFile, LsClientSimulator.ChangeType.DOCUMENT)

            diagnose()
        }
    }

    @Test
    fun renamedFileIsInModule(info: TestInfo) = rkTest {
        simulateClient(testProjectPath, info) {
            val myClassFile = modifiedFiles.single()
            val content = Files.readString(myClassFile)
            val newClassFile = rename(myClassFile, "MyClassRenamed.kt")
            val newContent = content.replace("MyClass", "MyClassRenamed")
            changeDocument(newClassFile, newContent, LsClientSimulator.ChangeType.DOCUMENT)

            diagnose(newClassFile)
        }
    }

    @Test
    fun renameFolderIsNoticed(info: TestInfo) = rkTest {
        simulateClient(testProjectPath, info) {
            val myClassFile = modifiedFiles.find { it.endsWith("MyClass.kt") }
            assertNotNull(myClassFile)

            val parent = myClassFile.parent
            rename(parent, "renamed")

            val renamedFolder = parent.parent.resolve("renamed")
            val toChange = renamedFolder.resolve(myClassFile.fileName.toString())
            val readString = toChange.readText()
            val newContent = readString.replace("MyClass", "MyClassRenamed")
            val internalClassFile = renamedFolder.resolve("subempty").resolve("MyInternalClass.kt")
            val internalText = internalClassFile.readText()
            val internalRenamedClassText = internalText.replace("MyInternalClass", "MyInternalClassRenamed")
            changeDocument(toChange, newContent, LsClientSimulator.ChangeType.DOCUMENT)
            changeDocument(internalClassFile, internalRenamedClassText, LsClientSimulator.ChangeType.DOCUMENT)

            diagnose()
        }
    }

    @Test
    fun deleteFolderIsNoticed(info: TestInfo) = rkTest {
        simulateClient(testProjectPath, info) {
            val myClassFile = modifiedFiles.find { it.endsWith("MyClass.kt") }
            assertNotNull(myClassFile)

            val parent = myClassFile.parent
            delete(parent)
            diagnose()
        }
    }
}