package com.postsyncpro.viewmodel

import com.postsyncpro.domain.model.*
import com.postsyncpro.domain.usecase.ExtractRulesUseCase
import com.postsyncpro.domain.usecase.MergeCollectionsUseCase
import com.postsyncpro.domain.usecase.ValidateSrsUseCase
import com.postsyncpro.presentation.screen.validator.ValidatorViewModel
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class ValidatorViewModelTest {

    private lateinit var extractRules: ExtractRulesUseCase
    private lateinit var validateSrs: ValidateSrsUseCase
    private lateinit var mergeUseCase: MergeCollectionsUseCase
    private lateinit var vm: ValidatorViewModel
    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        extractRules = mockk(relaxed = true)
        validateSrs = mockk(relaxed = true)
        mergeUseCase = mockk(relaxed = true)
        vm = ValidatorViewModel(extractRules, validateSrs, mergeUseCase)
    }

    @AfterTest
    fun teardown() {
        vm.onCleared()
        Dispatchers.resetMain()
    }

    // ─── Initial State ─────────────────────────────────────────

    @Test
    fun `initial state has empty SRS text`() {
        assertEquals("", vm.state.value.srsText)
    }

    @Test
    fun `initial state has no swagger spec`() {
        assertNull(vm.state.value.swaggerSpec)
    }

    @Test
    fun `initial state has empty endpoints`() {
        assertTrue(vm.state.value.availableEndpoints.isEmpty())
    }

    @Test
    fun `initial state is not analyzing`() {
        assertFalse(vm.state.value.isAnalyzing)
        assertFalse(vm.state.value.isExtracting)
    }

    @Test
    fun `initial state is not in batch mode`() {
        assertFalse(vm.state.value.isBatchMode)
    }

    // ─── SRS Text ──────────────────────────────────────────────

    @Test
    fun `updateSrsText changes srs text`() {
        vm.updateSrsText("The API shall return 200")
        assertEquals("The API shall return 200", vm.state.value.srsText)
    }

    @Test
    fun `updateSrsText with empty string clears text`() {
        vm.updateSrsText("Some text")
        vm.updateSrsText("")
        assertEquals("", vm.state.value.srsText)
    }

    // ─── Batch Mode ────────────────────────────────────────────

    @Test
    fun `toggleBatchMode flips state`() {
        assertFalse(vm.state.value.isBatchMode)
        vm.toggleBatchMode()
        assertTrue(vm.state.value.isBatchMode)
        vm.toggleBatchMode()
        assertFalse(vm.state.value.isBatchMode)
    }

    // ─── Endpoint Selection ────────────────────────────────────

    @Test
    fun `selectEndpoint sets selected endpoint`() {
        val endpoint = EndpointInfo("/api/users", "GET")
        vm.selectEndpoint(endpoint)
        assertEquals(endpoint, vm.state.value.selectedEndpoint)
    }

    @Test
    fun `toggleEndpoint adds and removes from set`() {
        val endpoint = EndpointInfo("/api/users", "GET")
        vm.toggleEndpoint(endpoint)
        assertTrue(endpoint in vm.state.value.selectedEndpoints)
        vm.toggleEndpoint(endpoint)
        assertFalse(endpoint in vm.state.value.selectedEndpoints)
    }

    @Test
    fun `selectAllEndpoints selects all available`() = runTest {
        val file = java.io.File.createTempFile("test-swagger", ".json").apply {
            deleteOnExit()
            writeText("""{"openapi":"3.0.0","info":{"title":"T","version":"1"},"paths":{"/users":{"get":{"summary":"Get users","parameters":[],"responses":{}}}}}""")
        }

        val spec = SwaggerSpec(
            openapi = "3.0.0",
            info = SwaggerInfo("T", "1"),
            paths = mapOf("/users" to mapOf("get" to SwaggerOperation(summary = "Get users")))
        )
        coEvery { mergeUseCase.parseSwaggerSpec(any()) } returns spec

        vm.loadSwaggerFile(file)
        advanceUntilIdle()

        vm.selectAllEndpoints()
        assertEquals(vm.state.value.availableEndpoints.size, vm.state.value.selectedEndpoints.size)
    }

    @Test
    fun `deselectAllEndpoints clears set`() {
        val endpoint = EndpointInfo("/api/users", "GET")
        vm.toggleEndpoint(endpoint)
        vm.deselectAllEndpoints()
        assertTrue(vm.state.value.selectedEndpoints.isEmpty())
    }
}
