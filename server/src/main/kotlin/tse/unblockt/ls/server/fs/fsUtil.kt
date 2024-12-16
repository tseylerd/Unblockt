// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

package tse.unblockt.ls.server.fs

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.local.CoreLocalVirtualFile
import org.jetbrains.kotlin.cli.common.localfs.KotlinLocalVirtualFile
import tse.unblockt.ls.protocol.Uri
import tse.unblockt.ls.server.util.Environment
import java.net.URL
import java.nio.file.Path
import java.nio.file.Paths

@Suppress("DEPRECATION")
fun Uri.asPath(): Path {
    val path = URL(data).path.run {
        if (Environment.OS == Environment.OperatingSystem.WINDOWS) {
            trimStart('/')
        } else {
            this
        }
    }
    return Paths.get(path)
}

val Uri.asIJUrl: String
    get() {
        val protocol = data.substringBefore(':')
        val path = asPath().toString()
        return VirtualFileManager.constructUrl(protocol, path)
    }

val VirtualFile.asUri: Uri
    get() {
        assert(isRegularFile)

        return Uri("file:///$path")
    }

val VirtualFile.isRegularFile: Boolean
    get() = this is KotlinLocalVirtualFile || this is CoreLocalVirtualFile

val Path.uri: Uri
    get() = Uri("file:///$this")

val String.cutProtocol
    get() = when {
        startsWith("file://") -> substringAfter("file://")
        startsWith("jrt://") -> substringAfter("jrt://")
        startsWith("jar://") -> substringAfter("jar://")
        else -> this
    }