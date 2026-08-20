package com.postsyncpro.workbench

import com.postsyncpro.data.repository.FileWorkbenchSavedRequestRepository
import com.postsyncpro.domain.model.ResolvedWorkbenchRequest
import com.postsyncpro.domain.model.WorkbenchAssertion
import com.postsyncpro.domain.model.WorkbenchAssertionEvaluationState
import com.postsyncpro.domain.model.WorkbenchAssertionOutcome
import com.postsyncpro.domain.model.WorkbenchEnvironment
import com.postsyncpro.domain.model.WorkbenchEnvironmentSnapshot
import com.postsyncpro.domain.model.WorkbenchExecutionResult
import com.postsyncpro.domain.model.WorkbenchFailureKind
import com.postsyncpro.domain.model.WorkbenchHistoryEntry
import com.postsyncpro.domain.model.WorkbenchRequestDraft
import com.postsyncpro.domain.model.WorkbenchResponse
import com.postsyncpro.domain.model.WorkbenchSavedCollection
import com.postsyncpro.domain.model.WorkbenchSavedRequest
import com.postsyncpro.domain.model.WorkbenchVariable
import com.postsyncpro.domain.repository.WorkbenchEnvironmentRepository
import com.postsyncpro.domain.repository.WorkbenchHistoryRepository
import com.postsyncpro.domain.repository.WorkbenchRequestRepository
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
import java.nio.file.Files
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class WorkbenchV2RaceRegressionTest {

    @Test
    fun `create environment never renames or overwrites the active environment`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val original = environment(
            id = "production",
            name = "Production",
            variables = mapOf("host" to WorkbenchVariable("prod.example.test"))
        )
        val environments = GateEnvironmentRepository(
            WorkbenchEnvironmentSnapshot(
                activeEnvironmentId = original.id,
                environments = listOf(original)
            )
        )
        val viewModel = viewModel(dispatcher, environmentRepository = environments)
        advanceUntilIdle()
        viewModel.updateVariable("host", WorkbenchVariable("staging.example.test"))

        viewModel.createEnvironment("Staging")
        advanceUntilIdle()

        val snapshot = environments.current
        val persistedOriginal = assertNotNull(snapshot.environments.firstOrNull { it.id == original.id })
        val created = assertNotNull(snapshot.environments.firstOrNull { it.name == "Staging" })
        assertEquals(2, snapshot.environments.size)
        assertEquals("Production", persistedOriginal.name)
        assertEquals("prod.example.test", persistedOriginal.variables.getValue("host").value)
        assertNotEquals(original.id, created.id)
        assertEquals("staging.example.test", created.variables.getValue("host").value)
        assertEquals(created.id, snapshot.activeEnvironmentId)
        assertEquals(listOf(created.id), environments.upsertedIds)
        viewModel.onCleared()
    }

    @Test
    fun `assertion error is an executed failing check while transport still succeeds`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = viewModel(
            dispatcher = dispatcher,
            requestRepository = CountingRequestRepository(
                WorkbenchExecutionResult.Success(response("""{"items":[]}"""))
            )
        )
        advanceUntilIdle()
        viewModel.updateDraft { it.copy(url = "https://example.test") }
        viewModel.addAssertion(
            WorkbenchAssertion.JsonPathExists(id = "invalid-path", path = "$.items[*]")
        )

        viewModel.send()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(WorkbenchAssertionEvaluationState.COMPLETED, state.lifecycle.assertionEvaluation)
        assertEquals(false, state.lifecycle.assertionsPassed)
        assertEquals(WorkbenchAssertionOutcome.ERROR, state.assertionReport?.results?.single()?.outcome)
        assertNotNull(state.lifecycle.assertionsEvaluatedAtEpochMillis)
        viewModel.onCleared()
    }

    @Test
    fun `cancelled environment load and save operations always clear busy flags`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val firstLoadGate = CompletableDeferred<Unit>()
        val environments = GateEnvironmentRepository(
            initial = WorkbenchEnvironmentSnapshot(),
            firstSnapshotGate = firstLoadGate
        )
        val viewModel = viewModel(dispatcher, environmentRepository = environments)
        testScheduler.runCurrent()
        assertTrue(viewModel.state.value.isEnvironmentLoading)

        // A second reload cancels the gated first load and completes normally.
        viewModel.reloadEnvironments()
        advanceUntilIdle()
        assertFalse(viewModel.state.value.isEnvironmentLoading)
        assertFalse(viewModel.state.value.isEnvironmentSaving)

        val saveGate = CompletableDeferred<Unit>()
        environments.upsertGate = saveGate
        viewModel.createEnvironment("Cancelled save")
        testScheduler.runCurrent()
        assertTrue(environments.upsertStarted.isCompleted)
        assertTrue(viewModel.state.value.isEnvironmentSaving)

        // Reload cancels the in-flight save; its successor owns and clears flags.
        viewModel.reloadEnvironments()
        advanceUntilIdle()
        assertFalse(viewModel.state.value.isEnvironmentLoading)
        assertFalse(viewModel.state.value.isEnvironmentSaving)
        assertTrue(environments.current.environments.isEmpty())
        viewModel.onCleared()
    }

    @Test
    fun `send is rejected during environment load and save`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val firstLoadGate = CompletableDeferred<Unit>()
        val environments = GateEnvironmentRepository(
            initial = WorkbenchEnvironmentSnapshot(),
            firstSnapshotGate = firstLoadGate
        )
        val requests = CountingRequestRepository(WorkbenchExecutionResult.Success(response("ok")))
        val viewModel = viewModel(
            dispatcher = dispatcher,
            requestRepository = requests,
            environmentRepository = environments
        )
        viewModel.updateDraft { it.copy(url = "https://example.test") }
        testScheduler.runCurrent()
        assertTrue(viewModel.state.value.isEnvironmentLoading)

        viewModel.send()
        assertEquals(0, requests.calls)
        assertFalse(viewModel.state.value.isSending)
        assertEquals(WorkbenchFailureKind.INVALID_REQUEST, viewModel.state.value.executionFailure?.kind)

        firstLoadGate.complete(Unit)
        advanceUntilIdle()
        environments.upsertGate = CompletableDeferred()
        viewModel.createEnvironment("Busy save")
        testScheduler.runCurrent()
        assertTrue(viewModel.state.value.isEnvironmentSaving)

        viewModel.send()
        assertEquals(0, requests.calls)
        assertFalse(viewModel.state.value.isSending)
        assertEquals(WorkbenchFailureKind.INVALID_REQUEST, viewModel.state.value.executionFailure?.kind)
        assertTrue(viewModel.state.value.errorMessage.orEmpty().contains("environment operation"))
        viewModel.onCleared()
        advanceUntilIdle()
    }

    @Test
    fun `library strips standalone sensitive assertion and URL user info credentials`() = runTest {
        val directory = createTempDirectory("postsync-v2-library-regression")
        val file = directory.resolve("library.json")
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = FileWorkbenchSavedRequestRepository(file = file, ioDispatcher = dispatcher)
        val assertionSecret = "standalone-library-assertion-secret-734"
        val userInfoSecret = "userinfo-library-secret-734"
        repository.upsertCollection(
            WorkbenchSavedCollection(
                id = "collection",
                name = "Regression",
                createdAtEpochMillis = 1,
                updatedAtEpochMillis = 1
            )
        )

        val saved = repository.upsertRequest(
            WorkbenchSavedRequest(
                id = "request",
                collectionId = "collection",
                name = "Secret boundary",
                draft = WorkbenchRequestDraft(
                    url = "https://user:$userInfoSecret@example.test/private"
                ),
                assertions = listOf(
                    WorkbenchAssertion.BodyContains(
                        id = "standalone-secret",
                        expected = assertionSecret,
                        sensitive = true
                    )
                ),
                createdAtEpochMillis = 2,
                updatedAtEpochMillis = 2
            )
        ).requests.single()

        val persistedText = Files.readString(file)
        val persistedAssertion = saved.assertions.single() as WorkbenchAssertion.BodyContains
        assertFalse(assertionSecret in persistedText)
        assertFalse(userInfoSecret in persistedText)
        assertTrue(persistedAssertion.enabled)
        assertTrue(persistedAssertion.sensitive)
        assertEquals("", persistedAssertion.expected)
        assertEquals("https://<redacted>@example.test/private", saved.draft.url)
        assertFalse(saved.toString().contains(assertionSecret))
        assertFalse(saved.toString().contains(userInfoSecret))
    }

    private fun viewModel(
        dispatcher: CoroutineDispatcher,
        requestRepository: CountingRequestRepository = CountingRequestRepository(
            WorkbenchExecutionResult.Success(response("ok"))
        ),
        historyRepository: WorkbenchHistoryRepository = InMemoryHistoryRepository(),
        environmentRepository: WorkbenchEnvironmentRepository? = null
    ) = WorkbenchViewModel(
        resolveVariables = ResolveWorkbenchVariablesUseCase(),
        executeRequest = ExecuteWorkbenchRequestUseCase(requestRepository),
        prepareContractRequest = PrepareContractRequestUseCase(),
        validateContractResponse = ValidateContractResponseUseCase(),
        historyRepository = historyRepository,
        redactor = SecretRedactor(),
        evaluateAssertions = EvaluateWorkbenchAssertionsUseCase(),
        environmentRepository = environmentRepository,
        ioDispatcher = dispatcher,
        mainDispatcher = dispatcher
    )

    private fun environment(
        id: String,
        name: String,
        variables: Map<String, WorkbenchVariable>
    ) = WorkbenchEnvironment(
        id = id,
        name = name,
        variables = variables,
        createdAtEpochMillis = 1,
        updatedAtEpochMillis = 1
    )

    private fun response(body: String) = WorkbenchResponse(
        statusCode = 200,
        body = body,
        contentType = "application/json",
        durationMillis = 1,
        sizeBytes = body.length.toLong(),
        receivedBytes = body.length.toLong(),
        truncated = false,
        startedAtEpochMillis = 1,
        completedAtEpochMillis = 2
    )

    private class CountingRequestRepository(
        private val result: WorkbenchExecutionResult
    ) : WorkbenchRequestRepository {
        var calls: Int = 0
            private set

        override suspend fun execute(request: ResolvedWorkbenchRequest): WorkbenchExecutionResult {
            calls++
            return result
        }
    }

    private class InMemoryHistoryRepository : WorkbenchHistoryRepository {
        private val entries = mutableListOf<WorkbenchHistoryEntry>()

        override suspend fun list(): List<WorkbenchHistoryEntry> = entries.toList()
        override suspend fun find(id: String): WorkbenchHistoryEntry? = entries.firstOrNull { it.id == id }
        override suspend fun save(entry: WorkbenchHistoryEntry): List<WorkbenchHistoryEntry> {
            entries.removeAll { it.id == entry.id }
            entries.add(0, entry)
            return entries.toList()
        }
        override suspend fun clear() = entries.clear()
    }

    private class GateEnvironmentRepository(
        initial: WorkbenchEnvironmentSnapshot,
        private val firstSnapshotGate: CompletableDeferred<Unit>? = null
    ) : WorkbenchEnvironmentRepository {
        var current: WorkbenchEnvironmentSnapshot = initial
            private set
        var upsertGate: CompletableDeferred<Unit>? = null
        var upsertStarted: CompletableDeferred<Unit> = CompletableDeferred()
        val upsertedIds = mutableListOf<String>()
        private var snapshotCalls = 0

        override suspend fun snapshot(): WorkbenchEnvironmentSnapshot {
            snapshotCalls++
            if (snapshotCalls == 1) firstSnapshotGate?.await()
            return current
        }

        override suspend fun find(id: String): WorkbenchEnvironment? =
            current.environments.firstOrNull { it.id == id }

        override suspend fun upsert(environment: WorkbenchEnvironment): WorkbenchEnvironmentSnapshot {
            return upsert(environment, select = false)
        }

        override suspend fun upsertAndSelectEnvironment(
            environment: WorkbenchEnvironment
        ): WorkbenchEnvironmentSnapshot = upsert(environment, select = true)

        private suspend fun upsert(
            environment: WorkbenchEnvironment,
            select: Boolean
        ): WorkbenchEnvironmentSnapshot {
            upsertStarted.complete(Unit)
            upsertGate?.await()
            upsertedIds += environment.id
            current = current.copy(
                activeEnvironmentId = if (select) environment.id else current.activeEnvironmentId,
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
            current = current.copy(activeEnvironmentId = id)
            return current
        }

        override suspend fun clear() {
            current = WorkbenchEnvironmentSnapshot()
        }
    }
}
