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
private const val PROJECT_PREFIX = "project:"
private const val PROJECT_PATH_PREFIX = "project-directory:"
private const val SOURCE_SET_PREFIX = "source-set:"
private const val PLATFORM_PREFIX = "source-set:platform:"
private const val PROJECT_BUILD_FILE_PREFIX = "project-build-file:"
private const val DEPENDENCY_PREFIX = "dependency:"
private const val SOURCE_DEPENDENCY_PREFIX = "source:"
private const val DEPENDENCY_SOURCE_SET_PREFIX = "dependency:source-set:"
private const val LIBRARY_DEPENDENCY_PREFIX = "artifact:"
private const val ARTIFACT_PATH_PREFIX = "dependency:artifact:path:"
private const val PROJECT_DEPENDENCY_PREFIX = "project:"
private const val PROJECT_DEPENDENCY_PATH_PREFIX = "dependency:project:path:"
private const val JAVA_HOME_PATH_PREFIX = "java:home:"

class GradleProjectImporter: ProjectImporter {
    override fun import(path: Path, progressSink: (String) -> Unit): ProjectImportResult {
        val homeResult = getJDKHome()
        val home = homeResult.getOrNull()
        home ?: return ProjectImportResult.Failure(ProjectImportError.JavaIsNotConfigured(homeResult.exceptionOrNull()))

        if (!Files.exists(Paths.get(home))) {
            return ProjectImportResult.Failure(ProjectImportError.JavaIsNotConfigured(RuntimeException("Java is not found in $home")))
        }

        if (!isGradleProject(path)) {
            return ProjectImportResult.Failure(ProjectImportError.ProjectNotFound("Gradle project is not configured in root folder. Please, configure gradle project and try again"))
        }

        val script = AnalysisEntrypoint::class.java.getResource("/gradle/classpath.gradle.kts")!!.readText()
        val tempFile = Files.createTempFile("gradle", ".init.gradle.kts")
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
                        .withArguments("--no-configuration-cache", "--init-script", tempFile.absolutePathString())
                        .run()

                    stdout.toString().lines()
                        .filter { it.startsWith(PREFIX) }
                        .joinToString("\n") { it.substringAfter(PREFIX) }
                }
            val gradleModel = outToGradleModel(path, allOut)
            if (gradleModel.projects.isEmpty()) {
                ProjectImportResult.Failure(ProjectImportError.ErrorWithDescription("No Kotlin projects found. Configure Kotlin projects using Gradle and invoke Reload Gradle Project action."))
            } else {
                gradleModel.asProjectModel()
            }
        }
        catch (t: Throwable) {
            Logging.logger.error("Error importing project", t)
            ProjectImportResult.Failure(ProjectImportError.BuildSystemError(t))
        }
        finally {
            Files.delete(tempFile)
        }
    }
}

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

private fun GradleProjectModel.asProjectModel(): ProjectImportResult {
    return ProjectImportResult.Success(
        UBProjectModel(
            path = path,
            modules = modules(),
            javaHome = javaHome
        )
    )
}

private fun GradleProjectModel.modules(): List<UBModule> {
    return projects.flatMap { project ->
        val projectName = project.name
        listOf(UBModule(
            projectName,
            project.path,
            emptySet(),
            project.buildFile,
            emptySet(),
        )) + project.sourceSets.map { sourceSet: GradleSourceSet ->
            UBModule(
                projectName + "." + sourceSet.name,
                path = sourceSet.path,
                dependencies = sourceSet.dependencies.flatMap { dependency ->
                    when (dependency) {
                        is GradleDepenency.Library -> listOf(UBDependency.Library(
                            dependency.name,
                            dependency.paths,
                            dependency.sources
                        ))
                        is GradleDepenency.SourceSet -> {
                            val dependencyProject = projects.find { it.name == dependency.projectName }!!
                            val depSourceSet: GradleSourceSet = dependencyProject.sourceSets.find { it.name == dependency.name }!!
                            listOf(UBDependency.Module(
                                dependencyProject.name + "." + depSourceSet.name,
                                depSourceSet.path
                            ))
                        }
                        is GradleDepenency.Project -> {
                            val projectDepName = dependency.name
                            val projectDependsOn = projects.find { prj -> prj.name == projectDepName }
                            if (projectDependsOn == null) {
                                // usually means non-jvm (java) module dependency
                                logger.warn("Failed to find project with name $projectDepName")
                                emptyList()
                            } else {
                                if (projectDependsOn.name == project.name) {
                                    emptyList()
                                } else {
                                    val allSourceSets = filterSourceSetsForDependencies(sourceSet, projectDependsOn)
                                    allSourceSets.map { ss ->
                                        UBDependency.Module(projectDepName + "." + ss.name, ss.path)
                                    }
                                }
                            }
                        }
                    }
                }.toSet(),
                buildFile = project.buildFile,
                platforms = sourceSet.platforms
            )
        }
    }
}

private fun filterSourceSetsForDependencies(sourceSet: GradleSourceSet, projectDependsOn: GradleProject): List<GradleSourceSet> {
    val mainPlatform = mainPlatform(sourceSet) ?: return emptyList()
    return projectDependsOn.sourceSets.filter { ss ->
        if (ss.name.contains("test")) {
            return@filter false
        }
        val dependsOn = mainPlatform(ss) ?: return@filter false
        shouldDepend(mainPlatform, dependsOn)
    }
}

