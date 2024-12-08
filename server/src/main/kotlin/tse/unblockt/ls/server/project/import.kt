// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

package tse.unblockt.ls.server.project

import org.apache.logging.log4j.kotlin.logger
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.ProgressListener
import tse.unblockt.ls.server.analysys.AnalysisEntrypoint
import tse.unblockt.ls.server.util.Environment
import tse.unblockt.ls.server.util.Logging
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.TimeUnit
import kotlin.io.path.absolutePathString

private const val PREFIX = "unblockt:"
private const val NO_KOTLIN_PLUGIN = "no-kotlin-jvm-plugin"
private const val OTHER_JVM_PLUGIN = "non-jvm-plugins"
private const val PROJECT_PREFIX = "project:"
private const val TEST_CLASSPATH_PREFIX = "test-classpath"
private const val PROJECT_PATH_PREFIX = "project-directory:"
private const val PROJECT_BUILD_FILE_PREFIX = "project-build-file:"
private const val DEPENDENCY_NAME_PREFIX = "dependency:name:"
private const val DEPENDENCY_ARTIFACT_NAME_PREFIX = "dependency:artifact:name:"
private const val DEPENDENCY_ARTIFACT_PATH_PREFIX = "dependency:artifact:path:"
private const val DEPENDENCY_TYPE_PREFIX = "dependency:type:"
private const val COMPILE_CLASSPATH_PREFIX = "compile-classpath:"
private const val JAVA_HOME_PATH_PREFIX = "java:home:"

fun isGradleProject(path: Path): Boolean {
    return Files.list(path).use { s ->
        s.toList().any {
            it.fileName.toString() == "build.gradle.kts"
                    || it.fileName.toString() == "build.gradle"
                    || it.fileName.toString() == "settings.gradle.kts"
                    || it.fileName.toString() == "settings.gradle"
        }
    }
}

fun hasGradleWrapper(path: Path): Boolean {
    val wrapper = path.resolve("gradle").resolve("wrapper")
    if (!Files.exists(wrapper)) {
        return false
    }

    val list = Files.list(wrapper).use { it.toList() }
    if (list.size != 2) {
        return false
    }

    if (list.none { it.fileName.toString() == "gradle-wrapper.jar" } || list.none { it.fileName.toString() == "gradle-wrapper.properties" }) {
        return false
    }

    val gradlewFile = path.resolve("gradlew")
    return Files.exists(gradlewFile)
}

fun importGradleProject(path: Path, progressSink: (String) -> Unit): ProjectImportResult {
    val homeResult = getJDKHome()
    val home = homeResult.getOrNull()
    home ?: return ProjectImportResult.Failure(ProjectImportError.JavaIsNotConfigured(homeResult.exceptionOrNull()))

    if (!Files.exists(Paths.get(home))) {
        return ProjectImportResult.Failure(ProjectImportError.JavaIsNotConfigured(RuntimeException("Java is not found in $home")))
    }

    if (!isGradleProject(path)) {
        return ProjectImportResult.Failure(ProjectImportError.GradleProjectNotFound)
    }

    val script = AnalysisEntrypoint::class.java.getResource("/gradle/classpath.gradle")?.readText()
    val tempFile = Files.createTempFile("gradle", "model")
    Files.writeString(tempFile, script)

    return try {
        val gradleVersionToUse = getGradleVersionToUse(path)
        val allOut = GradleConnector.newConnector()
            .forProjectDirectory(path.toFile()).run {
                if (gradleVersionToUse != null) {
                    useGradleVersion(gradleVersionToUse)
                } else {
                    this
                }
            }
            .connect().use { connection ->
                val errors = ByteArrayOutputStream()
                val stdout = ByteArrayOutputStream()
                connection.newBuild()
                    .setStandardError(errors)
                    .setStandardOutput(stdout)
                    .setJavaHome(File(home))
                    .addProgressListener(ProgressListener { p0 -> progressSink(p0.description) })
                    .withArguments("-init-script", tempFile.absolutePathString())
                    .run()

                stdout.toString().lines()
                    .filter { it.startsWith(PREFIX) }
                    .joinToString("\n") { it.substringAfter(PREFIX) }
            }
        outToModel(path, allOut)
    }
    catch (t: Throwable) {
        Logging.logger.error("Error importing project", t)
        ProjectImportResult.Failure(ProjectImportError.GradleError(t))
    }
    finally {
        Files.delete(tempFile)
    }
}

fun getJDKHome(): Result<String> {
    return runCatching {
        val command = when(Environment.OS) {
            Environment.OperatingSystem.MAC_OS -> arrayOf("/usr/libexec/java_home")
            Environment.OperatingSystem.LINUX -> arrayOf("bash", "-c", "update-alternatives --list java | tail -1 | xargs dirname | xargs dirname")
            Environment.OperatingSystem.WINDOWS -> arrayOf("powershell.exe", "(get-command java).Path | Split-Path | Split-Path")
        }
        val exec = Runtime.getRuntime().exec(command)
        exec.waitFor(10, TimeUnit.SECONDS)
        exec.inputReader().readLine()
    }
}

