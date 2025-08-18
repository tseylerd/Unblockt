// Copyright 2024 Dmitrii Tseiler. Licensed under the Apache 2.0 Licence

package tse.unblockt.ls.server.project

import java.nio.file.Path

data class UBProjectModel(
    val path: Path,
    val modules: List<UBModule>,
    val javaHome: Path
)

data class UBModule(
    val name: String,
    val path: Path,
    val dependencies: Set<UBDependency>,
    val buildFile: Path,
    val platforms: Set<Platform>,
)

enum class Platform {
    common,
    jvm,
    js,
    androidJvm,
    native,
    wasm;
}

sealed class UBDependency {
    data class Library(val name: String, val paths: List<Path>, val sources: List<Path>) : UBDependency()
    data class Module(val name: String, val path: Path) : UBDependency()
}