package com.postsyncpro.viewmodel

import com.postsyncpro.domain.usecase.AnalyzeCoverageUseCase
import com.postsyncpro.domain.usecase.ExtractRulesUseCase
import com.postsyncpro.domain.usecase.MergeCollectionsUseCase
import com.postsyncpro.domain.usecase.ValidateSrsUseCase
import com.postsyncpro.presentation.screen.coverage.CoverageViewModel
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class CoverageViewModelTest {

    private lateinit var extractRules: ExtractRulesUseCase
    private lateinit var validateSrs: ValidateSrsUseCase
    private lateinit var mergeUseCase: MergeCollectionsUseCase
    private lateinit var coverageUseCase: AnalyzeCoverageUseCase
    private lateinit var vm: CoverageViewModel
    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        extractRules = mockk(relaxed = true)
        validateSrs = mockk(relaxed = true)
        mergeUseCase = mockk(relaxed = true)
        coverageUseCase = mockk(relaxed = true)
        vm = CoverageViewModel(extractRules, validateSrs, mergeUseCase, coverageUseCase)
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
    fun `initial state has no matrix`() {
        assertNull(vm.state.value.matrix)
    }

    @Test
    fun `initial state is not analyzing`() {
        assertFalse(vm.state.value.isAnalyzing)
    }

    @Test
    fun `initial state has no error`() {
        assertNull(vm.state.value.error)
    }

    @Test
    fun `initial state has no selected cell`() {
        assertNull(vm.state.value.selectedCell)
    }

    // ─── SRS Text ──────────────────────────────────────────────

    @Test
    fun `updateSrsText changes text`() {
        vm.updateSrsText("API requirements")
        assertEquals("API requirements", vm.state.value.srsText)
    }

    // ─── Cell Selection ────────────────────────────────────────

    @Test
    fun `selectCell sets selected cell`() {
        val cell = mockk<com.postsyncpro.domain.model.CoverageCell>(relaxed = true)
        vm.selectCell(cell)
        assertEquals(cell, vm.state.value.selectedCell)
    }

    @Test
    fun `selectCell with null clears selection`() {
        val cell = mockk<com.postsyncpro.domain.model.CoverageCell>(relaxed = true)
        vm.selectCell(cell)
        vm.selectCell(null)
        assertNull(vm.state.value.selectedCell)
    }
}
