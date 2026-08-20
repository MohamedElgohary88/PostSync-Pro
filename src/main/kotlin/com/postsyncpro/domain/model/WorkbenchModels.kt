package com.postsyncpro.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** HTTP methods supported by the request workbench. */
@Serializable
enum class WorkbenchHttpMethod {
    GET,
    POST,
    PUT,
    PATCH,
    DELETE,
    HEAD,
    OPTIONS,
    TRACE
}

@Serializable
data class WorkbenchRequestField(
    val key: String = "",
    val value: String = "",
    val enabled: Boolean = true,
    /** Marks values that must never be persisted or surfaced in diagnostics. */
    val sensitive: Boolean = false
)

@Serializable
data class WorkbenchVariable(
    val value: String = "",
    val enabled: Boolean = true,
    val secret: Boolean = false
)

@Serializable
enum class ApiKeyLocation { HEADER, QUERY, COOKIE }

@Serializable
sealed class WorkbenchAuth {
    @Serializable
    @SerialName("none")
    data object None : WorkbenchAuth()

    @Serializable
    @SerialName("bearer")
    data class Bearer(val token: String = "") : WorkbenchAuth()

    @Serializable
    @SerialName("basic")
    data class Basic(
        val username: String = "",
        val password: String = ""
    ) : WorkbenchAuth()

    @Serializable
    @SerialName("apiKey")
    data class ApiKey(
        val name: String = "X-API-Key",
        val value: String = "",
        val location: ApiKeyLocation = ApiKeyLocation.HEADER
    ) : WorkbenchAuth()
}

@Serializable
data class WorkbenchRequestDraft(
    val name: String = "Untitled request",
    val method: WorkbenchHttpMethod = WorkbenchHttpMethod.GET,
    val url: String = "",
    val query: List<WorkbenchRequestField> = emptyList(),
    val headers: List<WorkbenchRequestField> = emptyList(),
    val body: String = "",
    val bodyEnabled: Boolean = false,
    /** Treat the complete request body as a credential at display/persistence boundaries. */
    val bodySensitive: Boolean = false,
    val auth: WorkbenchAuth = WorkbenchAuth.None,
    val timeoutMillis: Long = 30_000
)

/**
 * A request after template expansion. [sensitiveValues] is deliberately not
 * serializable; it only exists long enough to redact errors and history.
 */
data class ResolvedWorkbenchRequest(
    val draft: WorkbenchRequestDraft,
    val sensitiveValues: Set<String> = emptySet()
)

sealed class VariableResolutionError {
    abstract val location: String

    data class Missing(
        val variableName: String,
        override val location: String
    ) : VariableResolutionError()

    data class Cycle(
        val variableChain: List<String>,
        override val location: String
    ) : VariableResolutionError()

    data class ExpansionLimit(
        val variableName: String,
        override val location: String
    ) : VariableResolutionError()

    data class SecretRequired(
        val secretName: String,
        override val location: String
    ) : VariableResolutionError()
}

sealed class VariableResolutionResult {
    data class Success(val request: ResolvedWorkbenchRequest) : VariableResolutionResult()
    data class Failure(val errors: List<VariableResolutionError>) : VariableResolutionResult()
}

@Serializable
data class WorkbenchResponse(
    val statusCode: Int,
    val statusText: String = "",
    val headers: List<WorkbenchRequestField> = emptyList(),
    val body: String = "",
    val contentType: String? = null,
    val durationMillis: Long,
    /** Declared Content-Length when present, otherwise the bytes received. */
    val sizeBytes: Long,
    val receivedBytes: Long,
    val truncated: Boolean,
    val startedAtEpochMillis: Long,
    val completedAtEpochMillis: Long,
    /** True when history keeps only a bounded display preview of the captured response. */
    val previewTruncated: Boolean = false
)

@Serializable
enum class WorkbenchFailureKind {
    INVALID_REQUEST,
    TIMEOUT,
    NETWORK,
    CANCELLED,
    RESPONSE_READ
}

@Serializable
data class WorkbenchExecutionFailure(
    val kind: WorkbenchFailureKind,
    /** This message must already be redacted by the executor. */
    val message: String
)

