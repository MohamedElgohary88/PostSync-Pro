package com.postsyncpro.viewmodel

import com.postsyncpro.domain.usecase.RegressionAnalysisUseCase
import com.postsyncpro.presentation.screen.regression.RegressionViewModel
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class RegressionViewModelTest {

    private lateinit var regressionUseCase: RegressionAnalysisUseCase
    private lateinit var vm: RegressionViewModel
    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        regressionUseCase = mockk(relaxed = true)
        vm = RegressionViewModel(regressionUseCase)
    }

    @AfterTest
    fun teardown() {
        vm.onCleared()
        Dispatchers.resetMain()
    }

    // ─── Initial State ─────────────────────────────────────────

    @Test
    fun `initial state has no dashboard`() {
        assertNull(vm.state.value.dashboard)
    }

    @Test
    fun `initial state is not loading`() {
        assertFalse(vm.state.value.isLoading)
    }

    @Test
    fun `initial state has no error`() {
        assertNull(vm.state.value.error)
    }

    @Test
    fun `initial state has no expanded endpoint`() {
        assertNull(vm.state.value.expandedEndpoint)
    }

    // ─── Endpoint Toggle ───────────────────────────────────────

    @Test
    fun `toggleEndpoint sets expanded endpoint`() {
        vm.toggleEndpoint("GET /api/users")
        assertEquals("GET /api/users", vm.state.value.expandedEndpoint)
    }

    @Test
    fun `toggleEndpoint same key clears expanded`() {
        vm.toggleEndpoint("GET /api/users")
        vm.toggleEndpoint("GET /api/users")
        assertNull(vm.state.value.expandedEndpoint)
    }

    @Test
    fun `toggleEndpoint different key switches expanded`() {
        vm.toggleEndpoint("GET /api/users")
        vm.toggleEndpoint("POST /api/orders")
        assertEquals("POST /api/orders", vm.state.value.expandedEndpoint)
    }
}
