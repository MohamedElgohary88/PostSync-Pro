package com.postsyncpro.viewmodel

import com.postsyncpro.domain.model.MockRequestLog
import com.postsyncpro.domain.repository.MockServerRepository
import com.postsyncpro.domain.usecase.MergeCollectionsUseCase
import com.postsyncpro.presentation.screen.mockserver.MockServerViewModel
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class MockServerViewModelTest {

    private lateinit var mockServerRepo: MockServerRepository
    private lateinit var mergeUseCase: MergeCollectionsUseCase
    private lateinit var vm: MockServerViewModel
    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        mockServerRepo = mockk(relaxed = true)
        mergeUseCase = mockk(relaxed = true)

        every { mockServerRepo.requestLogs } returns MutableStateFlow(emptyList())
        every { mockServerRepo.isRunning() } returns false

        vm = MockServerViewModel(mockServerRepo, mergeUseCase)
    }

    @AfterTest
    fun teardown() {
        vm.onCleared()
        Dispatchers.resetMain()
    }

    // ─── Initial State ─────────────────────────────────────────

    @Test
    fun `initial port is 8080`() {
        assertEquals(8080, vm.state.value.port)
    }

    @Test
    fun `initial state is not running`() {
        assertFalse(vm.state.value.isRunning)
    }

    @Test
    fun `initial state has validation enabled`() {
        assertTrue(vm.state.value.enableValidation)
    }

    @Test
    fun `initial state has logging enabled`() {
        assertTrue(vm.state.value.enableLogging)
    }

    @Test
    fun `initial state has no swagger`() {
        assertNull(vm.state.value.swaggerSpec)
    }

    @Test
    fun `initial state has empty routes`() {
        assertTrue(vm.state.value.routes.isEmpty())
    }

    @Test
    fun `initial request counts are zero`() {
        assertEquals(0, vm.state.value.totalRequests)
        assertEquals(0, vm.state.value.successRequests)
        assertEquals(0, vm.state.value.failedRequests)
    }

    // ─── Port Update ───────────────────────────────────────────

    @Test
    fun `updatePort changes port`() {
        vm.updatePort("9090")
        assertEquals(9090, vm.state.value.port)
    }

    @Test
    fun `updatePort with invalid string keeps current port`() {
        vm.updatePort("abc")
        assertEquals(8080, vm.state.value.port)
    }

    // ─── Toggles ───────────────────────────────────────────────

    @Test
    fun `toggleValidation flips state`() {
        assertTrue(vm.state.value.enableValidation)
        vm.toggleValidation()
        assertFalse(vm.state.value.enableValidation)
        vm.toggleValidation()
        assertTrue(vm.state.value.enableValidation)
    }

    @Test
    fun `toggleLogging flips state`() {
        assertTrue(vm.state.value.enableLogging)
        vm.toggleLogging()
        assertFalse(vm.state.value.enableLogging)
        vm.toggleLogging()
        assertTrue(vm.state.value.enableLogging)
    }

    // ─── Clear Logs ────────────────────────────────────────────

    @Test
    fun `clearLogs delegates to repository`() {
        vm.clearLogs()
        verify { mockServerRepo.clearLogs() }
    }
}
