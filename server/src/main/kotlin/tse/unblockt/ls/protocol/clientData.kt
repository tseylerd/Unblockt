// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

package tse.unblockt.ls.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ProgressParams(val token: ProgressToken, val value: ProgressEvent)

@Serializable
data class WorkDoneProgressCreateParams(val token: ProgressToken)

@Serializable
data class ProgressEvent(
    val kind: ProgressEventKind,
    val title: String?,
    val cancellable: Boolean?,
    val message: String?,
    val percentage: Int? = null,
)

@Suppress("FunctionName")
fun ProgressBegin(
    cancellable: Boolean,
    title: String,
) = ProgressEvent(ProgressEventKind.BEGIN, title, cancellable, null, null)

@Suppress("FunctionName")
fun ProgressReport(
    cancellable: Boolean? = null,
    message: String? = null,
    percentage: Int? = null
) = ProgressEvent(ProgressEventKind.REPORT, null, cancellable, message, percentage)

@Suppress("FunctionName")
fun ProgressEnd(
    message: String? = null
) = ProgressEvent(ProgressEventKind.END, null, null, message, null)

@Serializable
enum class ProgressEventKind {
    @SerialName("begin") BEGIN,
    @SerialName("report") REPORT,
    @SerialName("end") END
}

@Serializable
data class ApplyEditsParams(
    val label: String? = null,
    val edit: WorkspaceEdit
)

@Serializable
data class ApplyEditsResult(
    val applied: Boolean,
    val failureReason: String? = null,
    val failedChange: Int? = null,
)

@Serializable
data class WorkspaceEdit(
    val changes: Map<Uri, List<TextEdit>>
)

@Serializable
data class MessageParams(
    val value: String,
    val type: MessageType,
)

enum class MessageType{
    INFO,
    ERROR,
}

@Serializable
data class HealthStatusInformation(
    val text: String,
    val message: String,
    val status: HealthStatus,
)

@Serializable
enum class HealthStatus {
    HEALTHY,
    WARNING,
    MESSAGE,
    ERROR
}

@Serializable
data class RegistrationParams(
    val registrations: List<Registration>
)

@Serializable
data class Registration(
    val id: String,
    val method: String,
    val registerOptions: RegistrationOptions?
)

@Serializable
sealed interface RegistrationOptions {
    @Serializable
    data class DidChangeWatchedFilesRegistrationOptions(
        val watchers: List<FileSystemWatcher>
    ): RegistrationOptions
}

@Serializable
data class FileSystemWatcher(
    val globPattern: String,
    val watchKind: WatchKind? = null,
)

@Serializable
@JvmInline
value class WatchKind private constructor(val kind: Int) {
    companion object {
        val CREATE = WatchKind(1)
        val CHANGE = WatchKind(2)
        val DELETE = WatchKind(4)
    }
}