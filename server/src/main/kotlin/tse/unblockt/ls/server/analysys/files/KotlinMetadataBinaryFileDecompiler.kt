// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

package tse.unblockt.ls.server.analysys.files

import com.intellij.openapi.fileTypes.BinaryFileDecompiler
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.analysis.decompiler.konan.FileWithMetadata
import org.jetbrains.kotlin.analysis.decompiler.konan.K2KlibMetadataDecompiler
import org.jetbrains.kotlin.analysis.decompiler.konan.KlibMetaFileType
import org.jetbrains.kotlin.library.metadata.KlibMetadataSerializerProtocol
import org.jetbrains.kotlin.library.metadata.KlibMetadataVersion
import org.jetbrains.kotlin.library.metadata.NullFlexibleTypeDeserializer
import org.jetbrains.kotlin.psi.stubs.KotlinStubVersions
import org.jetbrains.kotlin.serialization.js.DynamicTypeDeserializer

class KotlinMetadataBinaryFileDecompiler: K2KlibMetadataDecompiler<KlibMetadataVersion>(
    KlibMetaFileType,
    { KlibMetadataSerializerProtocol },
    DynamicTypeDeserializer,
    { KlibMetadataVersion.INSTANCE },
    { KlibMetadataVersion.INVALID_VERSION },
    KotlinStubVersions.KOTLIN_NATIVE_STUB_VERSION
), BinaryFileDecompiler {
    override fun decompile(p0: VirtualFile): CharSequence {
        val read: FileWithMetadata = doReadFile(p0) ?: throw IllegalArgumentException("Can't read file ${p0.path}")
        val compatible: FileWithMetadata.Compatible = read as? FileWithMetadata.Compatible ?: throw IllegalArgumentException("File is not compatible: ${p0.path}")
        return getDecompiledText(compatible, p0, KlibMetadataSerializerProtocol, NullFlexibleTypeDeserializer).text
    }
}