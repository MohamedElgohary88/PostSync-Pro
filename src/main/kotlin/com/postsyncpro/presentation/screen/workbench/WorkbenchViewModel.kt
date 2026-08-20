package com.postsyncpro.presentation.screen.workbench

import com.postsyncpro.domain.model.ContractViolation
import com.postsyncpro.domain.model.ContractViolationSeverity
import com.postsyncpro.domain.model.SwaggerSpec
import com.postsyncpro.domain.model.VariableResolutionError
import com.postsyncpro.domain.model.VariableResolutionResult
import com.postsyncpro.domain.model.WorkbenchAssertion
import com.postsyncpro.domain.model.WorkbenchAssertionEvaluationState
import com.postsyncpro.domain.model.WorkbenchAssertionOutcome
import com.postsyncpro.domain.model.WorkbenchAssertionReport
import com.postsyncpro.domain.model.WorkbenchAssertionResult
import com.postsyncpro.domain.model.WorkbenchAuth
import com.postsyncpro.domain.model.WorkbenchContractValidationState
import com.postsyncpro.domain.model.WorkbenchEnvironment
import com.postsyncpro.domain.model.WorkbenchEnvironmentSnapshot
import com.postsyncpro.domain.model.WorkbenchExecutionFailure
import com.postsyncpro.domain.model.WorkbenchExecutionLifecycle
import com.postsyncpro.domain.model.WorkbenchExecutionOutcome
import com.postsyncpro.domain.model.WorkbenchExecutionPhase
import com.postsyncpro.domain.model.WorkbenchExecutionResult
import com.postsyncpro.domain.model.WorkbenchFailureKind
import com.postsyncpro.domain.model.WorkbenchHistoryEntry
import com.postsyncpro.domain.model.WorkbenchHistoryPersistenceState
import com.postsyncpro.domain.model.WorkbenchOperationRef
import com.postsyncpro.domain.model.WorkbenchRequestDraft
import com.postsyncpro.domain.model.WorkbenchRequestField
import com.postsyncpro.domain.model.WorkbenchResponse
import com.postsyncpro.domain.model.WorkbenchSavedCollection
import com.postsyncpro.domain.model.WorkbenchSavedRequest
import com.postsyncpro.domain.model.WorkbenchSavedWorkspace
import com.postsyncpro.domain.model.WorkbenchSecretLocation
import com.postsyncpro.domain.model.WorkbenchVariable
import com.postsyncpro.domain.repository.WorkbenchEnvironmentRepository
import com.postsyncpro.domain.repository.WorkbenchHistoryRepository
import com.postsyncpro.domain.repository.WorkbenchSavedRequestRepository
import com.postsyncpro.domain.repository.WorkbenchStorageCorruptionException
import com.postsyncpro.domain.usecase.EvaluateWorkbenchAssertionsUseCase
import com.postsyncpro.domain.usecase.ExecuteWorkbenchRequestUseCase
import com.postsyncpro.domain.usecase.PrepareContractRequestUseCase
import com.postsyncpro.domain.usecase.ResolveWorkbenchVariablesUseCase
import com.postsyncpro.domain.usecase.SecretRedactor
import com.postsyncpro.domain.usecase.ValidateContractResponseUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

data class WorkbenchState(
    val draft: WorkbenchRequestDraft = WorkbenchRequestDraft(),
    val variables: Map<String, WorkbenchVariable> = emptyMap(),
    val contract: SwaggerSpec? = null,
    val contractSourceName: String? = null,
    val operations: List<WorkbenchOperationRef> = emptyList(),
    val selectedOperation: WorkbenchOperationRef? = null,
    /** Compatibility flag: true only while resolving/sending the HTTP request. */
    val isSending: Boolean = false,
    val response: WorkbenchResponse? = null,
    val executionFailure: WorkbenchExecutionFailure? = null,
    val variableErrors: List<VariableResolutionError> = emptyList(),
    val contractViolations: List<ContractViolation> = emptyList(),
    val assertions: List<WorkbenchAssertion> = emptyList(),
    val assertionReport: WorkbenchAssertionReport? = null,
    val history: List<WorkbenchHistoryEntry> = emptyList(),
    val isHistoryLoading: Boolean = true,
    val errorMessage: String? = null,
    /** Canonical ribbon state; never infer execution success from response/history. */
    val lifecycle: WorkbenchExecutionLifecycle = WorkbenchExecutionLifecycle(),
    /** Credentials removed from a history entry and still required before replay. */
    val requiredSecretRestorations: Set<String> = emptySet(),
    /** A bounded request preview cannot be sent until replaced with the full draft. */
    val replayRequiresFullRequest: Boolean = false,
    val environmentSnapshot: WorkbenchEnvironmentSnapshot = WorkbenchEnvironmentSnapshot(),
    val environmentDirty: Boolean = false,
    val isEnvironmentLoading: Boolean = false,
    val isEnvironmentSaving: Boolean = false,
    val environmentErrorMessage: String? = null,
    val environmentRecoveryAvailable: Boolean = false,
    val savedWorkspace: WorkbenchSavedWorkspace = WorkbenchSavedWorkspace(),
    val activeSavedRequestId: String? = null,
    val isLibraryLoading: Boolean = false,
    val isLibrarySaving: Boolean = false,
    val libraryErrorMessage: String? = null,
    val libraryRecoveryAvailable: Boolean = false,
    val isDraftDirty: Boolean = false
)

/**
 * Headless state holder for the Workbench UI. A single StateFlow owns request,
 * response, contract, lifecycle and history so an old execution cannot be
 * attached to a newer draft or operation.
 */
