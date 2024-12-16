// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

package tse.unblockt.ls.server.project


sealed class ProjectImportResult {
    data class Success(val model: UBProjectModel): ProjectImportResult()
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

    data class BuildSystemError(val throwable: Throwable): ProjectImportError() {
        override val description: String
            get() = throwable.collectMessagesMultiline() ?: "Failed to import project"
    }

    data class ProjectNotFound(private val message: String? = null): ProjectImportError() {
        override val description: String
            get() = message ?: "Project is not configured in root folder. Please, configure project and try again"
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
