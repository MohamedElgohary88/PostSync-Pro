package com.postsyncpro.viewmodel

import com.postsyncpro.domain.usecase.GenerateReportUseCase
import com.postsyncpro.presentation.screen.report.ReportViewModel
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class ReportViewModelTest {

    private lateinit var generateReportUseCase: GenerateReportUseCase
    private lateinit var vm: ReportViewModel
    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        generateReportUseCase = mockk(relaxed = true)
        vm = ReportViewModel(generateReportUseCase)
    }

    @AfterTest
    fun teardown() {
        vm.onCleared()
        Dispatchers.resetMain()
    }

    // ─── Initial State ─────────────────────────────────────────

    @Test
    fun `initial state has default project name`() {
        assertEquals("PostSync Pro QA Report", vm.state.value.projectName)
    }

    @Test
    fun `initial state has zero health score`() {
        assertEquals(0f, vm.state.value.healthScore)
    }

    @Test
    fun `initial state is not generated`() {
        assertFalse(vm.state.value.isGenerated)
        assertFalse(vm.state.value.isGenerating)
    }

    @Test
    fun `initial state has no error`() {
        assertNull(vm.state.value.error)
    }

    @Test
    fun `initial state has no metrics`() {
        assertNull(vm.state.value.srsMetrics)
        assertNull(vm.state.value.fuzzMetrics)
    }

    // ─── Project Name ──────────────────────────────────────────

    @Test
    fun `updateProjectName changes name`() {
        vm.updateProjectName("My QA Report")
        assertEquals("My QA Report", vm.state.value.projectName)
    }

    @Test
    fun `updateProjectName with empty string sets empty`() {
        vm.updateProjectName("")
        assertEquals("", vm.state.value.projectName)
    }

    // ─── Load Data ─────────────────────────────────────────────

    @Test
    fun `loadDataFromValidation with empty list keeps defaults`() {
        vm.loadDataFromValidation(emptyList())
        assertEquals(0f, vm.state.value.healthScore)
    }

    @Test
    fun `loadDataFromFuzz with empty list keeps defaults`() {
        vm.loadDataFromFuzz(emptyList())
        // Should not crash with empty data
        assertNotNull(vm.state.value)
    }
}
