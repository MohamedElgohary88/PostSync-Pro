package com.postsyncpro.workbench

import com.postsyncpro.domain.model.ResolvedWorkbenchRequest
import com.postsyncpro.domain.model.SwaggerInfo
import com.postsyncpro.domain.model.SwaggerOperation
import com.postsyncpro.domain.model.SwaggerParameter
import com.postsyncpro.domain.model.SwaggerSchema
import com.postsyncpro.domain.model.SwaggerServer
import com.postsyncpro.domain.model.SwaggerSpec
import com.postsyncpro.domain.model.WorkbenchEnvironment
import com.postsyncpro.domain.model.WorkbenchEnvironmentSnapshot
import com.postsyncpro.domain.model.WorkbenchExecutionResult
import com.postsyncpro.domain.model.WorkbenchHistoryEntry
import com.postsyncpro.domain.model.WorkbenchRequestDraft
import com.postsyncpro.domain.model.WorkbenchRequiredSecret
import com.postsyncpro.domain.model.WorkbenchSecretLocation
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
import com.postsyncpro.domain.repository.WorkbenchStorageCorruptionException
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
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class WorkbenchV2ConsistencyTest {
    @Test
    fun `editing while save is delayed never clears dirty state or attaches stale identity`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val gate = CompletableDeferred<Unit>()
        val saved = GateSavedRepository(gate = gate)
        val viewModel = viewModel(dispatcher, savedRepository = saved)
        advanceUntilIdle()
        viewModel.updateDraft { it.copy(name = "A", url = "https://a.example.test") }

        viewModel.saveCurrentRequest(collectionId = "collection", name = "A")
        testScheduler.runCurrent()
        assertTrue(viewModel.state.value.isLibrarySaving)
        viewModel.updateDraft { it.copy(name = "B", url = "https://b.example.test") }
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals("B", viewModel.state.value.draft.name)
        assertTrue(viewModel.state.value.isDraftDirty)
        assertNull(viewModel.state.value.activeSavedRequestId)
        assertEquals("A", viewModel.state.value.savedWorkspace.requests.single().name)
        viewModel.onCleared()
    }

    @Test
    fun `a delayed history load cannot repopulate state after clear`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val gate = CompletableDeferred<Unit>()
        val history = GateHistoryRepository(gate)
        val viewModel = viewModel(dispatcher, historyRepository = history)
        testScheduler.runCurrent()
        assertTrue(viewModel.state.value.isHistoryLoading)

        viewModel.clearHistory()
        advanceUntilIdle()
        gate.complete(Unit)
        advanceUntilIdle()

        assertTrue(history.entries.isEmpty())
        assertTrue(viewModel.state.value.history.isEmpty())
        assertFalse(viewModel.state.value.isHistoryLoading)
        viewModel.onCleared()
    }

    @Test
    fun `OpenAPI variables prepared in scratch survive environment round trip`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val environment = WorkbenchEnvironment(
            id = "environment",
            name = "Environment",
            variables = mapOf("host" to WorkbenchVariable("named.example.test")),
            createdAtEpochMillis = 1,
            updatedAtEpochMillis = 1
        )
        val environments = MemoryEnvironmentRepository(
            WorkbenchEnvironmentSnapshot(environments = listOf(environment))
        )
        val viewModel = viewModel(dispatcher, environmentRepository = environments)
        advanceUntilIdle()
        val spec = SwaggerSpec(
            info = SwaggerInfo("Scratch", "1"),
            servers = listOf(SwaggerServer("https://api.example.test")),
            paths = mapOf(
                "/users/{userId}" to mapOf(
                    "get" to SwaggerOperation(
                        parameters = listOf(
                            SwaggerParameter(
                                name = "userId",
                                location = "path",
                                required = true,
                                schema = SwaggerSchema(type = "string"),
                                example = JsonPrimitive("42")
                            )
                        )
                    )
                )
            )
        )

        viewModel.selectOperation(spec, "/users/{userId}", "GET")
        assertEquals("42", viewModel.state.value.variables["userId"]?.value)
        viewModel.selectEnvironment(environment.id)
        advanceUntilIdle()
        viewModel.selectEnvironment(null)
        advanceUntilIdle()

        assertEquals("42", viewModel.state.value.variables["userId"]?.value)
        viewModel.onCleared()
    }

    @Test
    fun `opening a sanitized request keeps a usable selected environment secret`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val liveSecret = "live-secret-credential-734"
        val redactor = SecretRedactor()
        val history = GateHistoryRepository()
        val environment = WorkbenchEnvironment(
            id = "production",
            name = "Production",
            variables = mapOf("credential" to WorkbenchVariable(liveSecret, secret = false)),
            createdAtEpochMillis = 1,
            updatedAtEpochMillis = 1
        )
        val collection = WorkbenchSavedCollection("collection", "Collection", createdAtEpochMillis = 1, updatedAtEpochMillis = 1)
        val request = WorkbenchSavedRequest(
            id = "saved",
            collectionId = collection.id,
            name = "Saved",
            draft = WorkbenchRequestDraft(url = "https://api.example.test/{{credential}}"),
            variables = mapOf("credential" to WorkbenchVariable("", secret = true)),
            requiredSecrets = listOf(
                WorkbenchRequiredSecret(
                    "variable:credential",
                    WorkbenchSecretLocation.VARIABLE,
                    "credential"
                )
            ),
            createdAtEpochMillis = 2,
            updatedAtEpochMillis = 2
        )
        val viewModel = viewModel(
            dispatcher,
            historyRepository = history,
            environmentRepository = MemoryEnvironmentRepository(
                WorkbenchEnvironmentSnapshot(environment.id, listOf(environment))
            ),
            savedRepository = MemorySavedRepository(
                WorkbenchSavedWorkspace(collections = listOf(collection), requests = listOf(request))
            ),
            requestRepository = object : WorkbenchRequestRepository {
                override suspend fun execute(request: ResolvedWorkbenchRequest): WorkbenchExecutionResult {
                    val raw = response("""{"echo":"$liveSecret"}""")
                    return WorkbenchExecutionResult.Success(
                        response = redactor.sanitizeResponse(raw, request.sensitiveValues),
                        contractResponse = raw
                    )
                }
            }
        )
        advanceUntilIdle()

        viewModel.openSavedRequest(request.id)

        assertEquals(liveSecret, viewModel.state.value.variables["credential"]?.value)
        assertTrue(viewModel.state.value.variables["credential"]?.secret == true)
        assertTrue(viewModel.state.value.requiredSecretRestorations.isEmpty())
        viewModel.send()
        advanceUntilIdle()
        assertFalse(viewModel.state.value.response?.body.orEmpty().contains(liveSecret))
        assertFalse(history.entries.first().response?.body.orEmpty().contains(liveSecret))
        viewModel.onCleared()
    }

    @Test
    fun `corrupt stores expose explicit backup and reset recovery`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val environments = RecoverableEnvironmentRepository()
        val library = RecoverableSavedRepository()
        val viewModel = viewModel(
            dispatcher,
            environmentRepository = environments,
            savedRepository = library
        )
        advanceUntilIdle()
        assertTrue(viewModel.state.value.environmentRecoveryAvailable)
        assertTrue(viewModel.state.value.libraryRecoveryAvailable)

        viewModel.backupAndResetCorruptEnvironments()
        viewModel.backupAndResetCorruptLibrary()
        advanceUntilIdle()

        assertFalse(viewModel.state.value.environmentRecoveryAvailable)
        assertFalse(viewModel.state.value.libraryRecoveryAvailable)
        assertTrue(viewModel.state.value.environmentErrorMessage.orEmpty().contains(environments.backupPath))
        assertTrue(viewModel.state.value.libraryErrorMessage.orEmpty().contains(library.backupPath))
        assertEquals(1, environments.recoveries)
        assertEquals(1, library.recoveries)
        viewModel.onCleared()
    }

    private fun viewModel(
        dispatcher: CoroutineDispatcher,
        historyRepository: WorkbenchHistoryRepository = GateHistoryRepository(),
        environmentRepository: WorkbenchEnvironmentRepository? = null,
        savedRepository: WorkbenchSavedRequestRepository? = null,
        requestRepository: WorkbenchRequestRepository = object : WorkbenchRequestRepository {
            override suspend fun execute(request: ResolvedWorkbenchRequest) =
                WorkbenchExecutionResult.Success(response("ok"))
        }
    ) = WorkbenchViewModel(
        resolveVariables = ResolveWorkbenchVariablesUseCase(),
        executeRequest = ExecuteWorkbenchRequestUseCase(requestRepository),
        prepareContractRequest = PrepareContractRequestUseCase(),
        validateContractResponse = ValidateContractResponseUseCase(),
        historyRepository = historyRepository,
        redactor = SecretRedactor(),
        evaluateAssertions = EvaluateWorkbenchAssertionsUseCase(),
        environmentRepository = environmentRepository,
        savedRequestRepository = savedRepository,
        ioDispatcher = dispatcher,
        mainDispatcher = dispatcher
    )

    private fun response(body: String) = WorkbenchResponse(
        statusCode = 200,
        body = body,
        durationMillis = 1,
        sizeBytes = body.length.toLong(),
        receivedBytes = body.length.toLong(),
        truncated = false,
        startedAtEpochMillis = 1,
        completedAtEpochMillis = 2
    )

    private class GateHistoryRepository(
        private val listGate: CompletableDeferred<Unit>? = null
    ) : WorkbenchHistoryRepository {
        val entries = mutableListOf(
            WorkbenchHistoryEntry(
                id = "old",
                createdAtEpochMillis = 1,
                request = WorkbenchRequestDraft(url = "https://old.example.test")
            )
        )

        override suspend fun list(): List<WorkbenchHistoryEntry> {
            listGate?.await()
            return entries.toList()
        }

        override suspend fun find(id: String) = entries.firstOrNull { it.id == id }
        override suspend fun save(entry: WorkbenchHistoryEntry): List<WorkbenchHistoryEntry> {
            entries.add(0, entry)
            return entries.toList()
        }

        override suspend fun clear() = entries.clear()
    }

    private class GateSavedRepository(
        private val gate: CompletableDeferred<Unit>
    ) : WorkbenchSavedRequestRepository {
        private val collection = WorkbenchSavedCollection(
            id = "collection",
            name = "Collection",
            createdAtEpochMillis = 1,
            updatedAtEpochMillis = 1
        )
        private var workspace = WorkbenchSavedWorkspace(collections = listOf(collection))

        override suspend fun snapshot() = workspace
        override suspend fun findRequest(id: String) = workspace.requests.firstOrNull { it.id == id }
        override suspend fun search(query: String, collectionId: String?, folderId: String?) = workspace.requests
        override suspend fun upsertCollection(collection: WorkbenchSavedCollection) = workspace
        override suspend fun upsertCollectionWithRequest(
            collection: WorkbenchSavedCollection,
            request: WorkbenchSavedRequest
        ): WorkbenchSavedWorkspace {
            gate.await()
            workspace = workspace.copy(
                collections = workspace.collections.filterNot { it.id == collection.id } + collection,
                requests = workspace.requests.filterNot { it.id == request.id } + request
            )
            return workspace
        }

        override suspend fun deleteCollection(id: String) = workspace
        override suspend fun upsertFolder(folder: WorkbenchSavedFolder) = workspace
        override suspend fun deleteFolder(id: String) = workspace
        override suspend fun upsertRequest(request: WorkbenchSavedRequest): WorkbenchSavedWorkspace {
            gate.await()
            workspace = workspace.copy(requests = workspace.requests.filterNot { it.id == request.id } + request)
            return workspace
        }

        override suspend fun deleteRequest(id: String) = workspace
        override suspend fun clear() { workspace = WorkbenchSavedWorkspace() }
    }

    private class MemoryEnvironmentRepository(
        private var current: WorkbenchEnvironmentSnapshot
    ) : WorkbenchEnvironmentRepository {
        override suspend fun snapshot() = current
        override suspend fun find(id: String) = current.environments.firstOrNull { it.id == id }
        override suspend fun upsert(environment: WorkbenchEnvironment) =
            current.copy(environments = current.environments.filterNot { it.id == environment.id } + environment)
                .also { current = it }

        override suspend fun upsertAndSelectEnvironment(environment: WorkbenchEnvironment) =
            upsert(environment).copy(activeEnvironmentId = environment.id).also { current = it }

        override suspend fun delete(id: String) = current.copy(
            activeEnvironmentId = current.activeEnvironmentId.takeUnless { it == id },
            environments = current.environments.filterNot { it.id == id }
        ).also { current = it }

        override suspend fun select(id: String?) = current.copy(activeEnvironmentId = id).also { current = it }
        override suspend fun clear() { current = WorkbenchEnvironmentSnapshot() }
    }

    private class MemorySavedRepository(
        private var workspace: WorkbenchSavedWorkspace
    ) : WorkbenchSavedRequestRepository {
        override suspend fun snapshot() = workspace
        override suspend fun findRequest(id: String) = workspace.requests.firstOrNull { it.id == id }
        override suspend fun search(query: String, collectionId: String?, folderId: String?) = workspace.requests
        override suspend fun upsertCollection(collection: WorkbenchSavedCollection) = workspace
        override suspend fun upsertCollectionWithRequest(
            collection: WorkbenchSavedCollection,
            request: WorkbenchSavedRequest
        ) = workspace
        override suspend fun deleteCollection(id: String) = workspace
        override suspend fun upsertFolder(folder: WorkbenchSavedFolder) = workspace
        override suspend fun deleteFolder(id: String) = workspace
        override suspend fun upsertRequest(request: WorkbenchSavedRequest) = workspace
        override suspend fun deleteRequest(id: String) = workspace
        override suspend fun clear() { workspace = WorkbenchSavedWorkspace() }
    }

    private class RecoverableEnvironmentRepository : WorkbenchEnvironmentRepository {
        val backupPath = "/tmp/workbench-environments.corrupt.bak"
        var recoveries = 0
        private var corrupt = true
        override suspend fun snapshot(): WorkbenchEnvironmentSnapshot {
            if (corrupt) throw WorkbenchStorageCorruptionException("corrupt environments")
            return WorkbenchEnvironmentSnapshot()
        }
        override suspend fun find(id: String): WorkbenchEnvironment? = null
        override suspend fun upsert(environment: WorkbenchEnvironment) = snapshot()
        override suspend fun upsertAndSelectEnvironment(environment: WorkbenchEnvironment) = snapshot()
        override suspend fun delete(id: String) = snapshot()
        override suspend fun select(id: String?) = snapshot()
        override suspend fun backupCorruptStoreAndReset(): String {
            recoveries++
            corrupt = false
            return backupPath
        }
        override suspend fun clear() { corrupt = false }
    }

    private class RecoverableSavedRepository : WorkbenchSavedRequestRepository {
        val backupPath = "/tmp/workbench-library.corrupt.bak"
        var recoveries = 0
        private var corrupt = true
        override suspend fun snapshot(): WorkbenchSavedWorkspace {
            if (corrupt) throw WorkbenchStorageCorruptionException("corrupt library")
            return WorkbenchSavedWorkspace()
        }
        override suspend fun findRequest(id: String): WorkbenchSavedRequest? = null
        override suspend fun search(query: String, collectionId: String?, folderId: String?) = emptyList<WorkbenchSavedRequest>()
        override suspend fun upsertCollection(collection: WorkbenchSavedCollection) = snapshot()
        override suspend fun upsertCollectionWithRequest(
            collection: WorkbenchSavedCollection,
            request: WorkbenchSavedRequest
        ) = snapshot()
        override suspend fun deleteCollection(id: String) = snapshot()
        override suspend fun upsertFolder(folder: WorkbenchSavedFolder) = snapshot()
        override suspend fun deleteFolder(id: String) = snapshot()
        override suspend fun upsertRequest(request: WorkbenchSavedRequest) = snapshot()
        override suspend fun deleteRequest(id: String) = snapshot()
        override suspend fun backupCorruptStoreAndReset(): String {
            recoveries++
            corrupt = false
            return backupPath
        }
        override suspend fun clear() { corrupt = false }
    }
}
