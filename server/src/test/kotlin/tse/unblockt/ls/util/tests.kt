// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

@file:Suppress("OPT_IN_USAGE")

package tse.unblockt.ls.util

import com.intellij.openapi.project.DefaultProjectFactory
import com.intellij.openapi.project.Project
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.core.config.Configurator
import org.apache.logging.log4j.core.config.builder.api.ConfigurationBuilderFactory
import org.junit.jupiter.api.TestInfo
import tse.unblockt.ls.protocol.*
import tse.unblockt.ls.rpc.RPCMethodCall
import tse.unblockt.ls.rpc.Transport
import tse.unblockt.ls.server.KotlinLanguageServer
import tse.unblockt.ls.server.analysys.completion.COMPLETION_ITEMS_PROPERTY_KEY
import tse.unblockt.ls.server.analysys.files.Offsets
import tse.unblockt.ls.server.analysys.text.OffsetBasedEdit
import tse.unblockt.ls.server.analysys.text.applyEdits
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.test.assertEquals

private const val OVERWRITE_TEST_DATA: Boolean = false

fun rkTest(block: suspend RkTestEnvironment.() -> Unit) {
    System.setProperty(COMPLETION_ITEMS_PROPERTY_KEY, "10000")

    configureStdOutLogger()
    coroutineTest {
        val toSend = Channel<String>()
        val toReceive = Channel<String>()
        val clientCallsChannel = Channel<RPCMethodCall<*, *>>(Channel.UNLIMITED)
        val factory = TestLanguageClientFactory(clientCallsChannel)
        val lsJob = launch {
            runLanguageServer("Kotlin for VSCode Tests", Transport.Channel(toSend, toReceive), factory) { client ->
                KotlinLanguageServer(client)
            }
        }
        val ls = TestLanguageServer(toSend, toReceive)
        val rkTestEnvironment = RkTestEnvironment(ls, toSend, toReceive, clientCallsChannel, this)
        try {
            rkTestEnvironment.block()
        } finally {
            ls.initializer.shutdown()
            ls.service.pollAllRequests()
            lsJob.cancelAndJoin()
        }
    }
}

fun coroutineTest(block: suspend CoroutineScope.() -> Unit) {
    runBlocking(Dispatchers.IO.limitedParallelism(5)) {
        try {
            block()
        } catch (t: Throwable) {
            cancel("Cancelling", t)
        }
    }
}

data class RkTestEnvironment(
    val languageServer: LanguageServer,
    val sendChannel: SendChannel<String>,
    val receiveChannel: ReceiveChannel<String>,
    val clientsCallChannel: Channel<RPCMethodCall<*, *>>,
    val scope: CoroutineScope,
) {
    var root: Path? = null
}

suspend fun RkTestEnvironment.init(path: Path) {
    root = path
    languageServer.initializer.initialize(
        InitializationRequestParameters(
            0,
            path.toAbsolutePath().normalize().toString(),
            workDoneToken = ProgressToken("test")
        )
    )
    languageServer.initializer.initialized()
    languageServer.service.pollAllRequests()
}

val project: Project
    get() = DefaultProjectFactory.getInstance().defaultProject

val testProjectPath: Path
    get() {
        return Paths.get(".").resolve("testData").resolve("TestKotlin").toAbsolutePath().normalize()
    }

val testAndroidProjectPath: Path
    get() {
        return Paths.get(".").resolve("testData").resolve("TestAndroidProject").toAbsolutePath().normalize()
    }

val testMultiplatformProjectPath: Path
    get() {
        return Paths.get(".").resolve("testData").resolve("TestMultiplatform").toAbsolutePath().normalize()
    }

val fastProjectPath: Path
    get() {
        return Paths.get(".").resolve("testData").resolve("FastProject").toAbsolutePath().normalize()
    }

val selfPath: Path
    get() {
        return Paths.get("../.").toAbsolutePath().normalize()
    }

val emptyProjectPath: Path
    get() {
        return Paths.get(".").resolve("testData").resolve("EmptyProject").toAbsolutePath().normalize()
    }

val emptyProjectWithGradlePath: Path
    get() {
        return Paths.get(".").resolve("testData").resolve("EmptyProjectWithGradle").toAbsolutePath().normalize()
    }

val emptyProjectWithGradleWithoutJVMPath: Path
    get() {
        return Paths.get(".").resolve("testData").resolve("EmptyProjectWithGradleWithoutJVM").toAbsolutePath().normalize()
    }

val emptyProjectWithGradleWithJVMPath: Path
    get() {
        return Paths.get(".").resolve("testData").resolve("EmptyProjectWithGradleWithJVM").toAbsolutePath().normalize()
    }

val testDataPath: Path
    get() {
        return Paths.get(".").resolve("testData").resolve("expected").toAbsolutePath().normalize()
    }

private val possibleSuffixes: List<String> = listOf(
    "After.json",
    ".kt",
    "After.kt",
    ".json",
    ".txt",
    "After.txt",
)

