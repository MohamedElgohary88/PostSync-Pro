package com.postsyncpro.workbench

import com.postsyncpro.domain.model.ContractViolation
import com.postsyncpro.domain.model.ResolvedWorkbenchRequest
import com.postsyncpro.domain.model.SwaggerInfo
import com.postsyncpro.domain.model.SwaggerMediaType
import com.postsyncpro.domain.model.SwaggerOperation
import com.postsyncpro.domain.model.SwaggerResponse
import com.postsyncpro.domain.model.SwaggerSchema
import com.postsyncpro.domain.model.SwaggerServer
import com.postsyncpro.domain.model.SwaggerSpec
import com.postsyncpro.domain.model.VariableResolutionError
import com.postsyncpro.domain.model.WorkbenchAuth
import com.postsyncpro.domain.model.WorkbenchContractValidationState
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
import com.postsyncpro.domain.model.WorkbenchResponse
import com.postsyncpro.domain.model.WorkbenchVariable
import com.postsyncpro.domain.repository.WorkbenchHistoryRepository
import com.postsyncpro.domain.repository.WorkbenchRequestRepository
import com.postsyncpro.domain.usecase.ExecuteWorkbenchRequestUseCase
import com.postsyncpro.domain.usecase.PrepareContractRequestUseCase
import com.postsyncpro.domain.usecase.ResolveWorkbenchVariablesUseCase
import com.postsyncpro.domain.usecase.ValidateContractResponseUseCase
import com.postsyncpro.presentation.screen.workbench.WorkbenchViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class WorkbenchViewModelTest {
    @Test
    fun `missing variables fail before HTTP execution`() = runTest {
        val requests = FakeRequestRepository()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = viewModel(requests, FakeHistoryRepository(), dispatcher)
        advanceUntilIdle()
        viewModel.updateDraft { it.copy(url = "https://{{host}}/users") }

        viewModel.send()
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isSending)
        assertEquals(0, requests.calls)
        assertEquals(
            "host",
            assertIs<VariableResolutionError.Missing>(viewModel.state.value.variableErrors.single()).variableName
        )
        assertEquals(WorkbenchExecutionPhase.FAILED, viewModel.state.value.lifecycle.phase)
        assertEquals(WorkbenchExecutionOutcome.FAILED, viewModel.state.value.lifecycle.outcome)
        viewModel.onCleared()
    }

    @Test
    fun `lifecycle exposes in-flight received and saved milestones truthfully`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val responseGate = CompletableDeferred<Unit>()
        val saveGate = CompletableDeferred<Unit>()
        val history = FakeHistoryRepository(saveGate = saveGate)
        val requests = FakeRequestRepository(
            result = WorkbenchExecutionResult.Success(response("ok")),
            gate = responseGate
        )
        val viewModel = viewModel(requests, history, dispatcher)
        advanceUntilIdle()
        viewModel.updateDraft { it.copy(url = "https://example.test") }

        viewModel.send()
        testScheduler.runCurrent()
        assertEquals(WorkbenchExecutionPhase.IN_FLIGHT, viewModel.state.value.lifecycle.phase)
        assertTrue(viewModel.state.value.isSending)

        responseGate.complete(Unit)
        testScheduler.runCurrent()
        assertEquals(WorkbenchExecutionPhase.HISTORY_SAVING, viewModel.state.value.lifecycle.phase)
        assertEquals(WorkbenchExecutionOutcome.SUCCEEDED, viewModel.state.value.lifecycle.outcome)
        assertEquals(WorkbenchHistoryPersistenceState.PENDING, viewModel.state.value.lifecycle.historyPersistence)
        assertNotNull(viewModel.state.value.lifecycle.responseReceivedAtEpochMillis)
        assertEquals("ok", viewModel.state.value.response?.body)
        assertFalse(viewModel.state.value.isSending)

        saveGate.complete(Unit)
        advanceUntilIdle()
        assertEquals(WorkbenchExecutionPhase.HISTORY_SAVED, viewModel.state.value.lifecycle.phase)
        assertEquals(WorkbenchExecutionOutcome.SUCCEEDED, viewModel.state.value.lifecycle.outcome)
        assertEquals(WorkbenchHistoryPersistenceState.SAVED, viewModel.state.value.lifecycle.historyPersistence)
        assertNotNull(viewModel.state.value.lifecycle.historySavedAtEpochMillis)
        viewModel.onCleared()
    }

    @Test
    fun `history save failure cannot masquerade as execution failure or success`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val history = FakeHistoryRepository(saveFailure = IllegalStateException("disk full"))
        val viewModel = viewModel(
            FakeRequestRepository(result = WorkbenchExecutionResult.Success(response("ok"))),
            history,
            dispatcher
        )
        advanceUntilIdle()
        viewModel.updateDraft { it.copy(url = "https://example.test") }

        viewModel.send()
        advanceUntilIdle()

        val lifecycle = viewModel.state.value.lifecycle
        assertEquals(WorkbenchExecutionOutcome.SUCCEEDED, lifecycle.outcome)
        assertEquals(WorkbenchExecutionPhase.HISTORY_SAVE_FAILED, lifecycle.phase)
        assertEquals(WorkbenchHistoryPersistenceState.FAILED, lifecycle.historyPersistence)
        assertNull(lifecycle.historySavedAtEpochMillis)
        assertTrue(viewModel.state.value.errorMessage.orEmpty().contains("could not be saved"))
        assertEquals(WorkbenchHistoryPersistenceState.FAILED,
            viewModel.state.value.history.single().lifecycle?.historyPersistence)
        viewModel.onCleared()
    }

    @Test
    fun `failed HTTP execution stays failed even when its history is saved`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val result = WorkbenchExecutionResult.Failure(
            WorkbenchExecutionFailure(WorkbenchFailureKind.NETWORK, "offline")
        )
        val viewModel = viewModel(
            FakeRequestRepository(result = result),
            FakeHistoryRepository(),
            dispatcher
        )
        advanceUntilIdle()
        viewModel.updateDraft { it.copy(url = "https://example.test") }

        viewModel.send()
        advanceUntilIdle()

        assertEquals(WorkbenchExecutionPhase.HISTORY_SAVED, viewModel.state.value.lifecycle.phase)
        assertEquals(WorkbenchExecutionOutcome.FAILED, viewModel.state.value.lifecycle.outcome)
        assertEquals(WorkbenchFailureKind.NETWORK, viewModel.state.value.executionFailure?.kind)
        viewModel.onCleared()
    }

    @Test
    fun `contract validation uses transient raw response while state and history remain redacted`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val raw = response("""{"access_token":"server-secret"}""", contentType = "application/json")
        val display = raw.copy(body = """{"access_token":"<redacted>"}""")
        val history = FakeHistoryRepository()
        val viewModel = viewModel(
            FakeRequestRepository(result = WorkbenchExecutionResult.Success(display, raw)),
            history,
            dispatcher
        )
        advanceUntilIdle()
        val spec = secretContract()
        viewModel.selectOperation(spec, "/secret", "GET")

        viewModel.send()
        advanceUntilIdle()

        assertEquals("""{"access_token":"<redacted>"}""", viewModel.state.value.response?.body)
        assertTrue(viewModel.state.value.contractViolations.isEmpty())
        assertEquals(WorkbenchContractValidationState.COMPLETED,
            viewModel.state.value.lifecycle.contractValidation)
        assertEquals(true, viewModel.state.value.lifecycle.contractPassed)
        assertFalse(history.savedEntry?.response?.body.orEmpty().contains("server-secret"))
        viewModel.onCleared()
    }

    @Test
    fun `draft mutation cancels and invalidates an older in-flight response`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val gate = CompletableDeferred<Unit>()
        val requests = FakeRequestRepository(
            result = WorkbenchExecutionResult.Success(response("response-a")),
            gate = gate
        )
        val viewModel = viewModel(requests, FakeHistoryRepository(), dispatcher)
        advanceUntilIdle()
        viewModel.updateDraft { it.copy(name = "A", url = "https://a.example.test") }
        viewModel.send()
        testScheduler.runCurrent()
        assertEquals(WorkbenchExecutionPhase.IN_FLIGHT, viewModel.state.value.lifecycle.phase)

        viewModel.updateDraft { it.copy(name = "B", url = "https://b.example.test") }
        assertFalse(viewModel.state.value.isSending)
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals("B", viewModel.state.value.draft.name)
        assertEquals("https://b.example.test", viewModel.state.value.draft.url)
        assertNull(viewModel.state.value.response)
        assertEquals(WorkbenchExecutionPhase.PREPARED, viewModel.state.value.lifecycle.phase)
        assertTrue(viewModel.state.value.history.isEmpty())
        viewModel.onCleared()
    }

    @Test
    fun `reopened history requires secrets and never reuses stale contract validation`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val historical = WorkbenchHistoryEntry(
            id = "history-1",
            createdAtEpochMillis = 1,
            request = WorkbenchRequestDraft(
                url = "https://example.test/secret",
                auth = WorkbenchAuth.Bearer("")
            ),
            response = response("{}", contentType = "application/json"),
            contractViolations = listOf(ContractViolation("OLD", "old contract")),
            operation = WorkbenchOperationRef("/secret", "GET"),
            lifecycle = WorkbenchExecutionLifecycle(
                phase = WorkbenchExecutionPhase.HISTORY_SAVED,
                outcome = WorkbenchExecutionOutcome.SUCCEEDED,
                historyPersistence = WorkbenchHistoryPersistenceState.SAVED
            )
        )
        val history = FakeHistoryRepository(initial = listOf(historical))
        val requests = FakeRequestRepository()
        val viewModel = viewModel(requests, history, dispatcher)
        advanceUntilIdle()
        viewModel.setContract(secretContract())

        viewModel.reopenHistory("history-1")
        assertNull(viewModel.state.value.selectedOperation)
        assertTrue(viewModel.state.value.contractViolations.isEmpty())
        assertTrue("auth.bearer" in viewModel.state.value.requiredSecretRestorations)

        viewModel.send()
        advanceUntilIdle()
        assertEquals(0, requests.calls)
        assertEquals(WorkbenchFailureKind.INVALID_REQUEST, viewModel.state.value.executionFailure?.kind)
        assertEquals(WorkbenchExecutionOutcome.FAILED, viewModel.state.value.lifecycle.outcome)
        viewModel.onCleared()
    }

    @Test
    fun `bounded history previews are persisted reopened and kept in memory with hard caps`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val oversizedEntries = (1..105).map { index ->
            WorkbenchHistoryEntry(
                id = "loaded-$index",
                createdAtEpochMillis = index.toLong(),
                request = WorkbenchRequestDraft(url = "https://example.test/$index"),
                response = response("x".repeat(70 * 1024))
            )
        }
        val history = FakeHistoryRepository(initial = oversizedEntries)
        val viewModel = viewModel(
            FakeRequestRepository(result = WorkbenchExecutionResult.Success(response("y".repeat(70 * 1024)))),
            history,
            dispatcher
        )
        advanceUntilIdle()

        assertEquals(100, viewModel.state.value.history.size)
        assertTrue(viewModel.state.value.history.all { it.response?.body.orEmpty().length <= 64 * 1024 })
        assertTrue(viewModel.state.value.history.all { it.response?.previewTruncated == true })

        viewModel.updateDraft {
            it.copy(
                url = "https://example.test/new",
                body = "r".repeat(300 * 1024),
                bodyEnabled = true
            )
        }
        viewModel.send()
        advanceUntilIdle()

        val saved = assertNotNull(history.savedEntry)
        assertEquals(256 * 1024, saved.request.body.length)
        assertTrue(saved.requestPreviewTruncated)
        assertEquals(64 * 1024, saved.response?.body?.length)
        assertEquals(true, saved.response?.previewTruncated)

        viewModel.reopenHistory(saved.id)
        assertTrue(viewModel.state.value.replayRequiresFullRequest)
        viewModel.send()
        advanceUntilIdle()
        assertEquals(1, history.saveCalls)
        assertEquals(WorkbenchFailureKind.INVALID_REQUEST, viewModel.state.value.executionFailure?.kind)
        viewModel.onCleared()
    }

    @Test
    fun `repository failures cannot grow fallback history beyond one hundred entries`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val history = FakeHistoryRepository(saveFailure = IllegalStateException("limit"))
        val viewModel = viewModel(FakeRequestRepository(), history, dispatcher)
        advanceUntilIdle()
        viewModel.updateDraft { it.copy(url = "https://example.test") }

        repeat(105) {
            viewModel.send()
            advanceUntilIdle()
        }

        assertEquals(100, viewModel.state.value.history.size)
        assertTrue(viewModel.state.value.history.all {
            it.lifecycle?.historyPersistence == WorkbenchHistoryPersistenceState.FAILED
        })
        viewModel.onCleared()
    }

    @Test
    fun `cancel stops in-flight request and records cancelled outcome`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val gate = CompletableDeferred<Unit>()
        val requests = FakeRequestRepository(gate = gate)
        val viewModel = viewModel(requests, FakeHistoryRepository(), dispatcher)
        advanceUntilIdle()
        viewModel.updateDraft { it.copy(url = "https://example.test") }
        viewModel.send()
        testScheduler.runCurrent()

        viewModel.cancelRequest()
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isSending)
        assertEquals(WorkbenchFailureKind.CANCELLED, viewModel.state.value.executionFailure?.kind)
        assertEquals(WorkbenchExecutionPhase.CANCELLED, viewModel.state.value.lifecycle.phase)
        assertEquals(WorkbenchExecutionOutcome.CANCELLED, viewModel.state.value.lifecycle.outcome)
        viewModel.onCleared()
    }

    private fun viewModel(
        requestRepository: WorkbenchRequestRepository,
        historyRepository: WorkbenchHistoryRepository,
        dispatcher: CoroutineDispatcher
    ): WorkbenchViewModel {
        var time = 1_000L
        return WorkbenchViewModel(
            resolveVariables = ResolveWorkbenchVariablesUseCase(),
            executeRequest = ExecuteWorkbenchRequestUseCase(requestRepository),
            prepareContractRequest = PrepareContractRequestUseCase(),
            validateContractResponse = ValidateContractResponseUseCase(),
            historyRepository = historyRepository,
            ioDispatcher = dispatcher,
            mainDispatcher = dispatcher,
            currentTimeMillis = { ++time }
        )
    }

    private fun response(
        body: String,
        contentType: String? = null
    ) = WorkbenchResponse(
        statusCode = 200,
        body = body,
        contentType = contentType,
        durationMillis = 1,
        sizeBytes = body.length.toLong(),
        receivedBytes = body.length.toLong(),
        truncated = false,
        startedAtEpochMillis = 1,
        completedAtEpochMillis = 2
    )

    private fun secretContract() = SwaggerSpec(
        info = SwaggerInfo("Secrets", "1"),
        servers = listOf(SwaggerServer("https://example.test")),
        paths = mapOf(
            "/secret" to mapOf(
                "get" to SwaggerOperation(
                    responses = mapOf(
                        "200" to SwaggerResponse(
                            content = mapOf(
                                "application/json" to SwaggerMediaType(
                                    SwaggerSchema(
                                        type = "object",
                                        required = listOf("access_token"),
                                        properties = mapOf(
                                            "access_token" to SwaggerSchema(
                                                type = "string",
                                                enumValues = listOf("server-secret")
                                            )
                                        )
                                    )
                                )
                            )
                        )
                    )
                )
            )
        )
    )

    private class FakeRequestRepository(
        private val result: WorkbenchExecutionResult? = null,
        private val gate: CompletableDeferred<Unit>? = null
    ) : WorkbenchRequestRepository {
        var calls = 0
        override suspend fun execute(request: ResolvedWorkbenchRequest): WorkbenchExecutionResult {
            calls++
            gate?.await()
            return result ?: WorkbenchExecutionResult.Success(
                WorkbenchResponse(
                    statusCode = 200,
                    body = "ok",
                    durationMillis = 1,
                    sizeBytes = 2,
                    receivedBytes = 2,
                    truncated = false,
                    startedAtEpochMillis = 1,
                    completedAtEpochMillis = 2
                )
            )
        }
    }

    private class FakeHistoryRepository(
        initial: List<WorkbenchHistoryEntry> = emptyList(),
        private val saveGate: CompletableDeferred<Unit>? = null,
        private val saveFailure: Exception? = null
    ) : WorkbenchHistoryRepository {
        private val entries = initial.toMutableList()
        var savedEntry: WorkbenchHistoryEntry? = null
        var saveCalls: Int = 0

        override suspend fun list(): List<WorkbenchHistoryEntry> = entries.toList()
        override suspend fun find(id: String): WorkbenchHistoryEntry? = entries.firstOrNull { it.id == id }
        override suspend fun save(entry: WorkbenchHistoryEntry): List<WorkbenchHistoryEntry> {
            saveCalls++
            savedEntry = entry
            saveGate?.await()
            saveFailure?.let { throw it }
            entries.removeAll { it.id == entry.id }
            entries.add(0, entry)
            return entries.toList()
        }
        override suspend fun clear() = entries.clear()
    }
}
