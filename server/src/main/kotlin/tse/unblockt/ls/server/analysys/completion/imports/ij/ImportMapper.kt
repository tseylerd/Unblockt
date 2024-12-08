// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package tse.unblockt.ls.server.analysys.completion.imports.ij

import org.jetbrains.kotlin.config.ApiVersion
import org.jetbrains.kotlin.name.FqName

class ImportMapper {
    companion object {
        private val UTIL_TO_COLLECTIONS
            get() = createPackageMapping(
                javaPackage = "java.util",
                kotlinPackage = "kotlin.collections",
                names = listOf(
                    "RandomAccess",
                    "ArrayList",
                    "LinkedHashMap",
                    "HashMap",
                    "LinkedHashSet",
                    "HashSet",
                ),
            )

        private val LANG_TO_TEXT
            get() = createPackageMapping(
                javaPackage = "java.lang",
                kotlinPackage = "kotlin.text",
                names = listOf("Appendable", "StringBuilder"),
            )

        private val CHARSET_TO_TEXT
            get() = createPackageMapping(
                javaPackage = "java.nio.charset",
                kotlinPackage = "kotlin.text",
                version = ApiVersion.KOTLIN_1_4,
                names = listOf("CharacterCodingException"),
            )

        private val KOTLIN_JVM_TO_KOTLIN
            get() = createPackageMapping(
                javaPackage = "kotlin.jvm",
                kotlinPackage = "kotlin",
                version = ApiVersion.KOTLIN_1_4,
                names = listOf("Throws"),
            )

        private val LANG_TO_KOTLIN
            get() = createPackageMapping(
                javaPackage = "java.lang",
                kotlinPackage = "kotlin",
                names = listOf(
                    "Error",
                    "Exception",
                    "RuntimeException",
                    "IllegalArgumentException",
                    "IllegalStateException",
                    "IndexOutOfBoundsException",
                    "UnsupportedOperationException",
                    "ArithmeticException",
                    "NumberFormatException",
                    "NullPointerException",
                    "ClassCastException",
                    "AssertionError",
                ),
            )

        private val UTIL_TO_KOTLIN
            get() = createPackageMapping(
                javaPackage = "java.util",
                kotlinPackage = "kotlin",
                names = listOf(
                    "NoSuchElementException",
                    "ConcurrentModificationException",
                    "Comparator",
                ),
            )

        private fun createPackageMapping(
            javaPackage: String,
            kotlinPackage: String,
            version: ApiVersion = ApiVersion.Companion.KOTLIN_1_3,
            names: List<String>,
        ): Map<FqName, FqNameWithVersion> = names.associate {
            FqName("$javaPackage.$it") to FqNameWithVersion(FqName("$kotlinPackage.$it"), version)
        }

        private val javaToKotlinMap: Map<FqName, FqNameWithVersion> =
            UTIL_TO_COLLECTIONS +
                    LANG_TO_TEXT +
                    CHARSET_TO_TEXT +
                    KOTLIN_JVM_TO_KOTLIN +
                    LANG_TO_KOTLIN +
                    UTIL_TO_KOTLIN

        fun findCorrespondingKotlinFqName(fqName: FqName, availableVersion: ApiVersion): FqName? {
            return javaToKotlinMap[fqName]?.takeIf { it.version <= availableVersion }?.fqName
        }
    }
}

private data class FqNameWithVersion(val fqName: FqName, val version: ApiVersion)