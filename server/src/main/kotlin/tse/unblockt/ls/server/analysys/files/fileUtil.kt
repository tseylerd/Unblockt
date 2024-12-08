// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

package tse.unblockt.ls.server.analysys.files

import com.intellij.openapi.util.io.FileUtil
import tse.unblockt.ls.protocol.Uri
import tse.unblockt.ls.server.fs.asPath
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isDirectory

val Uri.isKotlin: Boolean get() {
    return FileUtil.extensionEquals(data, "kt")
}

val Path.isKotlin: Boolean get() {
    return FileUtil.extensionEquals(toString(), "kt")
}

val Uri.isFolder: Boolean get() {
    return asPath().isDirectory()
}

val Uri.isFile: Boolean
    get() = Files.isRegularFile(asPath())

val Uri.isBuildFile: Boolean
    get() = data.endsWith("build.gradle.kts") ||
            data.endsWith("settings.gradle.kts") ||
            data.endsWith("build.gradle") ||
            data.endsWith("settings.gradle") ||
            data.endsWith("gradle.properties")

val Path.isBuildFile: Boolean
    get() = endsWith("build.gradle.kts") ||
            endsWith("settings.gradle.kts") ||
            endsWith("build.gradle") ||
            endsWith("settings.gradle") ||
            endsWith("gradle.properties")

val Uri.isSupportedByLanguageServer: Boolean
    get() = isBuildFile || isKotlin

val Path.isSupportedByLanguageServer: Boolean
    get() = isBuildFile || isKotlin