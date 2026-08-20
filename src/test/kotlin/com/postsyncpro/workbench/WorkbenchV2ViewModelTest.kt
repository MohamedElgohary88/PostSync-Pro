package com.postsyncpro.workbench

import com.postsyncpro.domain.model.ResolvedWorkbenchRequest
import com.postsyncpro.domain.model.WorkbenchAssertion
import com.postsyncpro.domain.model.WorkbenchAssertionEvaluationState
import com.postsyncpro.domain.model.WorkbenchAssertionOutcome
import com.postsyncpro.domain.model.WorkbenchAuth
import com.postsyncpro.domain.model.WorkbenchEnvironment
import com.postsyncpro.domain.model.WorkbenchEnvironmentSnapshot
import com.postsyncpro.domain.model.WorkbenchExecutionOutcome
import com.postsyncpro.domain.model.WorkbenchExecutionFailure
import com.postsyncpro.domain.model.WorkbenchExecutionResult
import com.postsyncpro.domain.model.WorkbenchFailureKind
import com.postsyncpro.domain.model.WorkbenchHistoryEntry
import com.postsyncpro.domain.model.WorkbenchRequestDraft
import com.postsyncpro.domain.model.WorkbenchResponse
import com.postsyncpro.domain.model.WorkbenchSavedCollection
import com.postsyncpro.domain.model.WorkbenchSavedFolder
import com.postsyncpro.domain.model.WorkbenchSavedRequest
import com.postsyncpro.domain.model.WorkbenchSavedWorkspace
import com.postsyncpro.domain.model.WorkbenchVariable
import com.postsyncpro.domain.repository.WorkbenchEnvironmentRepository
import com.postsyncpro.domain.repository.WorkbenchHistoryRepository
import com.postsyncpro.domain.repository.WorkbenchRequestRepository
import com.postsyncpro.domain.repository.WorkbenchSavedRequestRepository
import com.postsyncpro.domain.usecase.EvaluateWorkbenchAssertionsUseCase
import com.postsyncpro.domain.usecase.ExecuteWorkbenchRequestUseCase
import com.postsyncpro.domain.usecase.PrepareContractRequestUseCase
import com.postsyncpro.domain.usecase.ResolveWorkbenchVariablesUseCase
import com.postsyncpro.domain.usecase.SecretRedactor
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class WorkbenchV2ViewModelTest {

    @Test
    fun `assertions evaluate raw response while state and history remain redacted`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val raw = response("""{"access_token":"server-secret"}""")
        val display = raw.copy(body = """{"access_token":"<redacted>"}""")
        val history = FakeHistoryRepository()
        val viewModel = viewModel(
            requestRepository = FakeRequestRepository(WorkbenchExecutionResult.Success(display, raw)),
            historyRepository = history,
            dispatcher = dispatcher
        )
        advanceUntilIdle()
        viewModel.updateDraft {
            it.copy(url = "https://example.test", auth = WorkbenchAuth.Bearer("request-secret"))
        }
        viewModel.addAssertion(
            WorkbenchAssertion.JsonPathEquals(
                id = "token-check",
                path = "$.access_token",
                expectedJson = "\"server-secret\""
            )
        )

        viewModel.send()
        advanceUntilIdle()

        assertEquals(WorkbenchExecutionOutcome.SUCCEEDED, viewModel.state.value.lifecycle.outcome)
        assertEquals(WorkbenchAssertionEvaluationState.COMPLETED, viewModel.state.value.lifecycle.assertionEvaluation)
        assertEquals(true, viewModel.state.value.lifecycle.assertionsPassed)
        assertEquals(WorkbenchAssertionOutcome.PASSED, viewModel.state.value.assertionReport?.results?.single()?.outcome)
        assertFalse(viewModel.state.value.response?.body.orEmpty().contains("server-secret"))
        assertFalse(history.saved?.response?.body.orEmpty().contains("server-secret"))
        assertFalse(history.saved?.assertionResults?.single()?.message.orEmpty().contains("server-secret"))
        assertFalse(history.saved.toString().contains("request-secret"))
        viewModel.onCleared()
    }

    @Test
    fun `failed assertion is separate from a successful transport outcome`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = viewModel(
            requestRepository = FakeRequestRepository(WorkbenchExecutionResult.Success(response("ok"))),
            historyRepository = FakeHistoryRepository(),
            dispatcher = dispatcher
        )
        advanceUntilIdle()
        viewModel.updateDraft { it.copy(url = "https://example.test") }
        viewModel.addAssertion(WorkbenchAssertion.StatusEquals(id = "status", expected = 201))

        viewModel.send()
        advanceUntilIdle()

        assertEquals(WorkbenchExecutionOutcome.SUCCEEDED, viewModel.state.value.lifecycle.outcome)
        assertEquals(false, viewModel.state.value.lifecycle.assertionsPassed)
        assertEquals(WorkbenchAssertionOutcome.FAILED, viewModel.state.value.assertionReport?.results?.single()?.outcome)
        viewModel.onCleared()
    }

    @Test
    fun `sensitive assertion operand is evaluated transiently and stripped from history`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val secret = "standalone-history-assertion-secret-734"
        val raw = response("the payload contains $secret")
        val display = raw.copy(body = "the payload contains <redacted>")
        val history = FakeHistoryRepository()
        val requests = FakeRequestRepository(WorkbenchExecutionResult.Success(display, raw))
        val viewModel = viewModel(
            requestRepository = requests,
            historyRepository = history,
            dispatcher = dispatcher
        )
        advanceUntilIdle()
        viewModel.updateDraft { it.copy(url = "https://example.test") }
        viewModel.addAssertion(
            WorkbenchAssertion.BodyContains(id = "secret-check", expected = secret, sensitive = true)
        )

        viewModel.send()
        advanceUntilIdle()

        assertEquals(WorkbenchAssertionOutcome.PASSED, viewModel.state.value.assertionReport?.results?.single()?.outcome)
        val persisted = history.saved?.assertions?.single() as WorkbenchAssertion.BodyContains
        assertTrue(persisted.enabled)
        assertTrue(persisted.sensitive)
        assertEquals("", persisted.expected)
        assertFalse(history.saved.toString().contains(secret))

        viewModel.reopenHistory(requireNotNull(history.saved).id)
        assertTrue(viewModel.state.value.requiredSecretRestorations.any { it.startsWith("assertion.") })
        viewModel.send()
        advanceUntilIdle()
        assertEquals(1, requests.calls)
        assertEquals(WorkbenchFailureKind.INVALID_REQUEST, viewModel.state.value.executionFailure?.kind)
        viewModel.onCleared()
    }

    @Test
    fun `URL user info credentials are stripped from failed request history`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val secret = "userinfo-history-secret-734"
        val history = FakeHistoryRepository()
        val failure = WorkbenchExecutionResult.Failure(
            WorkbenchExecutionFailure(WorkbenchFailureKind.INVALID_REQUEST, "URL user-info is not allowed")
        )
        val viewModel = viewModel(FakeRequestRepository(failure), history, dispatcher)
        advanceUntilIdle()
        viewModel.updateDraft { it.copy(url = "https://user:$secret@example.test/private") }

        viewModel.send()
        advanceUntilIdle()

        assertFalse(history.saved?.request?.url.orEmpty().contains(secret))
        assertTrue(history.saved?.request?.url.orEmpty().contains("<redacted>@"))
        assertFalse(history.saved.toString().contains(secret))
        viewModel.onCleared()
    }

    @Test
    fun `editing assertions invalidates an in-flight response and clears spinner`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val gate = CompletableDeferred<Unit>()
        val history = FakeHistoryRepository()
        val viewModel = viewModel(
            requestRepository = FakeRequestRepository(WorkbenchExecutionResult.Success(response("old")), gate),
            historyRepository = history,
            dispatcher = dispatcher
        )
        advanceUntilIdle()
        viewModel.updateDraft { it.copy(url = "https://example.test") }
        viewModel.send()
        testScheduler.runCurrent()
        assertTrue(viewModel.state.value.isSending)

        viewModel.addAssertion(WorkbenchAssertion.StatusEquals(id = "new-check"))
        assertFalse(viewModel.state.value.isSending)
        gate.complete(Unit)
        advanceUntilIdle()

        assertNull(viewModel.state.value.response)
        assertNull(viewModel.state.value.assertionReport)
        assertTrue(history.entries.isEmpty())
        viewModel.onCleared()
    }

    @Test
    fun `environment switch invalidates in-flight execution and keeps selected variables`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val gate = CompletableDeferred<Unit>()
        val environment = WorkbenchEnvironment(
            id = "staging",
            name = "Staging",
            variables = mapOf("host" to WorkbenchVariable("staging.example.test")),
            createdAtEpochMillis = 1,
            updatedAtEpochMillis = 1
        )
        val environments = FakeEnvironmentRepository(WorkbenchEnvironmentSnapshot(environments = listOf(environment)))
        val history = FakeHistoryRepository()
        val viewModel = viewModel(
            requestRepository = FakeRequestRepository(WorkbenchExecutionResult.Success(response("old")), gate),
            historyRepository = history,
            dispatcher = dispatcher,
            environmentRepository = environments
        )
        advanceUntilIdle()
        viewModel.updateDraft { it.copy(url = "https://example.test") }
        viewModel.send()
        testScheduler.runCurrent()

        viewModel.selectEnvironment("staging")
        testScheduler.runCurrent()
        gate.complete(Unit)
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isSending)
        assertNull(viewModel.state.value.response)
        assertEquals("staging", viewModel.state.value.environmentSnapshot.activeEnvironmentId)
        assertEquals("staging.example.test", viewModel.state.value.variables["host"]?.value)
        assertTrue(history.entries.isEmpty())
        viewModel.onCleared()
    }

    @Test
    fun `environment creation calls only the atomic upsert and select API`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val environments = FakeEnvironmentRepository(WorkbenchEnvironmentSnapshot())
        val viewModel = viewModel(
            requestRepository = FakeRequestRepository(WorkbenchExecutionResult.Success(response("ok"))),
            historyRepository = FakeHistoryRepository(),
            dispatcher = dispatcher,
            environmentRepository = environments
        )
        advanceUntilIdle()

        viewModel.createEnvironment("Atomic environment")
        advanceUntilIdle()

        assertEquals(1, environments.atomicUpsertCalls)
        assertEquals(0, environments.legacyUpsertCalls)
        assertEquals(0, environments.selectCalls)
        assertEquals(
            "Atomic environment",
            viewModel.state.value.environmentSnapshot.activeEnvironment?.name
        )
        viewModel.onCleared()
    }

    @Test
    fun `collection creation and first save call only the atomic repository API`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val library = FakeSavedRepository()
        val viewModel = viewModel(
            requestRepository = FakeRequestRepository(WorkbenchExecutionResult.Success(response("ok"))),
            historyRepository = FakeHistoryRepository(),
            dispatcher = dispatcher,
            savedRequestRepository = library
        )
        advanceUntilIdle()

        viewModel.createCollectionAndSaveRequest("Atomic collection", "First request")
        advanceUntilIdle()

        assertEquals(1, library.atomicUpsertCalls)
        assertEquals(0, library.collectionUpsertCalls)
        assertEquals(0, library.requestUpsertCalls)
        assertEquals("Atomic collection", viewModel.state.value.savedWorkspace.collections.single().name)
        assertEquals("First request", viewModel.state.value.savedWorkspace.requests.single().name)
        viewModel.onCleared()
    }

    private fun viewModel(
        requestRepository: WorkbenchRequestRepository,
        historyRepository: WorkbenchHistoryRepository,
        dispatcher: CoroutineDispatcher,
        environmentRepository: WorkbenchEnvironmentRepository? = null,
        savedRequestRepository: WorkbenchSavedRequestRepository? = null
    ): WorkbenchViewModel = WorkbenchViewModel(
        resolveVariables = ResolveWorkbenchVariablesUseCase(),
        executeRequest = ExecuteWorkbenchRequestUseCase(requestRepository),
        prepareContractRequest = PrepareContractRequestUseCase(),
        validateContractResponse = ValidateContractResponseUseCase(),
        historyRepository = historyRepository,
        redactor = SecretRedactor(),
        evaluateAssertions = EvaluateWorkbenchAssertionsUseCase(),
        environmentRepository = environmentRepository,
        savedRequestRepository = savedRequestRepository,
        ioDispatcher = dispatcher,
        mainDispatcher = dispatcher
    )

    private fun response(body: String) = WorkbenchResponse(
        statusCode = 200,
        body = body,
        contentType = "application/json",
        durationMillis = 10,
        sizeBytes = body.length.toLong(),
        receivedBytes = body.length.toLong(),
        truncated = false,
        startedAtEpochMillis = 1,
        completedAtEpochMillis = 2
    )

    private class FakeRequestRepository(
        private val result: WorkbenchExecutionResult,
        private val gate: CompletableDeferred<Unit>? = null
    ) : WorkbenchRequestRepository {
        var calls: Int = 0
            private set

        override suspend fun execute(request: ResolvedWorkbenchRequest): WorkbenchExecutionResult {
            calls++
            gate?.await()
            return result
        }
    }

    private class FakeHistoryRepository : WorkbenchHistoryRepository {
        val entries = mutableListOf<WorkbenchHistoryEntry>()
        var saved: WorkbenchHistoryEntry? = null
        override suspend fun list(): List<WorkbenchHistoryEntry> = entries.toList()
        override suspend fun find(id: String): WorkbenchHistoryEntry? = entries.firstOrNull { it.id == id }
        override suspend fun save(entry: WorkbenchHistoryEntry): List<WorkbenchHistoryEntry> {
            saved = entry
            entries.removeAll { it.id == entry.id }
            entries.add(0, entry)
            return entries.toList()
        }
        override suspend fun clear() = entries.clear()
    }

    private class FakeEnvironmentRepository(
        private var current: WorkbenchEnvironmentSnapshot
    ) : WorkbenchEnvironmentRepository {
        var legacyUpsertCalls = 0
            private set
        var atomicUpsertCalls = 0
            private set
        var selectCalls = 0
            private set

        override suspend fun snapshot(): WorkbenchEnvironmentSnapshot = current
        override suspend fun find(id: String): WorkbenchEnvironment? = current.environments.firstOrNull { it.id == id }
        override suspend fun upsert(environment: WorkbenchEnvironment): WorkbenchEnvironmentSnapshot {
            legacyUpsertCalls++
            current = current.copy(environments = current.environments.filterNot { it.id == environment.id } + environment)
            return current
        }
        override suspend fun upsertAndSelectEnvironment(
            environment: WorkbenchEnvironment
        ): WorkbenchEnvironmentSnapshot {
            atomicUpsertCalls++
            current = current.copy(
                activeEnvironmentId = environment.id,
                environments = current.environments.filterNot { it.id == environment.id } + environment
            )
            return current
        }
        override suspend fun delete(id: String): WorkbenchEnvironmentSnapshot {
            current = current.copy(
                activeEnvironmentId = current.activeEnvironmentId.takeUnless { it == id },
                environments = current.environments.filterNot { it.id == id }
            )
            return current
        }
        override suspend fun select(id: String?): WorkbenchEnvironmentSnapshot {
            selectCalls++
            current = current.copy(activeEnvironmentId = id)
            return current
        }
        override suspend fun clear() {
            current = WorkbenchEnvironmentSnapshot()
        }
    }

    private class FakeSavedRepository : WorkbenchSavedRequestRepository {
        private var workspace = WorkbenchSavedWorkspace()
        var collectionUpsertCalls = 0
            private set
        var requestUpsertCalls = 0
            private set
        var atomicUpsertCalls = 0
            private set

        override suspend fun snapshot() = workspace
        override suspend fun findRequest(id: String) = workspace.requests.firstOrNull { it.id == id }
        override suspend fun search(query: String, collectionId: String?, folderId: String?) = workspace.requests
        override suspend fun upsertCollection(collection: WorkbenchSavedCollection): WorkbenchSavedWorkspace {
            collectionUpsertCalls++
            workspace = workspace.copy(collections = workspace.collections.filterNot { it.id == collection.id } + collection)
            return workspace
        }
        override suspend fun upsertCollectionWithRequest(
            collection: WorkbenchSavedCollection,
            request: WorkbenchSavedRequest
        ): WorkbenchSavedWorkspace {
            atomicUpsertCalls++
            require(request.collectionId == collection.id)
            workspace = workspace.copy(
                collections = workspace.collections.filterNot { it.id == collection.id } + collection,
                requests = workspace.requests.filterNot { it.id == request.id } + request
            )
            return workspace
        }
        override suspend fun deleteCollection(id: String): WorkbenchSavedWorkspace {
            workspace = workspace.copy(
                collections = workspace.collections.filterNot { it.id == id },
                folders = workspace.folders.filterNot { it.collectionId == id },
                requests = workspace.requests.filterNot { it.collectionId == id }
            )
            return workspace
        }
        override suspend fun upsertFolder(folder: WorkbenchSavedFolder): WorkbenchSavedWorkspace = workspace
        override suspend fun deleteFolder(id: String): WorkbenchSavedWorkspace = workspace
        override suspend fun upsertRequest(request: WorkbenchSavedRequest): WorkbenchSavedWorkspace {
            requestUpsertCalls++
            workspace = workspace.copy(requests = workspace.requests.filterNot { it.id == request.id } + request)
            return workspace
        }
        override suspend fun deleteRequest(id: String): WorkbenchSavedWorkspace {
            workspace = workspace.copy(requests = workspace.requests.filterNot { it.id == id })
            return workspace
        }
        override suspend fun clear() {
            workspace = WorkbenchSavedWorkspace()
        }
    }
}