fun assertEqualsWithFile(content: String, info: TestInfo) {
    val testDirectory = info.findDirectory
    val methodName = info.testMethod.get().name
    val allPossibleFiles = possibleSuffixes.map {
        testDirectory.resolve("$methodName$it")
    }
    val existingFile = allPossibleFiles.firstOrNull { Files.exists(it) } ?: allPossibleFiles.first().also {
        Files.write(it, content.toByteArray())
    }
    if (OVERWRITE_TEST_DATA) {
        Files.write(existingFile, content.toByteArray())
        return
    }
    val text = Files.readString(existingFile)
    assertEquals(text, content)
}

val TestInfo.findDirectory: Path
    get() {
        val testClass = testClass.get()
        val dirName = testClass.simpleName
        val pack = testClass.packageName.split(".")
        return pack.fold(testDataPath) { acc, cur -> acc.resolve(cur) }.resolve(dirName)
    }

val TestInfo.findModificationsDirectory: Path
    get() {
        return findDirectory.resolve(testMethod.get().name)
    }

val TestInfo.findInitial: Path
    get() {
        val dir = findDirectory
        return dir.resolve(testMethod.get().name + "Before.kt")
    }

fun wholeText(contentBefore: String) =
    Range(Offsets.offsetToPosition(0, contentBefore), Offsets.offsetToPosition(contentBefore.length, contentBefore))

fun projectTokens(file: Path, semanticTokens: SemanticTokens?): String {
    val content = Files.readString(file)
    semanticTokens ?: return content

    val markup = buildEdits(content, semanticTokens).sortedBy { it.start }
    return applyEdits(markup, content)
}

fun projectDiagnostics(content: String, report: DocumentDiagnosticReport?): String {
    report ?: return content
    val markup = buildEdits(content, report)
    return applyEdits(markup, content)
}

private fun buildEdits(content: String, report: DocumentDiagnosticReport): List<OffsetBasedEdit> {
    val items = report.items
    val lines = content.lines()
    val lineToStartOffset = mutableMapOf<Int, Int>()
    var currentOffset = 0
    for (i in lines.indices) {
        lineToStartOffset[i] = currentOffset
        currentOffset += lines[i].length + 1
    }
    val markup = items.flatMap { item ->
        val startOffset = Offsets.positionToOffset(item.range.start) {
            lineToStartOffset[it]!!
        }
        val endOffset = Offsets.positionToOffset(item.range.end) {
            lineToStartOffset[it]!!
        }
        listOf(
            OffsetBasedEdit(startOffset, startOffset, open(item.message)),
            OffsetBasedEdit(endOffset, endOffset, close(item.message)),
        )
    }
    return markup
}

private fun buildEdits(content: String, t: SemanticTokens): List<OffsetBasedEdit> {
    val tokens = t.data.asSequence().chunked(5)
    var line = 0
    var column = 0
    var offset = 0
    val lines = content.lines()
    val result = mutableListOf<OffsetBasedEdit>()
    for ((lineOffset, columnOffset, length, typeOrdinal, _) in tokens) {
        val nextLine = line + lineOffset
        val nextColumn = when {
            nextLine == line -> column + columnOffset
            else -> columnOffset
        }
        var lengthUntilNextOffset = 0
        for (curLineIdx in line..nextLine) {
            val curLine = lines[curLineIdx]
            val curLineStartOffset = if (curLineIdx == line) {
                column
            } else {
                0
            }
            val curLineEndOffset = if (curLineIdx == nextLine) {
                nextColumn
            } else {
                curLine.length + if (curLineIdx == lines.size - 1) 0 else 1
            }
            val diff = curLineEndOffset - curLineStartOffset
            lengthUntilNextOffset += diff
        }
        offset += lengthUntilNextOffset
        line = nextLine
        column = nextColumn
        val tokenType = SemanticTokenType.byOrdinal(typeOrdinal)
        result += OffsetBasedEdit(offset, offset, open(tokenType.name.lowercase()))
        result += OffsetBasedEdit(offset + length, offset + length, close(tokenType.name.lowercase()))
    }
    return result
}

private fun open(text: String): String {
    return "<$text>"
}

private fun close(text: String): String {
    return "</$text>"
}

private fun configureStdOutLogger() {
    val builder = ConfigurationBuilderFactory.newConfigurationBuilder()
    builder.add(
        builder.newAppender("CONSOLE", "Console")
            .add(
                builder.newLayout("PatternLayout")
                    .addAttribute("pattern", "%d{yyyy-MM-dd HH:mm:ss.SSS} [%t] %-5level %logger{36} - %msg%n")
            )
    )
    val rootLogger = builder.newRootLogger(Level.INFO)
    rootLogger.add(builder.newAppenderRef("CONSOLE"))
    builder.add(rootLogger)
    Configurator.initialize(builder.build())
}