package com.postsyncpro.viewmodel

import com.postsyncpro.domain.usecase.MergeCollectionsUseCase
import com.postsyncpro.domain.usecase.SwaggerDiffUseCase
import com.postsyncpro.presentation.screen.breakingchange.BreakingChangeViewModel
import com.postsyncpro.presentation.screen.breakingchange.ChangeFilter
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class BreakingChangeViewModelTest {

    private lateinit var diffUseCase: SwaggerDiffUseCase
    private lateinit var mergeUseCase: MergeCollectionsUseCase
    private lateinit var vm: BreakingChangeViewModel
    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        diffUseCase = mockk(relaxed = true)
        mergeUseCase = mockk(relaxed = true)
        vm = BreakingChangeViewModel(diffUseCase, mergeUseCase)
    }

    @AfterTest
    fun teardown() {
        vm.onCleared()
        Dispatchers.resetMain()
    }

    // ─── Initial State ─────────────────────────────────────────

    @Test
    fun `initial state has no specs`() {
        assertNull(vm.state.value.oldSpec)
        assertNull(vm.state.value.newSpec)
    }

    @Test
    fun `initial state has empty file names`() {
        assertEquals("", vm.state.value.oldFileName)
        assertEquals("", vm.state.value.newFileName)
    }

    @Test
    fun `initial state has no diff result`() {
        assertNull(vm.state.value.diffResult)
    }

    @Test
    fun `initial state is not analyzing`() {
        assertFalse(vm.state.value.isAnalyzing)
    }

    @Test
    fun `initial filter is ALL`() {
        assertEquals(ChangeFilter.ALL, vm.state.value.filter)
    }

    // ─── Filter ────────────────────────────────────────────────

    @Test
    fun `setFilter changes filter`() {
        vm.setFilter(ChangeFilter.BREAKING_ONLY)
        assertEquals(ChangeFilter.BREAKING_ONLY, vm.state.value.filter)
    }

    @Test
    fun `setFilter back to ALL`() {
        vm.setFilter(ChangeFilter.BREAKING_ONLY)
        vm.setFilter(ChangeFilter.ALL)
        assertEquals(ChangeFilter.ALL, vm.state.value.filter)
    }

    // ─── Endpoint Toggle ───────────────────────────────────────

    @Test
    fun `toggleEndpoint adds to expanded set`() {
        vm.toggleEndpoint("GET /api/users")
        assertTrue("GET /api/users" in vm.state.value.expandedEndpoints)
    }

    @Test
    fun `toggleEndpoint twice removes from expanded set`() {
        vm.toggleEndpoint("GET /api/users")
        vm.toggleEndpoint("GET /api/users")
        assertFalse("GET /api/users" in vm.state.value.expandedEndpoints)
    }
}
