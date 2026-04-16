package com.postsyncpro.viewmodel

import com.postsyncpro.domain.usecase.AnalyzeDependenciesUseCase
import com.postsyncpro.domain.usecase.MergeCollectionsUseCase
import com.postsyncpro.presentation.screen.dependencygraph.DependencyGraphViewModel
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class DependencyGraphViewModelTest {

    private lateinit var dependencyUseCase: AnalyzeDependenciesUseCase
    private lateinit var mergeUseCase: MergeCollectionsUseCase
    private lateinit var vm: DependencyGraphViewModel
    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        dependencyUseCase = mockk(relaxed = true)
        mergeUseCase = mockk(relaxed = true)
        vm = DependencyGraphViewModel(dependencyUseCase, mergeUseCase)
    }

    @AfterTest
    fun teardown() {
        vm.onCleared()
        Dispatchers.resetMain()
    }

    // ─── Initial State ��────────────────────────────────────────

    @Test
    fun `initial state has no graph`() {
        assertNull(vm.state.value.graph)
    }

    @Test
    fun `initial state has default zoom`() {
        assertEquals(1f, vm.state.value.zoom)
    }

    @Test
    fun `initial state has no selected node`() {
        assertNull(vm.state.value.selectedNodeId)
    }

    @Test
    fun `initial state has no filter`() {
        assertNull(vm.state.value.filterCategory)
    }

    @Test
    fun `initial state is not analyzing`() {
        assertFalse(vm.state.value.isAnalyzing)
    }

    // ─── Node Selection ────────────────────────────────────────

    @Test
    fun `selectNode sets selected node`() {
        vm.selectNode("node-1")
        assertEquals("node-1", vm.state.value.selectedNodeId)
    }

    @Test
    fun `selectNode with null clears selection`() {
        vm.selectNode("node-1")
        vm.selectNode(null)
        assertNull(vm.state.value.selectedNodeId)
    }

    // ─── Filter ────────────────────────────────────────────────

    @Test
    fun `setFilter changes category`() {
        vm.setFilter("Authentication")
        assertEquals("Authentication", vm.state.value.filterCategory)
    }

    @Test
    fun `setFilter with null clears filter`() {
        vm.setFilter("Authentication")
        vm.setFilter(null)
        assertNull(vm.state.value.filterCategory)
    }

    // ─── Zoom and Offset ─────────���─────────────────────────────

    @Test
    fun `updateZoom changes zoom level`() {
        vm.updateZoom(0.5f)
        assertNotEquals(1f, vm.state.value.zoom)
    }

    @Test
    fun `updateOffset changes canvas offset`() {
        vm.updateOffset(100f, 50f)
        assertEquals(100f, vm.state.value.canvasOffsetX)
        assertEquals(50f, vm.state.value.canvasOffsetY)
    }
}
