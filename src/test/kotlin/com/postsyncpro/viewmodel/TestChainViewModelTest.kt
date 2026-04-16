package com.postsyncpro.viewmodel

import com.postsyncpro.domain.model.*
import com.postsyncpro.domain.repository.TestChainRepository
import com.postsyncpro.domain.usecase.MergeCollectionsUseCase
import com.postsyncpro.presentation.screen.testchain.TestChainViewModel
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class TestChainViewModelTest {

    private lateinit var chainRepo: TestChainRepository
    private lateinit var mergeUseCase: MergeCollectionsUseCase
    private lateinit var vm: TestChainViewModel
    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        chainRepo = mockk(relaxed = true)
        mergeUseCase = mockk(relaxed = true)
        vm = TestChainViewModel(chainRepo, mergeUseCase)
    }

    @AfterTest
    fun teardown() {
        vm.onCleared()
        Dispatchers.resetMain()
    }

    // ─── Initial State ─────────────────────────────────────────

    @Test
    fun `initial state has empty nodes`() {
        assertTrue(vm.state.value.nodes.isEmpty())
    }

    @Test
    fun `initial state has empty connections`() {
        assertTrue(vm.state.value.connections.isEmpty())
    }

    @Test
    fun `initial state has no selected node`() {
        assertNull(vm.state.value.selectedNodeId)
    }

    @Test
    fun `initial state has default zoom`() {
        assertEquals(1f, vm.state.value.zoom)
    }

    @Test
    fun `initial state has empty base url`() {
        assertEquals("", vm.state.value.baseUrl)
    }

    // ─── Base URL ──────────────────────────────────────────────

    @Test
    fun `updateBaseUrl changes url`() {
        vm.updateBaseUrl("http://localhost:3000")
        assertEquals("http://localhost:3000", vm.state.value.baseUrl)
    }

    // ─── Node Operations ───────────────────────────────────────

    @Test
    fun `addNode adds node to list`() {
        val endpoint = EndpointInfo("/api/users", "GET")
        vm.addNode(endpoint)
        assertEquals(1, vm.state.value.nodes.size)
    }

    @Test
    fun `addNode multiple times adds multiple nodes`() {
        val ep1 = EndpointInfo("/api/users", "GET")
        val ep2 = EndpointInfo("/api/orders", "POST")
        vm.addNode(ep1)
        vm.addNode(ep2)
        assertEquals(2, vm.state.value.nodes.size)
    }

    @Test
    fun `removeNode removes from list`() {
        val endpoint = EndpointInfo("/api/users", "GET")
        vm.addNode(endpoint)
        val nodeId = vm.state.value.nodes.first().id
        vm.removeNode(nodeId)
        assertTrue(vm.state.value.nodes.isEmpty())
    }

    @Test
    fun `selectNode sets selected node id`() {
        vm.selectNode("node-1")
        assertEquals("node-1", vm.state.value.selectedNodeId)
    }

    @Test
    fun `selectNode with null clears selection`() {
        vm.selectNode("node-1")
        vm.selectNode(null)
        assertNull(vm.state.value.selectedNodeId)
    }

    // ─── Connection Operations ─────────────────────────────────

    @Test
    fun `startConnecting sets connecting state`() {
        vm.startConnecting("node-1")
        assertTrue(vm.state.value.isConnecting)
        assertEquals("node-1", vm.state.value.connectFromNodeId)
    }

    @Test
    fun `cancelConnecting clears connecting state`() {
        vm.startConnecting("node-1")
        vm.cancelConnecting()
        assertFalse(vm.state.value.isConnecting)
        assertNull(vm.state.value.connectFromNodeId)
    }

    @Test
    fun `finishConnecting creates connection`() {
        val ep1 = EndpointInfo("/api/users", "GET")
        val ep2 = EndpointInfo("/api/orders", "POST")
        vm.addNode(ep1)
        vm.addNode(ep2)
        val id1 = vm.state.value.nodes[0].id
        val id2 = vm.state.value.nodes[1].id

        vm.startConnecting(id1)
        vm.finishConnecting(id2)

        assertEquals(1, vm.state.value.connections.size)
        assertFalse(vm.state.value.isConnecting)
    }

    // ─── Clear Chain ───────────────────────────────────────────

    @Test
    fun `clearChain removes all nodes and connections`() {
        val endpoint = EndpointInfo("/api/users", "GET")
        vm.addNode(endpoint)
        vm.clearChain()
        assertTrue(vm.state.value.nodes.isEmpty())
        assertTrue(vm.state.value.connections.isEmpty())
    }
}