fun getGradleVersionToUse(path: Path): String? {
    if (hasGradleWrapper(path) || hasGradleInstalled()) {
        return null
    }

    return "8.11"
}

fun hasGradleInstalled(): Boolean {
    val command = when(Environment.OS) {
        Environment.OperatingSystem.MAC_OS -> arrayOf("gradle", "--version")
        Environment.OperatingSystem.LINUX -> arrayOf("gradle", "--version")
        Environment.OperatingSystem.WINDOWS -> arrayOf("gradle", "--version")
    }
    return try {
        val exec = Runtime.getRuntime().exec(command)
        val exited = exec.waitFor(10, TimeUnit.SECONDS)
        if (!exited) {
            false
        } else {
            exec.exitValue() == 0
        }
    } catch (t: Throwable) {
        false
    }
}

private fun outToModel(path: Path, allOut: String): ProjectImportResult {
    if (allOut == NO_KOTLIN_PLUGIN) {
        return ProjectImportResult.Failure(ProjectImportError.ErrorWithDescription("Kotlin JVM plugin is not configured for the project. Please apply Kotlin JVM plugin in Gradle build file."))
    }
    if (allOut == OTHER_JVM_PLUGIN) {
        return ProjectImportResult.Failure(ProjectImportError.ErrorWithDescription("Non JVM Kotlin plugin found in project configuration. Only JVM Kotlin plugin is supported."))
    }

    val templates = mutableListOf<ProjectTemplate>()
    val lines = allOut.lines()
    val model = LinesAccessor(lines) {
        val javaHome = advance().substringAfter(JAVA_HOME_PATH_PREFIX)
        val javaHomePath = Paths.get(javaHome)
        while (remains) {
            val project = parseProjects(path)
            templates.addAll(project)
        }
        ProjectModel(
            path,
            templates.map { t ->
                t.build(templates)
            },
            javaHomePath
        )
    }
    return ProjectImportResult.Success(model)
}

private fun LinesAccessor.parseProjects(root: Path): List<ProjectTemplate> {
    val projectName = advance().substringAfter(PROJECT_PREFIX)
    val projectPath = advance().substringAfter(PROJECT_PATH_PREFIX)
    val projectBuildFile = asPath(root, advance().substringAfter(PROJECT_BUILD_FILE_PREFIX))
    val builder = DependencyBuilder()
    var isTest = false
    until({ !it.startsWith(PROJECT_PREFIX) }) { line ->
        when {
            line.startsWith(TEST_CLASSPATH_PREFIX) -> isTest = true
            line.startsWith(DEPENDENCY_NAME_PREFIX) -> builder.name(line.substringAfter(DEPENDENCY_NAME_PREFIX))
            line.startsWith(DEPENDENCY_TYPE_PREFIX) -> builder.type(isTest, DependencyBuilder.DependencyType.fromGradle(line.substringAfter(DEPENDENCY_TYPE_PREFIX)))
            line.startsWith(DEPENDENCY_ARTIFACT_NAME_PREFIX) -> builder.name(line.substringAfter(DEPENDENCY_ARTIFACT_NAME_PREFIX))
            line.startsWith(DEPENDENCY_ARTIFACT_PATH_PREFIX) -> {
                val path = line.substringAfter(DEPENDENCY_ARTIFACT_PATH_PREFIX)
                builder.path(isTest, Paths.get(path))
            }
        }
    }
    val projectRoot = asPath(root, projectPath)
    val result = mutableListOf<ProjectTemplate>()
    result += ProjectTemplate(
        projectName,
        projectRoot,
        DependencyBuilder(),
        projectBuildFile
    )
    result += ProjectTemplate(
        "$projectName.main",
        projectRoot.resolve("src").resolve("main"),
        builder.forMain(),
        projectBuildFile
    )
    result += ProjectTemplate(
        "$projectName.test",
        projectRoot.resolve("src").resolve("test"),
        builder.forTest(projectName),
        projectBuildFile
    )
    return result
}

private fun asPath(root: Path, path: String): Path {
    val substringAfter = path.substringAfter(COMPILE_CLASSPATH_PREFIX)
    if (substringAfter == ":") {
        return root
    }
    return root.resolve(Paths.get(substringAfter.substringAfter(":")))
}

class DependencyBuilder {
    data class DependencyKey(
        val test: Boolean,
        val name: String
    )

    private val map = mutableMapOf<DependencyKey, MutableDependency>()

    private var lastName: String? = null

    fun name(name: String) {
        lastName = name
    }

    fun path(isTest: Boolean, path: Path) {
        val byName = byName(isTest, lastName!!)
        if (byName.type == null) {
            byName.type = DependencyType.LIBRARY
        }
        if (byName.type == DependencyType.LIBRARY) {
             byName.paths.add(path)
        }
    }