private fun shouldDepend(main: Platform, dependsOn: Platform): Boolean {
    if (main == dependsOn) {
        return true
    }
    if (dependsOn == Platform.common) {
        return true
    }
    return false
}

private fun mainPlatform(sourceSet: GradleSourceSet): Platform? {
    return when {
        sourceSet.platforms.isEmpty() -> null
        sourceSet.platforms.contains(Platform.common) -> Platform.common
        sourceSet.platforms.contains(Platform.jvm) -> Platform.jvm
        sourceSet.platforms.contains(Platform.androidJvm) -> Platform.androidJvm
        sourceSet.platforms.contains(Platform.js) -> Platform.js
        sourceSet.platforms.contains(Platform.wasm) -> Platform.wasm
        sourceSet.platforms.contains(Platform.native) -> Platform.native
        else -> null
    }
}

private fun outToGradleModel(path: Path, allOut: String): GradleProjectModel {
    val lines = allOut.lines()
    val projects = mutableSetOf<GradleProject>()
    val model = LinesAccessor(lines) {
        val javaHome = advance().substringAfter(JAVA_HOME_PATH_PREFIX)
        while (remains) {
            val project = parseGradleProject(path)
            projects.add(project)
        }
        GradleProjectModel(path, projects, Paths.get(javaHome))
    }
    return model
}

private fun LinesAccessor.parseGradleProject(root: Path): GradleProject {
    val projectName = advance().substringAfter(PROJECT_PREFIX)
    val projectPathStr = advance().substringAfter(PROJECT_PATH_PREFIX)
    val projectBuildFile = asPath(root, advance().substringAfter(PROJECT_BUILD_FILE_PREFIX))
    val projectPath = asPath(root, projectPathStr)
    val sourceSets = useWhile({ !it.startsWith(PROJECT_PREFIX) }) {
        parseSourceSets(root, projectPath)
    }
    return GradleProject(
        projectName,
        projectBuildFile,
        projectPath,
        sourceSets
    )
}

private fun LinesAccessor.parseSourceSets(root: Path, projectPath: Path): Set<GradleSourceSet> {
    val sourceSets = mutableSetOf<GradleSourceSet>()
    while (remains) {
        val sourceSetName = advance().substringAfter(SOURCE_SET_PREFIX)
        val platforms = mutableSetOf<Platform>()
        useWhile({ it.startsWith(PLATFORM_PREFIX) }) {
            platforms += when {
                remains -> Platform.valueOf(advance().substringAfter(PLATFORM_PREFIX))
                else -> Platform.common
            }
        }
        val dependencies = useWhile({ !it.startsWith(SOURCE_SET_PREFIX) }) {
            parseDependencies(root, projectPath)
        }
        sourceSets += GradleSourceSet(
            sourceSetName,
            projectPath.resolve("src").resolve(sourceSetName),
            dependencies,
            platforms
        )
    }
    return sourceSets
}

private fun LinesAccessor.parseDependencies(root: Path, projectPath: Path): Set<GradleDepenency> {
    val dependencies = mutableSetOf<GradleDepenency>()
    while (remains) {
        val depFirstLine = advance().substringAfter(DEPENDENCY_PREFIX)
        when {
            depFirstLine.startsWith(SOURCE_DEPENDENCY_PREFIX) -> {
                val projectName = depFirstLine.substringAfter(SOURCE_DEPENDENCY_PREFIX)
                val sourceSetName = advance().substringAfter(DEPENDENCY_SOURCE_SET_PREFIX)
                dependencies += GradleDepenency.SourceSet(projectName, sourceSetName)
            }

            depFirstLine.startsWith(LIBRARY_DEPENDENCY_PREFIX) -> {
                val name = depFirstLine.substringAfter(LIBRARY_DEPENDENCY_PREFIX)
                val paths = mutableListOf<Path>()
                useWhile({ it.startsWith(ARTIFACT_PATH_PREFIX) }) {
                    val path = advance().substringAfter(ARTIFACT_PATH_PREFIX)
                    paths.add(Paths.get(path))
                }
                dependencies += GradleDepenency.Library(name, paths, emptyList())
            }

            depFirstLine.startsWith(PROJECT_DEPENDENCY_PREFIX) -> {
                val name = depFirstLine.substringAfter(PROJECT_DEPENDENCY_PREFIX)
                val path = advance().substringAfter(PROJECT_DEPENDENCY_PATH_PREFIX)
                val projPath = asPath(root, path)
                dependencies += GradleDepenency.Project(name, projPath)
            }
        }
    }
    return dependencies
}

private fun asPath(root: Path, path: String): Path {
    if (path == ":") {
        return root
    }
    return root.resolve(Paths.get(path.substringAfter(":")))
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

private data class GradleProjectModel(
    val path: Path,
    val projects: Set<GradleProject>,
    val javaHome: Path,
)

private data class GradleProject(
    val name: String,
    val buildFile: Path,
    val path: Path,
    val sourceSets: Set<GradleSourceSet>
)

private data class GradleSourceSet(
    val name: String,
    val path: Path,
    val dependencies: Set<GradleDepenency>,
    val platforms: Set<Platform>
)

private sealed class GradleDepenency {
    data class Project(val name: String, val path: Path) : GradleDepenency()
    data class SourceSet(val projectName: String, val name: String) : GradleDepenency()
    data class Library(val name: String, val paths: List<Path>, val sources: List<Path>) : GradleDepenency()
}