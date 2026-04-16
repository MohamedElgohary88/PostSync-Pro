package com.postsyncpro.viewmodel

import com.postsyncpro.domain.repository.TestChainRepository
import com.postsyncpro.domain.usecase.AutoFlowUseCase
import com.postsyncpro.domain.usecase.MergeCollectionsUseCase
import com.postsyncpro.presentation.screen.autoflow.AutoFlowViewModel
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class AutoFlowViewModelTest {

    private lateinit var autoFlowUseCase: AutoFlowUseCase
    private lateinit var mergeUseCase: MergeCollectionsUseCase
    private lateinit var chainRepo: TestChainRepository
    private lateinit var vm: AutoFlowViewModel
    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        autoFlowUseCase = mockk(relaxed = true)
        mergeUseCase = mockk(relaxed = true)
        chainRepo = mockk(relaxed = true)
        vm = AutoFlowViewModel(autoFlowUseCase, mergeUseCase, chainRepo)
    }

    @AfterTest
    fun teardown() {
        vm.onCleared()
        Dispatchers.resetMain()
    }

    // ─── Initial State ─────────────────────────────────────────

    @Test
    fun `initial state is not running`() {
        assertFalse(vm.state.value.isRunning)
    }

    @Test
    fun `initial state is not paused`() {
        assertFalse(vm.state.value.isPaused)
    }

    @Test
    fun `initial state has no plan`() {
        assertNull(vm.state.value.plan)
    }

    @Test
    fun `initial state has empty step results`() {
        assertTrue(vm.state.value.stepResults.isEmpty())
    }

    @Test
    fun `initial state has empty variables`() {
        assertTrue(vm.state.value.variables.isEmpty())
    }

    @Test
    fun `initial state has no error`() {
        assertNull(vm.state.value.error)
    }

    // ─── Base URL ──────────────────────────────────────────────

    @Test
    fun `updateBaseUrl changes url`() {
        vm.updateBaseUrl("http://api.example.com")
        assertEquals("http://api.example.com", vm.state.value.config.baseUrl)
    }

    // ─── Reset ─────────────────────────────────────────────────

    @Test
    fun `resetFlow clears results`() {
        vm.resetFlow()
        assertTrue(vm.state.value.stepResults.isEmpty())
        assertFalse(vm.state.value.isRunning)
        assertFalse(vm.state.value.isPaused)
    }
}
