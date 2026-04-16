package com.postsyncpro.viewmodel

import com.postsyncpro.domain.model.*
import com.postsyncpro.domain.usecase.MergeCollectionsUseCase
import com.postsyncpro.domain.usecase.RunFuzzTestsUseCase
import com.postsyncpro.presentation.screen.fuzz.FuzzViewModel
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class FuzzViewModelTest {

    private lateinit var fuzzUseCase: RunFuzzTestsUseCase
    private lateinit var mergeUseCase: MergeCollectionsUseCase
    private lateinit var vm: FuzzViewModel
    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        fuzzUseCase = mockk(relaxed = true)
        mergeUseCase = mockk(relaxed = true)
        vm = FuzzViewModel(fuzzUseCase, mergeUseCase)
    }

    @AfterTest
    fun teardown() {
        vm.onCleared()
        Dispatchers.resetMain()
    }

    // ─── Initial State ─────────────────────────────────────────

    @Test
    fun `initial state has no swagger spec`() {
        assertNull(vm.state.value.swaggerSpec)
    }

    @Test
    fun `initial state has empty endpoints`() {
        assertTrue(vm.state.value.endpoints.isEmpty())
    }

    @Test
    fun `initial state has default mutations selected`() {
        val mutations = vm.state.value.selectedMutations
        assertTrue(MutationType.HAPPY_PATH in mutations)
        assertTrue(MutationType.NULL_VALUES in mutations)
        assertTrue(MutationType.EMPTY_STRINGS in mutations)
        assertTrue(MutationType.TYPE_MISMATCH in mutations)
    }

    @Test
    fun `initial state is not running`() {
        assertFalse(vm.state.value.isRunning)
        assertFalse(vm.state.value.isLoading)
    }

    @Test
    fun `initial state has empty base url`() {
        assertEquals("", vm.state.value.baseUrl)
    }

    // ─── URL and Auth ──────────────────────────────────────────

    @Test
    fun `updateBaseUrl changes base url`() {
        vm.updateBaseUrl("http://localhost:8080")
        assertEquals("http://localhost:8080", vm.state.value.baseUrl)
    }

    @Test
    fun `updateAuthHeader changes auth header`() {
        vm.updateAuthHeader("Bearer token123")
        assertEquals("Bearer token123", vm.state.value.authHeader)
    }

    // ─── Mutation Toggles ──────────────────────────────────────

    @Test
    fun `toggleMutation removes selected mutation`() {
        assertTrue(MutationType.HAPPY_PATH in vm.state.value.selectedMutations)
        vm.toggleMutation(MutationType.HAPPY_PATH)
        assertFalse(MutationType.HAPPY_PATH in vm.state.value.selectedMutations)
    }

    @Test
    fun `toggleMutation adds unselected mutation`() {
        vm.toggleMutation(MutationType.HAPPY_PATH) // remove
        vm.toggleMutation(MutationType.HAPPY_PATH) // add back
        assertTrue(MutationType.HAPPY_PATH in vm.state.value.selectedMutations)
    }

    // ─── Batch Mode ────────────────────────────────────────────

    @Test
    fun `toggleBatchMode flips state`() {
        assertFalse(vm.state.value.isBatchMode)
        vm.toggleBatchMode()
        assertTrue(vm.state.value.isBatchMode)
    }

    // ─── Endpoint Selection ────────────────────────────────────

    @Test
    fun `selectEndpoint sets selected endpoint`() {
        val endpoint = EndpointInfo("/api/test", "POST")
        vm.selectEndpoint(endpoint)
        assertEquals(endpoint, vm.state.value.selectedEndpoint)
    }

    @Test
    fun `toggleEndpoint adds and removes`() {
        val endpoint = EndpointInfo("/api/test", "POST")
        vm.toggleEndpoint(endpoint)
        assertTrue(endpoint in vm.state.value.selectedEndpoints)
        vm.toggleEndpoint(endpoint)
        assertFalse(endpoint in vm.state.value.selectedEndpoints)
    }

    @Test
    fun `selectAllEndpoints selects all available`() {
        // With no endpoints loaded, selecting all should result in empty set
        vm.selectAllEndpoints()
        assertTrue(vm.state.value.selectedEndpoints.isEmpty())
    }

    @Test
    fun `deselectAllEndpoints clears selection`() {
        val endpoint = EndpointInfo("/api/test", "POST")
        vm.toggleEndpoint(endpoint)
        vm.deselectAllEndpoints()
        assertTrue(vm.state.value.selectedEndpoints.isEmpty())
    }
}
