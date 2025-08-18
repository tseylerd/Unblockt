// Copyright 2024 Dmitrii Tseiler. Licensed under the Apache 2.0 Licence

package tse.unblockt.ls.server.analysys.project.module

import com.intellij.openapi.vfs.VirtualFile

interface LsScope {
    val allFilesInModule: Set<VirtualFile>
}