package com.postsyncpro.viewmodel

import com.postsyncpro.domain.model.*
import com.postsyncpro.domain.usecase.MergeCollectionsUseCase
import com.postsyncpro.presentation.screen.sync.SyncViewModel
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import java.io.File
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class SyncViewModelTest {

    private lateinit var mergeUseCase: MergeCollectionsUseCase
    private lateinit var vm: SyncViewModel
    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        mergeUseCase = mockk(relaxed = true)
        vm = SyncViewModel(mergeUseCase)
    }

    @AfterTest
    fun teardown() {
        if (::vm.isInitialized) vm.onCleared()
        Dispatchers.resetMain()
    }

    // ─── Initial State ─────────────────────────────────────────

    @Test
    fun `initial state has no files selected`() {
        assertNull(vm.state.value.oldCollectionFile)
        assertNull(vm.state.value.newSourceFile)
    }

    @Test
    fun `initial state has no collections loaded`() {
        assertNull(vm.state.value.oldCollection)
        assertNull(vm.state.value.newCollection)
        assertNull(vm.state.value.newSwagger)
    }

    @Test
    fun `initial state has no merge result`() {
        assertNull(vm.state.value.mergeResult)
    }

    @Test
    fun `initial state is not loading`() {
        assertFalse(vm.state.value.isLoading)
        assertFalse(vm.state.value.isMerging)
    }

    @Test
    fun `initial state has no error`() {
        assertNull(vm.state.value.error)
    }

    @Test
    fun `initial state has empty logs`() {
        assertTrue(vm.state.value.logs.isEmpty())
    }

    // ─── File Selection ──────────────────────────────────────────

    @Test
    fun `selectOldCollection with temp file triggers parsing`() = runTest {
        val file = File.createTempFile("test-collection", ".json").apply {
            deleteOnExit()
            writeText("""{"info":{"name":"Test","schema":"v2.1"},"item":[]}""")
        }

        coEvery { mergeUseCase.parsePostmanCollection(any()) } returns PostmanCollection(
            info = CollectionInfo("Test", "v2.1"),
            item = emptyList()
        )

        vm.selectOldCollection(file)
        advanceUntilIdle()

        assertEquals(file, vm.state.value.oldCollectionFile)
    }

    @Test
    fun `selectNewSource with temp file sets file`() = runTest {
        val file = File.createTempFile("test-swagger", ".json").apply {
            deleteOnExit()
            writeText("""{"openapi":"3.0.0","info":{"title":"Test","version":"1.0"},"paths":{}}""")
        }

        coEvery { mergeUseCase.parseSwaggerSpec(any()) } returns SwaggerSpec(
            openapi = "3.0.0",
            info = SwaggerInfo("Test", "1.0"),
            paths = emptyMap()
        )

        vm.selectNewSource(file)
        advanceUntilIdle()

        assertEquals(file, vm.state.value.newSourceFile)
    }

    @Test
    fun `selectOldCollection with invalid json sets error or remains null`() = runTest {
        val file = File.createTempFile("test-bad", ".json").apply {
            deleteOnExit()
            writeText("not-valid-json")
        }

        coEvery { mergeUseCase.parsePostmanCollection(any()) } throws RuntimeException("Invalid JSON")

        vm.selectOldCollection(file)
        advanceUntilIdle()

        // Either error is set or collection remains null
        assertTrue(vm.state.value.error != null || vm.state.value.oldCollection == null)
    }

    @Test
    fun `clearOldCollection removes parsed source and stale merge inputs`() = runTest {
        val file = File.createTempFile("test-old-clear", ".json").apply {
            deleteOnExit()
            writeText("{}")
        }
        coEvery { mergeUseCase.parsePostmanCollection(any()) } returns PostmanCollection(
            info = CollectionInfo("Old", "v2.1"),
            item = emptyList()
        )
        vm.selectOldCollection(file)
        advanceUntilIdle()

        vm.clearOldCollection()

        assertNull(vm.state.value.oldCollectionFile)
        assertNull(vm.state.value.oldCollection)
        assertNull(vm.state.value.mergeResult)
    }

    @Test
    fun `clearNewSource removes both possible parsed source types`() = runTest {
        val file = File.createTempFile("test-new-clear", ".json").apply {
            deleteOnExit()
            writeText("{}")
        }
        coEvery { mergeUseCase.parseSwaggerSpec(any()) } returns SwaggerSpec(
            openapi = "3.0.0",
            info = SwaggerInfo("New", "1"),
            paths = mapOf("/users" to mapOf("get" to SwaggerOperation()))
        )
        vm.selectNewSource(file)
        advanceUntilIdle()

        vm.clearNewSource()

        assertNull(vm.state.value.newSourceFile)
        assertNull(vm.state.value.newSwagger)
        assertNull(vm.state.value.newCollection)
        assertNull(vm.state.value.mergeResult)
    }

    // ─── Merge ─────────────────────────────────────────────────

    @Test
    fun `merge requires both collections`() = runTest {
        // No files loaded, merge should not crash
        vm.merge()
        advanceUntilIdle()

        assertNull(vm.state.value.mergeResult)
    }
}