class WorkbenchViewModel(
    private val resolveVariables: ResolveWorkbenchVariablesUseCase,
    private val executeRequest: ExecuteWorkbenchRequestUseCase,
    private val prepareContractRequest: PrepareContractRequestUseCase,
    private val validateContractResponse: ValidateContractResponseUseCase,
    private val historyRepository: WorkbenchHistoryRepository,
    private val redactor: SecretRedactor = SecretRedactor(),
    private val evaluateAssertions: EvaluateWorkbenchAssertionsUseCase =
        EvaluateWorkbenchAssertionsUseCase(secretRedactor = redactor),
    private val environmentRepository: WorkbenchEnvironmentRepository? = null,
    private val savedRequestRepository: WorkbenchSavedRequestRepository? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    mainDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    private val currentTimeMillis: () -> Long = System::currentTimeMillis
) {
    private val scope = CoroutineScope(SupervisorJob() + mainDispatcher)
    private val executionSequence = AtomicLong(0L)
    private val draftRevision = AtomicLong(0L)
    private val historySequence = AtomicLong(0L)
    private val environmentSequence = AtomicLong(0L)
    private val librarySequence = AtomicLong(0L)
    private var sendJob: Job? = null
    private var historyJob: Job? = null
    private var environmentJob: Job? = null
    private var libraryJob: Job? = null
    private var scratchVariables: Map<String, WorkbenchVariable> = emptyMap()

    private val _state = MutableStateFlow(WorkbenchState())
    val state: StateFlow<WorkbenchState> = _state.asStateFlow()

    init {
        reloadHistory()
        reloadEnvironments()
        reloadSavedWorkspace()
    }

    fun updateDraft(transform: (WorkbenchRequestDraft) -> WorkbenchRequestDraft) {
        invalidateDraftForMutation()
        _state.update { current ->
            val updated = transform(current.draft)
            val secretErrors = requiredSecretErrors(
                updated,
                current.variables,
                current.assertions,
                scanLargeBody = updated.body.length <= MAX_INTERACTIVE_SECRET_SCAN_CHARS
            )
            current.copy(
                draft = updated,
                isSending = false,
                response = null,
                executionFailure = null,
                variableErrors = secretErrors,
                contractViolations = emptyList(),
                assertionReport = null,
                errorMessage = null,
                lifecycle = preparedLifecycle(),
                requiredSecretRestorations = secretErrors.mapTo(linkedSetOf()) { it.secretName },
                isDraftDirty = true
                // replayRequiresFullRequest intentionally remains sticky.
            )
        }
    }

    /** Replaces a bounded history preview with a caller-supplied complete draft. */
    fun replaceDraftWithCompleteRequest(draft: WorkbenchRequestDraft) {
        invalidateDraftForMutation()
        _state.update { current ->
            val secretErrors = requiredSecretErrors(draft, current.variables, current.assertions)
            current.copy(
                draft = draft,
                isSending = false,
                response = null,
                executionFailure = null,
                variableErrors = secretErrors,
                contractViolations = emptyList(),
                assertionReport = null,
                errorMessage = null,
                lifecycle = preparedLifecycle(),
                requiredSecretRestorations = secretErrors.mapTo(linkedSetOf()) { it.secretName },
                replayRequiresFullRequest = false,
                activeSavedRequestId = null,
                isDraftDirty = true
            )
        }
    }

    fun setVariables(variables: Map<String, WorkbenchVariable>) {
        if (_state.value.isEnvironmentLoading || _state.value.isEnvironmentSaving) {
            _state.update { it.copy(environmentErrorMessage = "Wait for the environment operation to finish") }
            return
        }
        invalidateExecutionForMutation()
        val copied = variables.toMap()
        if (_state.value.environmentSnapshot.activeEnvironmentId == null) scratchVariables = copied
        _state.update { current ->
            val secretErrors = requiredSecretErrors(
                current.draft,
                copied,
                current.assertions,
                scanLargeBody = current.draft.body.length <= MAX_INTERACTIVE_SECRET_SCAN_CHARS
            )
            current.copy(
                variables = copied,
                isSending = false,
                response = null,
                executionFailure = null,
                variableErrors = secretErrors,
                contractViolations = emptyList(),
                assertionReport = null,
                errorMessage = null,
                lifecycle = preparedLifecycle(),
                requiredSecretRestorations = secretErrors.mapTo(linkedSetOf()) { it.secretName },
                environmentDirty = current.environmentSnapshot.activeEnvironmentId != null
            )
        }
    }

    fun updateVariable(name: String, variable: WorkbenchVariable?) {
        if (_state.value.isEnvironmentLoading || _state.value.isEnvironmentSaving) {
            _state.update { it.copy(environmentErrorMessage = "Wait for the environment operation to finish") }
            return
        }
        invalidateExecutionForMutation()
        _state.update { current ->
            val updated = current.variables.toMutableMap()
            if (variable == null) updated.remove(name) else updated[name] = variable
            val secretErrors = requiredSecretErrors(
                current.draft,
                updated,
                current.assertions,
                scanLargeBody = current.draft.body.length <= MAX_INTERACTIVE_SECRET_SCAN_CHARS
            )
            current.copy(
                variables = updated,
                isSending = false,
                response = null,
                executionFailure = null,
                variableErrors = secretErrors,
                contractViolations = emptyList(),
                assertionReport = null,
                errorMessage = null,
                lifecycle = preparedLifecycle(),
                requiredSecretRestorations = secretErrors.mapTo(linkedSetOf()) { it.secretName },
                environmentDirty = current.environmentSnapshot.activeEnvironmentId != null
            )
        }
        if (_state.value.environmentSnapshot.activeEnvironmentId == null) {
            scratchVariables = _state.value.variables
        }
    }

    fun setAssertions(assertions: List<WorkbenchAssertion>) {
        if (assertions.size > MAX_ASSERTIONS) {
            _state.update { it.copy(errorMessage = "A request can contain at most $MAX_ASSERTIONS assertions") }
            return
        }
        invalidateDraftForMutation()
        val normalized = normalizeAssertions(assertions)
        _state.update { current ->
            val secretErrors = requiredSecretErrors(
                current.draft,
                current.variables,
                normalized,
                scanLargeBody = current.draft.body.length <= MAX_INTERACTIVE_SECRET_SCAN_CHARS
            )
            current.copy(
                assertions = normalized,
                assertionReport = null,
                response = null,
                executionFailure = null,
                contractViolations = emptyList(),
                variableErrors = secretErrors,
                requiredSecretRestorations = secretErrors.mapTo(linkedSetOf()) { it.secretName },
                errorMessage = null,
                isSending = false,
                lifecycle = preparedLifecycle(),
                isDraftDirty = true
            )
        }
    }

    fun addAssertion(assertion: WorkbenchAssertion) {
        if (_state.value.assertions.size >= MAX_ASSERTIONS) {
            _state.update { it.copy(errorMessage = "A request can contain at most $MAX_ASSERTIONS assertions") }
            return
        }
        setAssertions(_state.value.assertions + assertion)
    }

    fun updateAssertion(id: String, assertion: WorkbenchAssertion) {
        val current = _state.value.assertions
        val index = current.indexOfFirst { it.id == id }
        if (index < 0) return
        val replacement = assertion.withId(id)
        setAssertions(current.toMutableList().also { it[index] = replacement })
    }

    fun removeAssertion(id: String) {
        val updated = _state.value.assertions.filterNot { it.id == id }
        if (updated.size == _state.value.assertions.size) return
        setAssertions(updated)
    }

    fun setContract(spec: SwaggerSpec?, sourceName: String? = null) {
        invalidateExecutionForMutation()
        val operations = operations(spec)
        _state.update { current ->
            current.copy(
                contract = spec,
                contractSourceName = sourceName?.take(MAX_CONTRACT_SOURCE_NAME_CHARS),
                isSending = false,
                operations = operations,
                selectedOperation = null,
                response = null,
                executionFailure = null,
                contractViolations = emptyList(),
                assertionReport = null,
                errorMessage = null,
                lifecycle = preparedLifecycle()
            )
        }
    }

    fun selectOperation(
        spec: SwaggerSpec,
        path: String,
        method: String,
        serverIndex: Int = 0
    ) {
        if (_state.value.isEnvironmentLoading || _state.value.isEnvironmentSaving) {
            _state.update { it.copy(environmentErrorMessage = "Wait for the environment operation to finish") }
            return
        }
        invalidateExecutionForMutation()
        runCatching { prepareContractRequest(spec, path, method, serverIndex) }
            .onSuccess { prepared ->
                draftRevision.incrementAndGet()
                val beforeUpdate = _state.value
                val mergedVariables = prepared.variables + beforeUpdate.variables
                if (beforeUpdate.environmentSnapshot.activeEnvironmentId == null) {
                    scratchVariables = mergedVariables
                }
                _state.update { current ->
                    val secretErrors = requiredSecretErrors(prepared.draft, mergedVariables, current.assertions)
                    current.copy(
                        draft = prepared.draft,
                        isSending = false,
                        variables = mergedVariables,
                        contract = spec,
                        operations = operations(spec),
                        selectedOperation = WorkbenchOperationRef(path, method.uppercase()),
                        response = null,
                        executionFailure = null,
                        variableErrors = secretErrors,
                        contractViolations = emptyList(),
                        assertionReport = null,
                        errorMessage = null,
                        lifecycle = preparedLifecycle(),
                        requiredSecretRestorations = secretErrors.mapTo(linkedSetOf()) { it.secretName },
                        replayRequiresFullRequest = false,
                        environmentDirty = current.environmentSnapshot.activeEnvironmentId != null &&
                            mergedVariables != current.variables,
                        isDraftDirty = true
                    )
                }
            }
            .onFailure { error ->
                _state.update {
                    it.copy(
                        isSending = false,
                        errorMessage = error.message ?: "Failed to prepare contract operation",
                        lifecycle = preparedLifecycle()
                    )
                }
            }
    }

    fun send() {
        val snapshot = _state.value
        if (snapshot.draft.url.isBlank()) {
            rejectBeforeSend(snapshot, "Enter a request URL before sending")
            return
        }
        if (snapshot.isEnvironmentLoading || snapshot.isEnvironmentSaving) {
            rejectBeforeSend(snapshot, "Wait for the environment operation to finish before sending")
            return
        }
        if (snapshot.replayRequiresFullRequest) {
            rejectBeforeSend(
                snapshot,
                "Historical request preview is truncated; restore the complete request before sending"
            )
            return
        }

        val secretErrors = requiredSecretErrors(snapshot.draft, snapshot.variables, snapshot.assertions)
        if (secretErrors.isNotEmpty()) {
            rejectBeforeSend(snapshot, "Restore required secrets before sending", secretErrors)
            return
        }

        sendJob?.cancel()
        val executionId = executionSequence.incrementAndGet()
        val startedAt = currentTimeMillis()
        _state.update {
            it.copy(
                isSending = true,
                response = null,
                executionFailure = null,
                variableErrors = emptyList(),
                contractViolations = emptyList(),
                assertionReport = null,
                errorMessage = null,
                lifecycle = WorkbenchExecutionLifecycle(
                    executionId = executionId,
                    phase = WorkbenchExecutionPhase.RESOLVING,
                    preparedAtEpochMillis = snapshot.lifecycle.preparedAtEpochMillis ?: startedAt
                ),
                requiredSecretRestorations = emptySet()
            )
        }

        sendJob = scope.launch {
            var sensitiveValues = emptySet<String>()
            try {
                val resolution = withContext(ioDispatcher) {
                    resolveVariables(snapshot.draft, snapshot.variables)
                }
                if (resolution is VariableResolutionResult.Failure) {
                    if (isCurrent(executionId)) {
                        val now = currentTimeMillis()
                        _state.update {
                            it.copy(
                                isSending = false,
                                variableErrors = resolution.errors,
                                executionFailure = WorkbenchExecutionFailure(
                                    WorkbenchFailureKind.INVALID_REQUEST,
                                    "Resolve missing or cyclic variables before sending"
                                ),
                                lifecycle = it.lifecycle.copy(
                                    phase = WorkbenchExecutionPhase.FAILED,
                                    outcome = WorkbenchExecutionOutcome.FAILED,
                                    completedAtEpochMillis = now
                                )
                            )
                        }
                    }
                    return@launch
                }

                val resolved = (resolution as VariableResolutionResult.Success).request
                sensitiveValues = resolved.sensitiveValues
                if (!isCurrent(executionId)) return@launch
                _state.update {
                    it.copy(
                        isSending = true,
                        lifecycle = it.lifecycle.copy(
                            phase = WorkbenchExecutionPhase.IN_FLIGHT,
                            inFlightAtEpochMillis = currentTimeMillis()
                        )
                    )
                }

                val result = withContext(ioDispatcher) { executeRequest(resolved) }
                if (!isCurrent(executionId)) return@launch
                handleExecutionResult(executionId, snapshot, result, sensitiveValues)
            } catch (_: CancellationException) {
                // Explicit cancellation or a mutation owns the visible state.
            } catch (error: Exception) {
                if (isCurrent(executionId)) {
                    val now = currentTimeMillis()
                    _state.update {
                        it.copy(
                            isSending = false,
                            executionFailure = WorkbenchExecutionFailure(
                                WorkbenchFailureKind.NETWORK,
                                redactor.redactText(error.message ?: "Request execution failed", sensitiveValues)
                            ),
                            lifecycle = it.lifecycle.copy(
                                phase = WorkbenchExecutionPhase.FAILED,
                                outcome = WorkbenchExecutionOutcome.FAILED,
                                completedAtEpochMillis = now
                            )
                        )
                    }
                }
            }
        }
    }

    fun cancelRequest() {
        if (!_state.value.isSending) return
        executionSequence.incrementAndGet()
        sendJob?.cancel()
        sendJob = null
        val now = currentTimeMillis()
        _state.update {
            it.copy(
                isSending = false,
                executionFailure = WorkbenchExecutionFailure(
                    WorkbenchFailureKind.CANCELLED,
                    "Request was cancelled"
                ),
                lifecycle = it.lifecycle.copy(
                    phase = WorkbenchExecutionPhase.CANCELLED,
                    outcome = WorkbenchExecutionOutcome.CANCELLED,
                    completedAtEpochMillis = now
                )
            )
        }
    }

    fun reopenHistory(id: String, discardUnsavedChanges: Boolean = true) {
        if ((_state.value.isDraftDirty || _state.value.environmentDirty) && !discardUnsavedChanges) {
            _state.update { it.copy(errorMessage = "Save or discard request and environment changes before replaying history") }
            return
        }
        val entry = _state.value.history.firstOrNull { it.id == id } ?: return
        invalidateDraftForMutation()
        val bounded = boundedHistoryEntry(entry)
        scratchVariables = bounded.variables
        val reopenedAssertions = normalizeAssertions(bounded.assertions)
        val secretErrors = requiredSecretErrors(bounded.request, bounded.variables, reopenedAssertions)
        val lifecycle = bounded.lifecycle ?: WorkbenchExecutionLifecycle(
            phase = WorkbenchExecutionPhase.HISTORY_SAVED,
            outcome = WorkbenchExecutionOutcome.NONE,
            historyPersistence = WorkbenchHistoryPersistenceState.SAVED,
            preparedAtEpochMillis = bounded.createdAtEpochMillis,
            historySavedAtEpochMillis = bounded.createdAtEpochMillis
        )
        val replayMessage = when {
            bounded.requestPreviewTruncated ->
                "Historical request is a bounded preview; restore the complete request before sending"
            secretErrors.isNotEmpty() -> "Restore required secrets before replaying this request"
            bounded.operation != null -> "Select a contract operation to revalidate this historical response"
            else -> null
        }

        _state.update {
            it.copy(
                draft = bounded.request,
                variables = bounded.variables,
                selectedOperation = null,
                response = bounded.response,
                executionFailure = bounded.failure,
                contractViolations = emptyList(),
                assertions = reopenedAssertions,
                assertionReport = bounded.assertionResults.takeIf { results -> results.isNotEmpty() }
                    ?.let(::WorkbenchAssertionReport),
                variableErrors = secretErrors,
                isSending = false,
                errorMessage = replayMessage,
                lifecycle = lifecycle,
                requiredSecretRestorations = secretErrors.mapTo(linkedSetOf()) { error -> error.secretName },
                replayRequiresFullRequest = bounded.requestPreviewTruncated,
                environmentSnapshot = it.environmentSnapshot.copy(activeEnvironmentId = null),
                environmentDirty = false,
                activeSavedRequestId = null,
                isDraftDirty = true
            )
        }
        persistEnvironmentSelection(null)
    }

    fun reloadHistory() {
        val operationId = historySequence.incrementAndGet()
        historyJob?.cancel()
        _state.update { it.copy(isHistoryLoading = true) }
        val currentHistory = _state.value.history
        historyJob = scope.launch {
            val loaded = captureNonCancellation {
                withContext(ioDispatcher) { mergeHistory(historyRepository.list(), currentHistory) }
            }
            if (historySequence.get() != operationId) return@launch
            loaded.onSuccess { entries ->
                _state.update { current ->
                    current.copy(
                        history = entries,
                        isHistoryLoading = false
                    )
                }
            }.onFailure {
                _state.update {
                    it.copy(isHistoryLoading = false, errorMessage = "Could not load request history")
                }
            }
        }
    }

    fun clearHistory() {
        if (sendJob?.isActive == true) {
            _state.update { it.copy(errorMessage = "Wait for the active request to finish before clearing history") }
            return
        }
        val operationId = historySequence.incrementAndGet()
        historyJob?.cancel()
        historyJob = scope.launch {
            val cleared = captureNonCancellation {
                withContext(ioDispatcher) { historyRepository.clear() }
            }
            if (historySequence.get() != operationId) return@launch
            cleared.onSuccess {
                _state.update {
                    it.copy(history = emptyList(), isHistoryLoading = false, errorMessage = null)
                }
            }.onFailure {
                _state.update {
                    it.copy(isHistoryLoading = false, errorMessage = "Could not clear request history")
                }
            }
        }
    }

    fun reloadEnvironments() {
        val repository = environmentRepository ?: run {
            _state.update { it.copy(isEnvironmentLoading = false) }
            return
        }
        val operationId = environmentSequence.incrementAndGet()
        environmentJob?.cancel()
        invalidateExecutionForMutation()
        _state.update {
            it.copy(
                isSending = false,
                isEnvironmentLoading = true,
                isEnvironmentSaving = false,
                environmentErrorMessage = null,
                response = null,
                executionFailure = null,
                assertionReport = null,
                contractViolations = emptyList(),
                lifecycle = preparedLifecycle()
            )
        }
        environmentJob = scope.launch {
            val loaded = captureNonCancellation { withContext(ioDispatcher) { repository.snapshot() } }
            if (environmentSequence.get() != operationId) return@launch
            loaded.onSuccess { snapshot ->
                val variables = snapshot.activeEnvironment?.variables ?: scratchVariables
                val current = _state.value
                val secretErrors = requiredSecretErrors(current.draft, variables, current.assertions)
                _state.update {
                    it.copy(
                        environmentSnapshot = snapshot,
                        variables = variables,
                        environmentDirty = false,
                        isEnvironmentLoading = false,
                        isEnvironmentSaving = false,
                        environmentErrorMessage = null,
                        environmentRecoveryAvailable = false,
                        variableErrors = secretErrors,
                        requiredSecretRestorations = secretErrors.mapTo(linkedSetOf()) { error -> error.secretName }
                    )
                }
            }.onFailure { error ->
                _state.update { current ->
                    current.copy(
                        isEnvironmentLoading = false,
                        isEnvironmentSaving = false,
                        environmentErrorMessage = "Could not load environments; saved data was left untouched",
                        environmentRecoveryAvailable = error is WorkbenchStorageCorruptionException
                    )
                }
            }
        }
    }

    fun backupAndResetCorruptEnvironments() {
        val repository = environmentRepository ?: return
        invalidateExecutionForMutation()
        val operationId = environmentSequence.incrementAndGet()
        environmentJob?.cancel()
        _state.update {
            it.copy(
                isSending = false,
                isEnvironmentLoading = false,
                isEnvironmentSaving = true,
                environmentErrorMessage = null,
                response = null,
                executionFailure = null,
                contractViolations = emptyList(),
                assertionReport = null,
                lifecycle = preparedLifecycle()
            )
        }
        environmentJob = scope.launch {
            val recovered = captureNonCancellation {
                withContext(ioDispatcher) {
                    val backup = repository.backupCorruptStoreAndReset()
                    backup to repository.snapshot()
                }
            }
            if (environmentSequence.get() != operationId) return@launch
            recovered.onSuccess { (backup, snapshot) ->
                val variables = snapshot.activeEnvironment?.variables ?: scratchVariables
                val current = _state.value
                val secretErrors = requiredSecretErrors(current.draft, variables, current.assertions)
                _state.update {
                    it.copy(
                        environmentSnapshot = snapshot,
                        variables = variables,
                        environmentDirty = false,
                        isEnvironmentLoading = false,
                        isEnvironmentSaving = false,
                        environmentRecoveryAvailable = false,
                        environmentErrorMessage = backup?.let {
                            path -> "Environment store reset. Corrupt backup: $path"
                        } ?: "Environment store reset",
                        variableErrors = secretErrors,
                        requiredSecretRestorations = secretErrors.mapTo(linkedSetOf()) { error ->
                            error.secretName
                        }
                    )
                }
            }.onFailure {
                _state.update { state ->
                    state.copy(
                        isEnvironmentLoading = false,
                        isEnvironmentSaving = false,
                        environmentRecoveryAvailable = true,
                        environmentErrorMessage = "Could not back up and reset the environment store"
                    )
                }
            }
        }
    }

    fun selectEnvironment(id: String?, discardUnsavedChanges: Boolean = false) {
        val repository = environmentRepository ?: return
        val current = _state.value
        if (id == current.environmentSnapshot.activeEnvironmentId) return
        if (current.environmentDirty && !discardUnsavedChanges) {
            _state.update {
                it.copy(environmentErrorMessage = "Save or discard environment changes before switching")
            }
            return
        }
        if (current.environmentSnapshot.activeEnvironmentId == null) {
            scratchVariables = current.variables
        }
        invalidateExecutionForMutation()
        val operationId = environmentSequence.incrementAndGet()
        environmentJob?.cancel()
        _state.update {
            it.copy(
                isSending = false,
                isEnvironmentLoading = true,
                isEnvironmentSaving = false,
                environmentErrorMessage = null,
                response = null,
                executionFailure = null,
                contractViolations = emptyList(),
                assertionReport = null,
                lifecycle = preparedLifecycle()
            )
        }
        environmentJob = scope.launch {
            val selected = captureNonCancellation { withContext(ioDispatcher) { repository.select(id) } }
            if (environmentSequence.get() != operationId) return@launch
            selected.onSuccess { snapshot ->
                val variables = snapshot.activeEnvironment?.variables ?: scratchVariables
                val currentState = _state.value
                val secretErrors = requiredSecretErrors(currentState.draft, variables, currentState.assertions)
                _state.update {
                    it.copy(
                        environmentSnapshot = snapshot,
                        variables = variables,
                        variableErrors = secretErrors,
                        requiredSecretRestorations = secretErrors.mapTo(linkedSetOf()) { error -> error.secretName },
                        environmentDirty = false,
                        isEnvironmentLoading = false,
                        isEnvironmentSaving = false,
                        environmentErrorMessage = null,
                        environmentRecoveryAvailable = false
                    )
                }
            }.onFailure { error ->
                _state.update { state ->
                    state.copy(
                        isEnvironmentLoading = false,
                        isEnvironmentSaving = false,
                        environmentErrorMessage = "Could not switch environment",
                        environmentRecoveryAvailable = error is WorkbenchStorageCorruptionException
                    )
                }
            }
        }
    }

    fun saveEnvironment(name: String? = null) {
        persistEnvironment(name = name, createNew = false)
    }

    fun createEnvironment(name: String) {
        persistEnvironment(name = name, createNew = true)
    }

    private fun persistEnvironment(name: String?, createNew: Boolean) {
        val repository = environmentRepository ?: return
        val current = _state.value
        val active = current.environmentSnapshot.activeEnvironment.takeUnless { createNew }
        val resolvedName = (name ?: active?.name).orEmpty().trim()
        if (resolvedName.isBlank()) {
            _state.update { it.copy(environmentErrorMessage = "Environment name is required") }
            return
        }
        invalidateExecutionForMutation()
        val operationId = environmentSequence.incrementAndGet()
        environmentJob?.cancel()
        val now = currentTimeMillis()
        val environment = WorkbenchEnvironment(
            id = active?.id ?: UUID.randomUUID().toString(),
            name = resolvedName.take(MAX_ENVIRONMENT_NAME_CHARS),
            variables = current.variables,
            createdAtEpochMillis = active?.createdAtEpochMillis ?: now,
            updatedAtEpochMillis = now,
            secretsRequiringRestore = active?.secretsRequiringRestore.orEmpty()
        )
        _state.update {
            it.copy(
                isSending = false,
                isEnvironmentSaving = true,
                isEnvironmentLoading = false,
                environmentErrorMessage = null,
                response = null,
                executionFailure = null,
                contractViolations = emptyList(),
                assertionReport = null,
                lifecycle = preparedLifecycle()
            )
        }
        environmentJob = scope.launch {
            val saved = captureNonCancellation {
                withContext(ioDispatcher) {
                    repository.upsertAndSelectEnvironment(environment)
                }
            }
            if (environmentSequence.get() != operationId) return@launch
            saved.onSuccess { snapshot ->
                val variables = snapshot.activeEnvironment?.variables ?: current.variables
                val currentState = _state.value
                val secretErrors = requiredSecretErrors(
                    currentState.draft,
                    variables,
                    currentState.assertions
                )
                _state.update {
                    it.copy(
                        environmentSnapshot = snapshot,
                        variables = variables,
                        environmentDirty = false,
                        isEnvironmentSaving = false,
                        isEnvironmentLoading = false,
                        environmentErrorMessage = null,
                        environmentRecoveryAvailable = false,
                        variableErrors = secretErrors,
                        requiredSecretRestorations = secretErrors.mapTo(linkedSetOf()) { error ->
                            error.secretName
                        }
                    )
                }
            }.onFailure { error ->
                _state.update { state ->
                    state.copy(
                        isEnvironmentSaving = false,
                        isEnvironmentLoading = false,
                        environmentErrorMessage = "Could not save environment; existing data was left untouched",
                        environmentRecoveryAvailable = error is WorkbenchStorageCorruptionException
                    )
                }
            }
        }
    }

    fun discardEnvironmentChanges() {
        val current = _state.value
        val active = current.environmentSnapshot.activeEnvironment ?: return
        invalidateExecutionForMutation()
        val secretErrors = requiredSecretErrors(current.draft, active.variables, current.assertions)
        _state.update {
            it.copy(
                variables = active.variables,
                isSending = false,
                environmentDirty = false,
                response = null,
                executionFailure = null,
                contractViolations = emptyList(),
                assertionReport = null,
                variableErrors = secretErrors,
                requiredSecretRestorations = secretErrors.mapTo(linkedSetOf()) { error -> error.secretName },
                lifecycle = preparedLifecycle(),
                environmentErrorMessage = null
            )
        }
    }

    fun deleteEnvironment(id: String, discardUnsavedChanges: Boolean = false) {
        val repository = environmentRepository ?: return
        val current = _state.value
        if (current.environmentSnapshot.activeEnvironmentId == id &&
            current.environmentDirty && !discardUnsavedChanges
        ) {
            _state.update {
                it.copy(environmentErrorMessage = "Discard environment changes before deleting")
            }
            return
        }
        invalidateExecutionForMutation()
        val operationId = environmentSequence.incrementAndGet()
        environmentJob?.cancel()
        _state.update {
            it.copy(
                isSending = false,
                isEnvironmentSaving = true,
                isEnvironmentLoading = false,
                environmentErrorMessage = null,
                response = null,
                executionFailure = null,
                contractViolations = emptyList(),
                assertionReport = null,
                lifecycle = preparedLifecycle()
            )
        }
        environmentJob = scope.launch {
            val deleted = captureNonCancellation { withContext(ioDispatcher) { repository.delete(id) } }
            if (environmentSequence.get() != operationId) return@launch
            deleted.onSuccess { snapshot ->
                val variables = snapshot.activeEnvironment?.variables ?: scratchVariables
                val currentState = _state.value
                val secretErrors = requiredSecretErrors(currentState.draft, variables, currentState.assertions)
                _state.update {
                    it.copy(
                        environmentSnapshot = snapshot,
                        variables = variables,
                        environmentDirty = false,
                        isEnvironmentSaving = false,
                        isEnvironmentLoading = false,
                        environmentErrorMessage = null,
                        environmentRecoveryAvailable = false,
                        response = null,
                        assertionReport = null,
                        contractViolations = emptyList(),
                        variableErrors = secretErrors,
                        requiredSecretRestorations = secretErrors.mapTo(linkedSetOf()) { error -> error.secretName },
                        lifecycle = preparedLifecycle()
                    )
                }
            }.onFailure { error ->
                _state.update { state ->
                    state.copy(
                        isEnvironmentSaving = false,
                        isEnvironmentLoading = false,
                        environmentErrorMessage = "Could not delete environment",
                        environmentRecoveryAvailable = error is WorkbenchStorageCorruptionException
                    )
                }
            }
        }
    }

    fun reloadSavedWorkspace() {
        val repository = savedRequestRepository ?: run {
            _state.update { it.copy(isLibraryLoading = false) }
            return
        }
        val operationId = librarySequence.incrementAndGet()
        libraryJob?.cancel()
        _state.update {
            it.copy(isLibraryLoading = true, isLibrarySaving = false, libraryErrorMessage = null)
        }
        libraryJob = scope.launch {
            val loaded = captureNonCancellation { withContext(ioDispatcher) { repository.snapshot() } }
            if (librarySequence.get() != operationId) return@launch
            loaded.onSuccess { workspace ->
                _state.update {
                    it.copy(
                        savedWorkspace = workspace,
                        isLibraryLoading = false,
                        isLibrarySaving = false,
                        libraryErrorMessage = null,
                        libraryRecoveryAvailable = false
                    )
                }
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        isLibraryLoading = false,
                        isLibrarySaving = false,
                        libraryErrorMessage = "Could not load the request library; saved data was left untouched",
                        libraryRecoveryAvailable = error is WorkbenchStorageCorruptionException
                    )
                }
            }
        }
    }

    fun backupAndResetCorruptLibrary() {
        val repository = savedRequestRepository ?: return
        val operationId = librarySequence.incrementAndGet()
        libraryJob?.cancel()
        _state.update {
            it.copy(isLibraryLoading = false, isLibrarySaving = true, libraryErrorMessage = null)
        }
        libraryJob = scope.launch {
            val recovered = captureNonCancellation {
                withContext(ioDispatcher) {
                    val backup = repository.backupCorruptStoreAndReset()
                    backup to repository.snapshot()
                }
            }
            if (librarySequence.get() != operationId) return@launch
            recovered.onSuccess { (backup, workspace) ->
                _state.update {
                    it.copy(
                        savedWorkspace = workspace,
                        activeSavedRequestId = null,
                        isDraftDirty = it.isDraftDirty || it.activeSavedRequestId != null,
                        isLibraryLoading = false,
                        isLibrarySaving = false,
                        libraryRecoveryAvailable = false,
                        libraryErrorMessage = backup?.let {
                            path -> "Request library reset. Corrupt backup: $path"
                        } ?: "Request library reset"
                    )
                }
            }.onFailure {
                _state.update { state ->
                    state.copy(
                        isLibraryLoading = false,
                        isLibrarySaving = false,
                        libraryRecoveryAvailable = true,
                        libraryErrorMessage = "Could not back up and reset the request library"
                    )
                }
            }
        }
    }

    fun createCollection(name: String, description: String = "") {
        val repository = savedRequestRepository ?: return
        val trimmed = name.trim()
        if (trimmed.isBlank()) {
            _state.update { it.copy(libraryErrorMessage = "Collection name is required") }
            return
        }
        val now = currentTimeMillis()
        val collection = WorkbenchSavedCollection(
            id = UUID.randomUUID().toString(),
            name = trimmed.take(MAX_COLLECTION_NAME_CHARS),
            description = description.trim().take(MAX_COLLECTION_DESCRIPTION_CHARS),
            createdAtEpochMillis = now,
            updatedAtEpochMillis = now
        )
        runLibraryMutation("Could not create collection") { repository.upsertCollection(collection) }
    }

    fun deleteCollection(id: String) {
        val repository = savedRequestRepository ?: return
        runLibraryMutation(
            failureMessage = "Could not delete collection",
            onSuccess = { workspace ->
                val activeId = _state.value.activeSavedRequestId
                if (activeId != null && workspace.requests.none { it.id == activeId }) {
                    _state.update { it.copy(activeSavedRequestId = null, isDraftDirty = true) }
                }
            }
        ) { repository.deleteCollection(id) }
    }

    fun saveCurrentRequest(
        collectionId: String? = null,
        name: String? = null,
        saveAs: Boolean = false
    ) {
        val repository = savedRequestRepository ?: return
        val current = _state.value
        if (!canPersistCurrentDraft(current.draft)) return
        val activeSaved = current.activeSavedRequestId?.let { activeId ->
            current.savedWorkspace.requests.firstOrNull { it.id == activeId }
        }
        val targetCollectionId = collectionId ?: activeSaved?.collectionId
            ?: current.savedWorkspace.collections.firstOrNull()?.id
        if (targetCollectionId == null || current.savedWorkspace.collections.none { it.id == targetCollectionId }) {
            _state.update { it.copy(libraryErrorMessage = "Create or select a collection before saving") }
            return
        }
        val requestName = (name ?: current.draft.name).trim().ifBlank { "Untitled request" }
        val now = currentTimeMillis()
        val existing = activeSaved.takeUnless { saveAs }
        val revisionAtSave = draftRevision.get()
        val activeIdAtSave = current.activeSavedRequestId
        val request = WorkbenchSavedRequest(
            id = existing?.id ?: UUID.randomUUID().toString(),
            collectionId = targetCollectionId,
            folderId = existing?.folderId,
            name = requestName.take(MAX_SAVED_REQUEST_NAME_CHARS),
            description = existing?.description.orEmpty(),
            tags = existing?.tags.orEmpty(),
            draft = current.draft.copy(name = requestName.take(MAX_SAVED_REQUEST_NAME_CHARS)),
            variables = current.variables.filter { (key, variable) ->
                variable.secret || redactor.isSensitiveKey(key)
            },
            assertions = current.assertions,
            createdAtEpochMillis = existing?.createdAtEpochMillis ?: now,
            updatedAtEpochMillis = now
        )
        runLibraryMutation(
            failureMessage = "Could not save request",
            onSuccess = { workspace ->
                if (draftRevision.get() == revisionAtSave &&
                    _state.value.activeSavedRequestId == activeIdAtSave
                ) {
                    _state.update {
                        it.copy(
                            savedWorkspace = workspace,
                            activeSavedRequestId = request.id,
                            isDraftDirty = false
                        )
                    }
                }
            }
        ) { repository.upsertRequest(request) }
    }

    fun createCollectionAndSaveRequest(collectionName: String, requestName: String) {
        val repository = savedRequestRepository ?: return
        val trimmedCollection = collectionName.trim()
        val trimmedRequest = requestName.trim().ifBlank { "Untitled request" }
        if (trimmedCollection.isBlank()) {
            _state.update { it.copy(libraryErrorMessage = "Collection name is required") }
            return
        }
        val current = _state.value
        if (!canPersistCurrentDraft(current.draft)) return
        val revisionAtSave = draftRevision.get()
        val activeIdAtSave = current.activeSavedRequestId
        val now = currentTimeMillis()
        val collection = WorkbenchSavedCollection(
            id = UUID.randomUUID().toString(),
            name = trimmedCollection.take(MAX_COLLECTION_NAME_CHARS),
            createdAtEpochMillis = now,
            updatedAtEpochMillis = now
        )
        val request = WorkbenchSavedRequest(
            id = UUID.randomUUID().toString(),
            collectionId = collection.id,
            name = trimmedRequest.take(MAX_SAVED_REQUEST_NAME_CHARS),
            draft = current.draft.copy(name = trimmedRequest.take(MAX_SAVED_REQUEST_NAME_CHARS)),
            variables = current.variables.filter { (key, variable) ->
                variable.secret || redactor.isSensitiveKey(key)
            },
            assertions = current.assertions,
            createdAtEpochMillis = now,
            updatedAtEpochMillis = now
        )
        runLibraryMutation(
            failureMessage = "Could not create the collection and save the request",
            onSuccess = { workspace ->
                if (draftRevision.get() == revisionAtSave &&
                    _state.value.activeSavedRequestId == activeIdAtSave
                ) {
                    _state.update {
                        it.copy(
                            savedWorkspace = workspace,
                            activeSavedRequestId = request.id,
                            isDraftDirty = false
                        )
                    }
                }
            }
        ) {
            repository.upsertCollectionWithRequest(collection, request)
        }
    }

    fun openSavedRequest(id: String, discardUnsavedChanges: Boolean = false) {
        val current = _state.value
        if (current.isDraftDirty && !discardUnsavedChanges) {
            _state.update { it.copy(libraryErrorMessage = "Save or discard request changes before opening another request") }
            return
        }
        val saved = current.savedWorkspace.requests.firstOrNull { it.id == id } ?: return
        invalidateDraftForMutation()
        val assertions = normalizeAssertions(saved.assertions)
        val mergedVariables = current.variables.toMutableMap()
        saved.variables.forEach { (name, savedVariable) ->
            val currentVariable = mergedVariables[name]
            val mustBeSecret = savedVariable.secret || redactor.isSensitiveKey(name)
            mergedVariables[name] = if (currentVariable != null && currentVariable.enabled &&
                !isUnavailableSecretValue(currentVariable.value)
            ) {
                currentVariable.copy(secret = currentVariable.secret || mustBeSecret)
            } else {
                savedVariable.copy(
                    value = "",
                    enabled = currentVariable?.enabled ?: savedVariable.enabled,
                    secret = true
                )
            }
        }
        saved.requiredSecrets.asSequence()
            .filter { it.location == WorkbenchSecretLocation.VARIABLE }
            .mapNotNull { required ->
                required.id.removePrefix("variable:").takeIf { it != required.id && it.isNotBlank() }
            }
            .forEach { name ->
                val currentVariable = mergedVariables[name]
                mergedVariables[name] = if (currentVariable != null && currentVariable.enabled &&
                    !isUnavailableSecretValue(currentVariable.value)
                ) {
                    currentVariable.copy(secret = true)
                } else {
                    WorkbenchVariable(
                        value = "",
                        enabled = currentVariable?.enabled ?: true,
                        secret = true
                    )
                }
            }
        val secretErrors = requiredSecretErrors(saved.draft, mergedVariables, assertions)
        _state.update {
            it.copy(
                draft = saved.draft,
                variables = mergedVariables,
                assertions = assertions,
                assertionReport = null,
                selectedOperation = null,
                response = null,
                executionFailure = null,
                contractViolations = emptyList(),
                variableErrors = secretErrors,
                requiredSecretRestorations = secretErrors.mapTo(linkedSetOf()) { error -> error.secretName },
                replayRequiresFullRequest = false,
                lifecycle = preparedLifecycle(),
                activeSavedRequestId = saved.id,
                isDraftDirty = false,
                libraryErrorMessage = null,
                isSending = false
            )
        }
    }

    fun deleteSavedRequest(id: String) {
        val repository = savedRequestRepository ?: return
        runLibraryMutation(
            failureMessage = "Could not delete saved request",
            onSuccess = {
                if (_state.value.activeSavedRequestId == id) {
                    _state.update { state -> state.copy(activeSavedRequestId = null, isDraftDirty = true) }
                }
            }
        ) { repository.deleteRequest(id) }
    }

    fun newRequest(discardUnsavedChanges: Boolean = false) {
        val current = _state.value
        if (current.isDraftDirty && !discardUnsavedChanges) {
            _state.update { it.copy(libraryErrorMessage = "Save or discard request changes before starting a new request") }
            return
        }
        invalidateDraftForMutation()
        _state.update {
            it.copy(
                draft = WorkbenchRequestDraft(),
                assertions = emptyList(),
                assertionReport = null,
                selectedOperation = null,
                response = null,
                executionFailure = null,
                contractViolations = emptyList(),
                variableErrors = emptyList(),
                requiredSecretRestorations = emptySet(),
                replayRequiresFullRequest = false,
                lifecycle = preparedLifecycle(),
                activeSavedRequestId = null,
                isDraftDirty = false,
                libraryErrorMessage = null,
                isSending = false
            )
        }
    }

    fun clearMessage() {
        _state.update { it.copy(errorMessage = null) }
    }

    fun onCleared() {
        executionSequence.incrementAndGet()
        historySequence.incrementAndGet()
        environmentSequence.incrementAndGet()
        librarySequence.incrementAndGet()
        sendJob?.cancel()
        historyJob?.cancel()
        environmentJob?.cancel()
        libraryJob?.cancel()
        scope.cancel()
    }

    private fun runLibraryMutation(
        failureMessage: String,
        invalidateExecution: Boolean = false,
        onSuccess: (WorkbenchSavedWorkspace) -> Unit = {},
        mutation: suspend () -> WorkbenchSavedWorkspace
    ) {
        if (invalidateExecution || _state.value.isSending) {
            invalidateExecutionForMutation()
            _state.update {
                it.copy(
                    isSending = false,
                    response = null,
                    executionFailure = null,
                    contractViolations = emptyList(),
                    assertionReport = null,
                    lifecycle = preparedLifecycle()
                )
            }
        }
        val operationId = librarySequence.incrementAndGet()
        libraryJob?.cancel()
        _state.update {
            it.copy(isLibrarySaving = true, isLibraryLoading = false, libraryErrorMessage = null)
        }
        libraryJob = scope.launch {
            val changed = captureNonCancellation { withContext(ioDispatcher) { mutation() } }
            if (librarySequence.get() != operationId) return@launch
            changed.onSuccess { workspace ->
                _state.update {
                    it.copy(
                        savedWorkspace = workspace,
                        isLibrarySaving = false,
                        isLibraryLoading = false,
                        libraryErrorMessage = null,
                        libraryRecoveryAvailable = false
                    )
                }
                onSuccess(workspace)
            }.onFailure { error ->
                _state.update { state ->
                    state.copy(
                        isLibrarySaving = false,
                        isLibraryLoading = false,
                        libraryErrorMessage = failureMessage,
                        libraryRecoveryAvailable = error is WorkbenchStorageCorruptionException
                    )
                }
            }
        }
    }

    private fun canPersistCurrentDraft(draft: WorkbenchRequestDraft): Boolean {
        if (draft.body.toByteArray(Charsets.UTF_8).size <= MAX_SAVED_REQUEST_BODY_BYTES) return true
        _state.update {
            it.copy(
                libraryErrorMessage = "Request bodies larger than 2 MiB cannot be saved to the library"
            )
        }
        return false
    }

    private fun persistEnvironmentSelection(id: String?) {
        val repository = environmentRepository ?: return
        val operationId = environmentSequence.incrementAndGet()
        environmentJob?.cancel()
        environmentJob = scope.launch {
            val selected = captureNonCancellation { withContext(ioDispatcher) { repository.select(id) } }
            if (environmentSequence.get() != operationId) return@launch
            selected.onFailure {
                _state.update { state ->
                    state.copy(environmentErrorMessage = "Could not persist the environment selection")
                }
            }
        }
    }

    private suspend fun handleExecutionResult(
        executionId: Long,
        snapshot: WorkbenchState,
        result: WorkbenchExecutionResult,
        sensitiveValues: Set<String>
    ) {
        val success = result as? WorkbenchExecutionResult.Success
        val displayResponse = success?.response
        val failure = (result as? WorkbenchExecutionResult.Failure)?.error
        val responseReceivedAt = currentTimeMillis()

        if (success != null) {
            _state.update {
                it.copy(
                    isSending = false,
                    response = displayResponse,
                    executionFailure = null,
                    lifecycle = it.lifecycle.copy(
                        phase = WorkbenchExecutionPhase.RESPONSE_RECEIVED,
                        outcome = WorkbenchExecutionOutcome.SUCCEEDED,
                        responseReceivedAtEpochMillis = responseReceivedAt,
                        completedAtEpochMillis = responseReceivedAt
                    )
                )
            }
        } else {
            _state.update {
                it.copy(
                    isSending = false,
                    response = null,
                    executionFailure = failure,
                    lifecycle = it.lifecycle.copy(
                        phase = WorkbenchExecutionPhase.FAILED,
                        outcome = WorkbenchExecutionOutcome.FAILED,
                        completedAtEpochMillis = responseReceivedAt
                    )
                )
            }
        }
        if (!isCurrent(executionId)) return

        var violations = emptyList<ContractViolation>()
        var contractError: String? = null
        if (success != null && snapshot.contract != null && snapshot.selectedOperation != null) {
            _state.update {
                it.copy(
                    lifecycle = it.lifecycle.copy(
                        phase = WorkbenchExecutionPhase.CONTRACT_VALIDATING,
                        contractValidation = WorkbenchContractValidationState.PENDING
                    )
                )
            }
            val validated = captureNonCancellation {
                withContext(ioDispatcher) {
                    // Raw content exists only in this transient result and never enters StateFlow/history.
                    validateContractResponse(
                        snapshot.contract,
                        snapshot.selectedOperation.path,
                        snapshot.selectedOperation.method,
                        success.contractResponse
                    )
                }
            }
            if (!isCurrent(executionId)) return
            validated.onSuccess { resultViolations ->
                violations = resultViolations
                val now = currentTimeMillis()
                _state.update {
                    it.copy(
                        contractViolations = violations,
                        lifecycle = it.lifecycle.copy(
                            phase = WorkbenchExecutionPhase.CONTRACT_VALIDATED,
                            contractValidation = WorkbenchContractValidationState.COMPLETED,
                            contractPassed = violations.none { violation ->
                                violation.severity == ContractViolationSeverity.ERROR
                            },
                            contractValidatedAtEpochMillis = now,
                            completedAtEpochMillis = now
                        )
                    )
                }
            }.onFailure {
                contractError = "Response received, but contract validation failed"
                _state.update {
                    it.copy(
                        errorMessage = contractError,
                        lifecycle = it.lifecycle.copy(
                            phase = WorkbenchExecutionPhase.RESPONSE_RECEIVED,
                            contractValidation = WorkbenchContractValidationState.FAILED,
                            contractPassed = null,
                            completedAtEpochMillis = currentTimeMillis()
                        )
                    )
                }
            }
        }

        var assertionReport: WorkbenchAssertionReport? = null
        var assertionError: String? = null
        if (success != null && snapshot.assertions.isNotEmpty()) {
            _state.update {
                it.copy(
                    lifecycle = it.lifecycle.copy(
                        phase = WorkbenchExecutionPhase.ASSERTIONS_EVALUATING,
                        assertionEvaluation = WorkbenchAssertionEvaluationState.PENDING
                    )
                )
            }
            val evaluated = captureNonCancellation {
                withContext(ioDispatcher) {
                    evaluateAssertions(
                        success.contractResponse,
                        snapshot.assertions,
                        sensitiveValues
                    )
                }
            }
            if (!isCurrent(executionId)) return
            evaluated.onSuccess { report ->
                assertionReport = report
                val now = currentTimeMillis()
                val ranAnyAssertion = report.results.any { result ->
                    result.outcome != WorkbenchAssertionOutcome.SKIPPED
                }
                _state.update {
                    it.copy(
                        assertionReport = report,
                        lifecycle = it.lifecycle.copy(
                            phase = WorkbenchExecutionPhase.ASSERTIONS_EVALUATED,
                            assertionEvaluation = WorkbenchAssertionEvaluationState.COMPLETED,
                            assertionsPassed = report.allPassed.takeIf { ranAnyAssertion },
                            assertionsEvaluatedAtEpochMillis = now,
                            completedAtEpochMillis = now
                        )
                    )
                }
            }.onFailure {
                assertionError = "Response received, but assertions could not be evaluated"
                _state.update {
                    it.copy(
                        errorMessage = assertionError ?: contractError,
                        lifecycle = it.lifecycle.copy(
                            assertionEvaluation = WorkbenchAssertionEvaluationState.FAILED,
                            assertionsPassed = false,
                            completedAtEpochMillis = currentTimeMillis()
                        )
                    )
                }
            }
        }

        if (!isCurrent(executionId)) return
        val beforePersistence = _state.value.lifecycle
        _state.update {
            it.copy(
                lifecycle = beforePersistence.copy(
                    phase = WorkbenchExecutionPhase.HISTORY_SAVING,
                    historyPersistence = WorkbenchHistoryPersistenceState.PENDING
                )
            )
        }

        val savedAt = currentTimeMillis()
        val savedLifecycle = beforePersistence.copy(
            phase = WorkbenchExecutionPhase.HISTORY_SAVED,
            historyPersistence = WorkbenchHistoryPersistenceState.SAVED,
            historySavedAtEpochMillis = savedAt
        )
        val candidate = withContext(ioDispatcher) {
            boundedHistoryEntry(
                redactor.sanitizeHistoryEntry(
                    WorkbenchHistoryEntry(
                        id = UUID.randomUUID().toString(),
                        createdAtEpochMillis = savedAt,
                        request = snapshot.draft,
                        variables = snapshot.variables,
                        response = displayResponse,
                        failure = failure,
                        contractViolations = violations,
                        assertions = snapshot.assertions,
                        assertionResults = assertionReport?.results.orEmpty(),
                        operation = snapshot.selectedOperation,
                        lifecycle = savedLifecycle
                    )
                )
            )
        }
        if (!isCurrent(executionId)) return
        val persisted = captureNonCancellation {
            withContext(ioDispatcher) { sanitizeHistory(historyRepository.save(candidate)) }
        }
        if (!isCurrent(executionId)) return

        persisted.onSuccess { entries ->
            _state.update {
                it.copy(
                    lifecycle = savedLifecycle,
                    history = entries,
                    errorMessage = assertionError ?: contractError
                )
            }
        }.onFailure {
            val failedLifecycle = beforePersistence.copy(
                phase = WorkbenchExecutionPhase.HISTORY_SAVE_FAILED,
                historyPersistence = WorkbenchHistoryPersistenceState.FAILED,
                historySavedAtEpochMillis = null
            )
            val failedEntry = candidate.copy(lifecycle = failedLifecycle)
            _state.update { current ->
                current.copy(
                    lifecycle = failedLifecycle,
                    history = mergeHistory(listOf(failedEntry), current.history),
                    errorMessage = "Request completed, but history could not be saved"
                )
            }
        }
    }

    private fun rejectBeforeSend(
        snapshot: WorkbenchState,
        message: String,
        secretErrors: List<VariableResolutionError.SecretRequired> = emptyList()
    ) {
        val now = currentTimeMillis()
        _state.update {
            it.copy(
                isSending = false,
                executionFailure = WorkbenchExecutionFailure(WorkbenchFailureKind.INVALID_REQUEST, message),
                variableErrors = secretErrors,
                errorMessage = message,
                lifecycle = snapshot.lifecycle.copy(
                    phase = WorkbenchExecutionPhase.FAILED,
                    outcome = WorkbenchExecutionOutcome.FAILED,
                    contractValidation = WorkbenchContractValidationState.NOT_REQUESTED,
                    assertionEvaluation = WorkbenchAssertionEvaluationState.NOT_REQUESTED,
                    historyPersistence = WorkbenchHistoryPersistenceState.NOT_ATTEMPTED,
                    contractPassed = null,
                    assertionsPassed = null,
                    inFlightAtEpochMillis = null,
                    responseReceivedAtEpochMillis = null,
                    contractValidatedAtEpochMillis = null,
                    assertionsEvaluatedAtEpochMillis = null,
                    completedAtEpochMillis = now,
                    historySavedAtEpochMillis = null
                ),
                requiredSecretRestorations = secretErrors.mapTo(linkedSetOf()) { error -> error.secretName }
            )
        }
    }

    private fun invalidateExecutionForMutation() {
        executionSequence.incrementAndGet()
        sendJob?.cancel()
        sendJob = null
    }

    private fun invalidateDraftForMutation() {
        draftRevision.incrementAndGet()
        invalidateExecutionForMutation()
    }

    private fun isCurrent(executionId: Long): Boolean = executionSequence.get() == executionId

    private fun preparedLifecycle() = WorkbenchExecutionLifecycle(
        phase = WorkbenchExecutionPhase.PREPARED,
        preparedAtEpochMillis = currentTimeMillis()
    )

    private fun normalizeAssertions(assertions: List<WorkbenchAssertion>): List<WorkbenchAssertion> {
        val ids = linkedSetOf<String>()
        return assertions.take(MAX_ASSERTIONS).map { assertion ->
            val candidate = assertion.id.take(MAX_ASSERTION_ID_CHARS)
            val id = candidate.takeIf { it.isNotBlank() && ids.add(it) }
                ?: generateSequence { UUID.randomUUID().toString() }.first(ids::add)
            assertion.withId(id)
        }
    }

    private fun WorkbenchAssertion.withId(id: String): WorkbenchAssertion = when (this) {
        is WorkbenchAssertion.StatusEquals -> copy(id = id)
        is WorkbenchAssertion.StatusRange -> copy(id = id)
        is WorkbenchAssertion.ResponseTimeMax -> copy(id = id)
        is WorkbenchAssertion.HeaderExists -> copy(id = id)
        is WorkbenchAssertion.HeaderEquals -> copy(id = id)
        is WorkbenchAssertion.HeaderContains -> copy(id = id)
        is WorkbenchAssertion.BodyContains -> copy(id = id)
        is WorkbenchAssertion.JsonPathExists -> copy(id = id)
        is WorkbenchAssertion.JsonPathEquals -> copy(id = id)
    }

    private fun operations(spec: SwaggerSpec?): List<WorkbenchOperationRef> = spec?.paths
        ?.flatMap { (path, methods) ->
            methods.keys.mapNotNull { method ->
                if (method.uppercase() in SUPPORTED_METHODS) WorkbenchOperationRef(path, method.uppercase())
                else null
            }
        }
        ?.sortedWith(compareBy({ it.path }, { it.method }))
        .orEmpty()

    private fun requiredSecretErrors(
        draft: WorkbenchRequestDraft,
        variables: Map<String, WorkbenchVariable>,
        assertions: List<WorkbenchAssertion> = emptyList(),
        scanLargeBody: Boolean = true
    ): List<VariableResolutionError.SecretRequired> {
        val errors = linkedSetOf<VariableResolutionError.SecretRequired>()
        fun requireSecret(name: String, location: String, value: String) {
            if (isUnavailableSecretValue(value)) {
                errors += VariableResolutionError.SecretRequired(name, location)
            }
        }

        when (val auth = draft.auth) {
            WorkbenchAuth.None -> Unit
            is WorkbenchAuth.Bearer -> requireSecret("auth.bearer", "auth.bearer", auth.token)
            is WorkbenchAuth.Basic -> requireSecret("auth.password", "auth.password", auth.password)
            is WorkbenchAuth.ApiKey -> requireSecret("auth.apiKey", "auth.apiKey.value", auth.value)
        }
        draft.query.filter { it.enabled && (it.sensitive || redactor.isSensitiveKey(it.key)) }
            .forEach { requireSecret("query.${it.key}", "query.${it.key}", it.value) }
        draft.headers.filter { it.enabled && (it.sensitive || redactor.isSensitiveKey(it.key)) }
            .forEach { requireSecret("header.${it.key}", "headers.${it.key}", it.value) }
        if (REDACTION_MARKERS.any { draft.url.contains(it, ignoreCase = true) }) {
            errors += VariableResolutionError.SecretRequired("url", "url")
        }
        if (draft.bodyEnabled && scanLargeBody &&
            REDACTION_MARKERS.any { draft.body.contains(it, ignoreCase = true) }
        ) {
            errors += VariableResolutionError.SecretRequired("body", "body")
        }
        if (draft.bodyEnabled && draft.bodySensitive && isUnavailableSecretValue(draft.body)) {
            errors += VariableResolutionError.SecretRequired("body", "body")
        }
        assertions.forEachIndexed { index, assertion ->
            if (!assertion.enabled) return@forEachIndexed
            val missing = when (assertion) {
                is WorkbenchAssertion.HeaderEquals -> assertion.sensitive &&
                    isUnavailableSecretValue(assertion.expected)
                is WorkbenchAssertion.HeaderContains -> assertion.sensitive &&
                    isUnavailableSecretValue(assertion.expected)
                is WorkbenchAssertion.BodyContains -> assertion.sensitive &&
                    isUnavailableSecretValue(assertion.expected)
                is WorkbenchAssertion.JsonPathEquals -> assertion.sensitive &&
                    isUnavailableSecretValue(assertion.expectedJson)
                else -> false
            }
            if (missing) {
                val name = assertion.id.ifBlank { (index + 1).toString() }
                errors += VariableResolutionError.SecretRequired(
                    secretName = "assertion.$name",
                    location = "assertions.$index"
                )
            }
        }

        val referenced = linkedSetOf<String>()
        fun collectTemplates(value: String) {
            TEMPLATE.findAll(value).forEach { referenced += it.groupValues[1] }
        }
        collectTemplates(draft.url)
        draft.query.filter { it.enabled }.forEach {
            collectTemplates(it.key)
            collectTemplates(it.value)
        }
        draft.headers.filter { it.enabled }.forEach {
            collectTemplates(it.key)
            collectTemplates(it.value)
        }
        if (draft.bodyEnabled && scanLargeBody) collectTemplates(draft.body)
        when (val auth = draft.auth) {
            WorkbenchAuth.None -> Unit
            is WorkbenchAuth.Bearer -> collectTemplates(auth.token)
            is WorkbenchAuth.Basic -> {
                collectTemplates(auth.username)
                collectTemplates(auth.password)
            }
            is WorkbenchAuth.ApiKey -> {
                collectTemplates(auth.name)
                collectTemplates(auth.value)
            }
        }
        variables.forEach { (name, variable) ->
            if (variable.secret && name in referenced &&
                (!variable.enabled || isUnavailableSecretValue(variable.value))
            ) {
                errors += VariableResolutionError.SecretRequired("variable.$name", "variables.$name")
            }
        }
        return errors.toList()
    }

    private fun isUnavailableSecretValue(value: String): Boolean = value.isBlank() ||
        REDACTION_MARKERS.any { value.contains(it, ignoreCase = true) }

    private fun boundedHistoryEntry(entry: WorkbenchHistoryEntry): WorkbenchHistoryEntry {
        var requestCut = entry.requestPreviewTruncated
        var responseCut = entry.response?.previewTruncated == true

        fun requestText(value: String, limit: Int): String {
            if (value.length > limit) requestCut = true
            return value.take(limit)
        }
        fun responseText(value: String, limit: Int): String {
            if (value.length > limit) responseCut = true
            return value.take(limit)
        }
        fun requestFields(fields: List<WorkbenchRequestField>): List<WorkbenchRequestField> {
            if (fields.size > MAX_HISTORY_REQUEST_FIELDS) requestCut = true
            return fields.take(MAX_HISTORY_REQUEST_FIELDS).map { field ->
                field.copy(
                    key = requestText(field.key, MAX_HISTORY_FIELD_KEY_CHARS),
                    value = requestText(field.value, MAX_HISTORY_FIELD_VALUE_CHARS)
                )
            }
        }

        val request = entry.request.copy(
            name = requestText(entry.request.name, MAX_HISTORY_NAME_CHARS),
            url = requestText(entry.request.url, MAX_HISTORY_URL_CHARS),
            query = requestFields(entry.request.query),
            headers = requestFields(entry.request.headers),
            body = requestText(entry.request.body, MAX_HISTORY_REQUEST_BODY_CHARS),
            auth = when (val auth = entry.request.auth) {
                WorkbenchAuth.None -> WorkbenchAuth.None
                is WorkbenchAuth.Bearer -> auth.copy(token = requestText(auth.token, MAX_HISTORY_SECRET_CHARS))
                is WorkbenchAuth.Basic -> auth.copy(
                    username = requestText(auth.username, MAX_HISTORY_FIELD_VALUE_CHARS),
                    password = requestText(auth.password, MAX_HISTORY_SECRET_CHARS)
                )
                is WorkbenchAuth.ApiKey -> auth.copy(
                    name = requestText(auth.name, MAX_HISTORY_FIELD_KEY_CHARS),
                    value = requestText(auth.value, MAX_HISTORY_SECRET_CHARS)
                )
            }
        )

        if (entry.variables.size > MAX_HISTORY_VARIABLES) requestCut = true
        val variables = entry.variables.entries.take(MAX_HISTORY_VARIABLES).associate { (name, variable) ->
            requestText(name, MAX_HISTORY_FIELD_KEY_CHARS) to variable.copy(
                value = requestText(variable.value, MAX_HISTORY_FIELD_VALUE_CHARS)
            )
        }

        val response = entry.response?.let { original ->
            if (original.headers.size > MAX_HISTORY_RESPONSE_HEADERS) responseCut = true
            original.copy(
                headers = original.headers.take(MAX_HISTORY_RESPONSE_HEADERS).map { header ->
                    header.copy(
                        key = responseText(header.key, MAX_HISTORY_RESPONSE_HEADER_KEY_CHARS),
                        value = responseText(header.value, MAX_HISTORY_RESPONSE_HEADER_VALUE_CHARS)
                    )
                },
                body = responseText(original.body, MAX_HISTORY_RESPONSE_BODY_CHARS),
                contentType = original.contentType?.let {
                    responseText(it, MAX_HISTORY_RESPONSE_HEADER_VALUE_CHARS)
                },
                previewTruncated = responseCut
            )
        }

        if (entry.contractViolations.size > MAX_HISTORY_VIOLATIONS) responseCut = true
        val violations = entry.contractViolations.take(MAX_HISTORY_VIOLATIONS).map { violation ->
            violation.copy(
                code = responseText(violation.code, MAX_HISTORY_VIOLATION_CODE_CHARS),
                message = responseText(violation.message, MAX_HISTORY_VIOLATION_MESSAGE_CHARS),
                jsonPath = responseText(violation.jsonPath, MAX_HISTORY_VIOLATION_PATH_CHARS)
            )
        }

        if (entry.assertions.size > MAX_ASSERTIONS) requestCut = true
        val assertions = normalizeAssertions(entry.assertions.take(MAX_ASSERTIONS)).map { assertion ->
            boundAssertionForHistory(assertion, ::requestText)
        }
        if (entry.assertionResults.size > MAX_ASSERTIONS) responseCut = true
        val assertionResults = entry.assertionResults.take(MAX_ASSERTIONS).map { result ->
            result.copy(
                assertionId = responseText(result.assertionId, MAX_ASSERTION_ID_CHARS),
                message = responseText(result.message, MAX_HISTORY_ASSERTION_MESSAGE_CHARS)
            )
        }

        return entry.copy(
            id = entry.id.take(MAX_HISTORY_ID_CHARS),
            request = request,
            variables = variables,
            response = response?.copy(previewTruncated = responseCut),
            failure = entry.failure?.copy(message = responseText(entry.failure.message, MAX_HISTORY_FAILURE_MESSAGE_CHARS)),
            contractViolations = violations,
            assertions = assertions,
            assertionResults = assertionResults,
            operation = entry.operation?.copy(
                path = responseText(entry.operation.path, MAX_HISTORY_OPERATION_PATH_CHARS),
                method = responseText(entry.operation.method, MAX_HISTORY_METHOD_CHARS)
            ),
            requestPreviewTruncated = requestCut
        )
    }

    private fun boundAssertionForHistory(
        assertion: WorkbenchAssertion,
        bound: (String, Int) -> String
    ): WorkbenchAssertion = when (assertion) {
        is WorkbenchAssertion.StatusEquals -> assertion.copy(id = bound(assertion.id, MAX_ASSERTION_ID_CHARS))
        is WorkbenchAssertion.StatusRange -> assertion.copy(id = bound(assertion.id, MAX_ASSERTION_ID_CHARS))
        is WorkbenchAssertion.ResponseTimeMax -> assertion.copy(id = bound(assertion.id, MAX_ASSERTION_ID_CHARS))
        is WorkbenchAssertion.HeaderExists -> assertion.copy(
            id = bound(assertion.id, MAX_ASSERTION_ID_CHARS),
            name = bound(assertion.name, MAX_ASSERTION_HEADER_NAME_CHARS)
        )
        is WorkbenchAssertion.HeaderEquals -> assertion.copy(
            id = bound(assertion.id, MAX_ASSERTION_ID_CHARS),
            name = bound(assertion.name, MAX_ASSERTION_HEADER_NAME_CHARS),
            expected = bound(assertion.expected, MAX_ASSERTION_EXPECTED_CHARS)
        )
        is WorkbenchAssertion.HeaderContains -> assertion.copy(
            id = bound(assertion.id, MAX_ASSERTION_ID_CHARS),
            name = bound(assertion.name, MAX_ASSERTION_HEADER_NAME_CHARS),
            expected = bound(assertion.expected, MAX_ASSERTION_EXPECTED_CHARS)
        )
        is WorkbenchAssertion.BodyContains -> assertion.copy(
            id = bound(assertion.id, MAX_ASSERTION_ID_CHARS),
            expected = bound(assertion.expected, MAX_ASSERTION_EXPECTED_CHARS)
        )
        is WorkbenchAssertion.JsonPathExists -> assertion.copy(
            id = bound(assertion.id, MAX_ASSERTION_ID_CHARS),
            path = bound(assertion.path, MAX_ASSERTION_PATH_CHARS)
        )
        is WorkbenchAssertion.JsonPathEquals -> assertion.copy(
            id = bound(assertion.id, MAX_ASSERTION_ID_CHARS),
            path = bound(assertion.path, MAX_ASSERTION_PATH_CHARS),
            expectedJson = bound(assertion.expectedJson, MAX_ASSERTION_EXPECTED_CHARS)
        )
    }

    private fun sanitizeHistory(entries: List<WorkbenchHistoryEntry>): List<WorkbenchHistoryEntry> = entries
        .asSequence()
        .map(redactor::sanitizeHistoryEntry)
        .map(::boundedHistoryEntry)
        .sortedByDescending { it.createdAtEpochMillis }
        .distinctBy { it.id }
        .take(MAX_IN_MEMORY_HISTORY)
        .toList()

    private fun mergeHistory(
        preferred: List<WorkbenchHistoryEntry>,
        existing: List<WorkbenchHistoryEntry>
    ): List<WorkbenchHistoryEntry> = sanitizeHistory(preferred + existing)

    private suspend fun <T> captureNonCancellation(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Result.failure(error)
    }

    private companion object {
        val SUPPORTED_METHODS = setOf("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS", "TRACE")
        val TEMPLATE = Regex("\\{\\{\\s*([A-Za-z0-9_.-]+)\\s*}}")
        val REDACTION_MARKERS = setOf("<redacted>", "[redacted]", "••••")

        const val MAX_IN_MEMORY_HISTORY = 100
        const val MAX_HISTORY_ID_CHARS = 512
        const val MAX_HISTORY_NAME_CHARS = 512
        const val MAX_HISTORY_URL_CHARS = 16 * 1024
        const val MAX_HISTORY_REQUEST_BODY_CHARS = 256 * 1024
        const val MAX_HISTORY_REQUEST_FIELDS = 200
        const val MAX_HISTORY_VARIABLES = 200
        const val MAX_HISTORY_FIELD_KEY_CHARS = 512
        const val MAX_HISTORY_FIELD_VALUE_CHARS = 16 * 1024
        const val MAX_HISTORY_SECRET_CHARS = 16 * 1024
        const val MAX_HISTORY_RESPONSE_BODY_CHARS = 64 * 1024
        const val MAX_HISTORY_RESPONSE_HEADERS = 100
        const val MAX_HISTORY_RESPONSE_HEADER_KEY_CHARS = 256
        const val MAX_HISTORY_RESPONSE_HEADER_VALUE_CHARS = 4 * 1024
        const val MAX_HISTORY_VIOLATIONS = 200
        const val MAX_HISTORY_VIOLATION_CODE_CHARS = 256
        const val MAX_HISTORY_VIOLATION_MESSAGE_CHARS = 4 * 1024
        const val MAX_HISTORY_VIOLATION_PATH_CHARS = 4 * 1024
        const val MAX_HISTORY_FAILURE_MESSAGE_CHARS = 4 * 1024
        const val MAX_HISTORY_OPERATION_PATH_CHARS = 8 * 1024
        const val MAX_HISTORY_METHOD_CHARS = 32
        const val MAX_ASSERTIONS = 100
        const val MAX_ASSERTION_ID_CHARS = 256
        const val MAX_ASSERTION_HEADER_NAME_CHARS = 256
        const val MAX_ASSERTION_PATH_CHARS = 2 * 1024
        const val MAX_ASSERTION_EXPECTED_CHARS = 16 * 1024
        const val MAX_HISTORY_ASSERTION_MESSAGE_CHARS = 2 * 1024
        const val MAX_CONTRACT_SOURCE_NAME_CHARS = 512
        const val MAX_ENVIRONMENT_NAME_CHARS = 256
        const val MAX_COLLECTION_NAME_CHARS = 256
        const val MAX_COLLECTION_DESCRIPTION_CHARS = 2 * 1024
        const val MAX_SAVED_REQUEST_NAME_CHARS = 256
        const val MAX_SAVED_REQUEST_BODY_BYTES = 2 * 1024 * 1024
        const val MAX_INTERACTIVE_SECRET_SCAN_CHARS = 64 * 1024
    }
}