    fun type(isTest: Boolean, type: DependencyType) {
        if (type == DependencyType.MODULE) {
            byName(isTest, lastName!!).type = type
        }
    }

    fun build(projects: List<ProjectTemplate>): List<Dependency> {
        return map.entries.map {
            it.value.build(projects)
        }
    }

    private fun byName(test: Boolean, name: String): MutableDependency {
        return map.computeIfAbsent(DependencyKey(test, name)) { key ->
            MutableDependency().apply {
                this.name = key.name
            }
        }
    }

    fun forMain(): DependencyBuilder {
        val depBuilder = DependencyBuilder()
        for ((key, value) in map) {
            if (key.test) {
                continue
            }
            val md = if (value.type == DependencyType.MODULE) {
                MutableDependency().apply {
                    name = value.name + ".main"
                    paths = value.paths
                    type = DependencyType.MODULE
                }
            } else {
                value
            }
            depBuilder.map += key to md
        }
        return depBuilder
    }

    fun forTest(currentModule: String): DependencyBuilder {
        val depBuilder = DependencyBuilder()
        for ((key, value) in map) {
            val mds = if (value.type == DependencyType.MODULE) {
                listOf(key.copy(test = true, name = key.name + ".main") to MutableDependency().apply {
                    name = value.name + ".main"
                    paths = value.paths
                    type = DependencyType.MODULE
                })
            } else if (!key.test) {
                continue
            } else {
                listOf(key to value)
            }
            for ((k, v) in mds) {
                depBuilder.map += k to v
            }

        }
        depBuilder.map += DependencyKey(true, "$currentModule.main") to MutableDependency().apply {
            name = "$currentModule.main"
            paths = mutableListOf()
            type = DependencyType.MODULE
        }
        return depBuilder
    }

    enum class DependencyType {
        LIBRARY,
        MODULE;
        companion object {
            fun fromGradle(substringAfter: String): DependencyType {
                return when (substringAfter) {
                    "module" -> MODULE
                    else -> LIBRARY
                }
            }
        }
    }
}

data class ProjectTemplate(val name: String, val path: Path, val deps: DependencyBuilder, val buildFile: Path) {
    fun build(projects: List<ProjectTemplate>): GradleProject {
        return GradleProject(name, path, deps.build(projects), buildFile)
    }
}

private class MutableDependency {
    var name: String? = null
    var paths = mutableListOf<Path>()
    var type: DependencyBuilder.DependencyType? = null

    fun build(projects: List<ProjectTemplate>): Dependency {
        return when (type) {
            DependencyBuilder.DependencyType.MODULE -> Dependency.Module(name!!, projects.find { it.name == name!! }!!.path)
            else -> Dependency.Library(name!!, paths)
        }
    }
}

private class LinesAccessor private constructor(val lines: List<String>) {
    companion object {
        operator fun <T> invoke(lines: List<String>, block: LinesAccessor.() -> T): T {
            return LinesAccessor(lines).block()
        }
    }

    var current: Int = 0
    val remains: Boolean
        get() = current < lines.size

    fun advance(): String {
        return lines[current++]
    }

    fun until(condition: (String) -> Boolean, call: (String) -> Unit) {
        while (current < lines.size && condition(lines[current])) {
            call(lines[current])
            current++
        }
    }
}

sealed class ProjectImportResult {
    data class Success(val model: ProjectModel): ProjectImportResult()
    data class Failure(val error: ProjectImportError): ProjectImportResult()
}

sealed class ProjectImportError {
    companion object {
        fun Throwable.collectMessagesMultiline(limit: Int = 5): String? {
            return buildString {
                var current: Throwable? = this@collectMessagesMultiline
                var remaining = limit
                var prevLine: String? = null
                while (current != null && remaining > 0) {
                    val nextLine = current.message
                    if (prevLine != nextLine) {
                        appendLine(nextLine)
                    }
                    prevLine = nextLine
                    current = current.cause
                    remaining--
                }
            }.takeIf { it.isNotBlank() }
        }
    }

    abstract val description: String

    data class JavaIsNotConfigured(val error: Throwable?): ProjectImportError() {
        override val description: String
            get() = error?.collectMessagesMultiline() ?: """
                Java is not configured. Please, install proper Java on the machine and try again.
            """.trimIndent()
    }

    data class GradleError(val throwable: Throwable): ProjectImportError() {
        override val description: String
            get() = throwable.collectMessagesMultiline() ?: "Failed to import Gradle project"
    }

    data object GradleProjectNotFound: ProjectImportError() {
        override val description: String
            get() = "Gradle project is not configured in root folder. Please, configure gradle project and try again"
    }

    data class FailedToImportProject(val error: Throwable): ProjectImportError() {
        override val description: String
            get() = error.collectMessagesMultiline() ?: "Failed to import project"
    }

    data class ErrorWithDescription(private val text: String): ProjectImportError() {
        override val description: String
            get() = text
    }
}