sealed class WorkbenchExecutionResult {
    /**
     * [response] is always safe for UI/state/history. [contractResponse] is a
     * transient in-process view used only by contract validation. It is kept
     * internal and deliberately excluded from toString to avoid secret leaks.
     */
    class Success(
        val response: WorkbenchResponse,
        internal val contractResponse: WorkbenchResponse = response
    ) : WorkbenchExecutionResult() {
        override fun toString(): String = "Success(response=$response)"
    }

    data class Failure(val error: WorkbenchExecutionFailure) : WorkbenchExecutionResult()
}

@Serializable
enum class WorkbenchExecutionPhase {
    IDLE,
    PREPARED,
    RESOLVING,
    IN_FLIGHT,
    RESPONSE_RECEIVED,
    CONTRACT_VALIDATING,
    CONTRACT_VALIDATED,
    ASSERTIONS_EVALUATING,
    ASSERTIONS_EVALUATED,
    HISTORY_SAVING,
    HISTORY_SAVED,
    HISTORY_SAVE_FAILED,
    FAILED,
    CANCELLED
}

@Serializable
enum class WorkbenchExecutionOutcome { NONE, SUCCEEDED, FAILED, CANCELLED }

@Serializable
enum class WorkbenchContractValidationState { NOT_REQUESTED, PENDING, COMPLETED, FAILED }

@Serializable
enum class WorkbenchAssertionEvaluationState { NOT_REQUESTED, PENDING, COMPLETED, FAILED }

@Serializable
enum class WorkbenchHistoryPersistenceState { NOT_ATTEMPTED, PENDING, SAVED, FAILED }

@Serializable
data class WorkbenchExecutionLifecycle(
    val executionId: Long? = null,
    val phase: WorkbenchExecutionPhase = WorkbenchExecutionPhase.IDLE,
    val outcome: WorkbenchExecutionOutcome = WorkbenchExecutionOutcome.NONE,
    val contractValidation: WorkbenchContractValidationState = WorkbenchContractValidationState.NOT_REQUESTED,
    val assertionEvaluation: WorkbenchAssertionEvaluationState = WorkbenchAssertionEvaluationState.NOT_REQUESTED,
    val historyPersistence: WorkbenchHistoryPersistenceState = WorkbenchHistoryPersistenceState.NOT_ATTEMPTED,
    val contractPassed: Boolean? = null,
    val assertionsPassed: Boolean? = null,
    val preparedAtEpochMillis: Long? = null,
    val inFlightAtEpochMillis: Long? = null,
    val responseReceivedAtEpochMillis: Long? = null,
    val contractValidatedAtEpochMillis: Long? = null,
    val assertionsEvaluatedAtEpochMillis: Long? = null,
    val completedAtEpochMillis: Long? = null,
    val historySavedAtEpochMillis: Long? = null
)

@Serializable
enum class ContractViolationSeverity { ERROR, WARNING }

@Serializable
data class ContractViolation(
    val code: String,
    val message: String,
    val jsonPath: String = "$",
    val severity: ContractViolationSeverity = ContractViolationSeverity.ERROR
)

@Serializable
data class WorkbenchOperationRef(
    val path: String,
    val method: String
)

data class PreparedWorkbenchRequest(
    val draft: WorkbenchRequestDraft,
    val variables: Map<String, WorkbenchVariable>
)

@Serializable
data class WorkbenchHistoryEntry(
    val id: String,
    val createdAtEpochMillis: Long,
    val request: WorkbenchRequestDraft,
    val variables: Map<String, WorkbenchVariable> = emptyMap(),
    val response: WorkbenchResponse? = null,
    val failure: WorkbenchExecutionFailure? = null,
    val contractViolations: List<ContractViolation> = emptyList(),
    val assertions: List<WorkbenchAssertion> = emptyList(),
    val assertionResults: List<WorkbenchAssertionResult> = emptyList(),
    val operation: WorkbenchOperationRef? = null,
    val lifecycle: WorkbenchExecutionLifecycle? = null,
    /** A truncated request preview must be restored explicitly before replay. */
    val requestPreviewTruncated: Boolean = false
)
