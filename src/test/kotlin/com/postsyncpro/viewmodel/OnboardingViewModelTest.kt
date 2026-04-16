package com.postsyncpro.viewmodel

import com.postsyncpro.domain.model.AiMode
import com.postsyncpro.domain.model.CloudProvider
import com.postsyncpro.domain.usecase.DetectSystemProfileUseCase
import com.postsyncpro.domain.usecase.ManageSettingsUseCase
import com.postsyncpro.presentation.screen.onboarding.OnboardingViewModel
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

    private lateinit var detectProfile: DetectSystemProfileUseCase
    private lateinit var manageSettings: ManageSettingsUseCase
    private lateinit var vm: OnboardingViewModel
    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        detectProfile = mockk(relaxed = true)
        manageSettings = mockk(relaxed = true)

        coEvery { manageSettings.loadConfig() } returns null
        every { detectProfile.invoke() } returns com.postsyncpro.domain.model.SystemProfile(
            totalRamMb = 16384,
            availableRamMb = 8192,
            osName = "macOS",
            osArch = "aarch64",
            cpuCores = 8,
            recommendedMode = AiMode.CLOUD,
            recommendation = "Cloud mode recommended"
        )
        vm = OnboardingViewModel(detectProfile, manageSettings)
    }

    @AfterTest
    fun teardown() {
        vm.onCleared()
        Dispatchers.resetMain()
    }

    // ���── Initial State ─────────────────────────────────────────

    @Test
    fun `initial state has cloud mode selected`() {
        assertEquals(AiMode.CLOUD, vm.state.value.selectedMode)
    }

    @Test
    fun `initial state has Gemini provider`() {
        assertEquals(CloudProvider.GEMINI, vm.state.value.selectedProvider)
    }

    @Test
    fun `initial state has empty api key`() {
        assertEquals("", vm.state.value.apiKey)
    }

    @Test
    fun `initial state has api key hidden`() {
        assertFalse(vm.state.value.isApiKeyVisible)
    }

    @Test
    fun `initial state is not complete`() {
        assertFalse(vm.state.value.isComplete)
    }

    @Test
    fun `initial state is not saving`() {
        assertFalse(vm.state.value.isSaving)
    }

    // ─── Mode Selection ────────────────────────────────────────

    @Test
    fun `selectMode changes mode`() {
        vm.selectMode(AiMode.LOCAL)
        assertEquals(AiMode.LOCAL, vm.state.value.selectedMode)
    }

    @Test
    fun `selectMode back to cloud`() {
        vm.selectMode(AiMode.LOCAL)
        vm.selectMode(AiMode.CLOUD)
        assertEquals(AiMode.CLOUD, vm.state.value.selectedMode)
    }

    // ─── Provider Selection ────────────────────────────────────

    @Test
    fun `selectProvider changes provider`() {
        vm.selectProvider(CloudProvider.GROQ)
        assertEquals(CloudProvider.GROQ, vm.state.value.selectedProvider)
    }

    // ─── API Key ─────────────────────────────────────���─────────

    @Test
    fun `updateApiKey changes key`() {
        vm.updateApiKey("test-key-123")
        assertEquals("test-key-123", vm.state.value.apiKey)
    }

    @Test
    fun `toggleApiKeyVisibility flips state`() {
        assertFalse(vm.state.value.isApiKeyVisible)
        vm.toggleApiKeyVisibility()
        assertTrue(vm.state.value.isApiKeyVisible)
        vm.toggleApiKeyVisibility()
        assertFalse(vm.state.value.isApiKeyVisible)
    }

    // ─── Local Model Path ──────────────────────────────────────

    @Test
    fun `updateLocalModelPath changes path`() {
        vm.updateLocalModelPath("/path/to/model")
        assertEquals("/path/to/model", vm.state.value.localModelPath)
    }

    // ─── Model Selection ───────────────────────────────────��───

    @Test
    fun `selectModel sets model id for valid model`() {
        vm.selectModel("phi3-mini")
        assertEquals("phi3-mini", vm.state.value.selectedModelId)
    }

    @Test
    fun `selectModel with invalid id does nothing`() {
        vm.selectModel("nonexistent-model")
        assertNull(vm.state.value.selectedModelId)
    }
}
