// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

package tse.unblockt.ls.server.project

import java.nio.file.Path

data class ProjectModel(
    val path: Path,
    val projects: List<GradleProject>,
    val javaHome: Path
)

data class GradleProject(
    val name: String,
    val path: Path,
    val dependencies: List<Dependency>,
    val buildFile: Path,
)

sealed class Dependency {
    data class Library(val name: String, val paths: List<Path>) : Dependency()
    data class Module(val name: String, val path: Path) : Dependency()
}