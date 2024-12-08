// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

package tse.unblockt.ls.server.analysys.project.module

import com.intellij.openapi.vfs.VirtualFile

interface LsScope {
    val allFilesInModule: Set<VirtualFile>
}