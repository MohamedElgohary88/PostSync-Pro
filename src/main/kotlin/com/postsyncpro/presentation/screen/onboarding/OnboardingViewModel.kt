package com.postsyncpro.presentation.screen.onboarding

import com.postsyncpro.data.network.ModelDownloader
import com.postsyncpro.domain.model.*
import com.postsyncpro.domain.usecase.DetectSystemProfileUseCase
import com.postsyncpro.domain.usecase.ManageSettingsUseCase
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class OnboardingState(
    val systemProfile: SystemProfile? = null,
    val selectedMode: AiMode = AiMode.CLOUD,
    val selectedProvider: CloudProvider = CloudProvider.GEMINI,
    val apiKey: String = "",
    val isApiKeyVisible: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null,
    val isComplete: Boolean = false,
    // Local AI model state
    val localModelPath: String = "",
    val selectedModelId: String? = null,
    val downloadProgress: Map<String, ModelDownloadProgress> = emptyMap(),
    val downloadedModels: Set<String> = emptySet()
)

class OnboardingViewModel(
    private val detectSystemProfile: DetectSystemProfileUseCase,
    private val manageSettings: ManageSettingsUseCase
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val _state = MutableStateFlow(OnboardingState())
    val state: StateFlow<OnboardingState> = _state.asStateFlow()

    private val modelDownloader = ModelDownloader()
    private val downloadJobs = mutableMapOf<String, Job>()

    init {
        detectSystem()
        checkExistingModels()
        loadExistingConfig()
    }

    private fun detectSystem() {
        val profile = detectSystemProfile()
        _state.value = _state.value.copy(
            systemProfile = profile,
            selectedMode = profile.recommendedMode
        )
    }

    private fun checkExistingModels() {
        val downloaded = mutableSetOf<String>()
        AvailableModels.models.forEach { model ->
            if (modelDownloader.isModelDownloaded(model)) {
                downloaded.add(model.id)
            }
        }
        if (downloaded.isNotEmpty()) {
            val firstDownloaded = AvailableModels.models.first { it.id in downloaded }
            _state.value = _state.value.copy(
                downloadedModels = downloaded,
                selectedModelId = firstDownloaded.id,
                localModelPath = modelDownloader.getModelPath(firstDownloaded)
            )
        }
    }

    private fun loadExistingConfig() {
        scope.launch {
            val config = manageSettings.loadConfig() ?: return@launch
            _state.value = _state.value.copy(selectedMode = config.mode)
            config.cloudConfig?.let { cloud ->
                _state.value = _state.value.copy(
                    selectedProvider = cloud.provider,
                    apiKey = cloud.apiKey
                )
            }
            config.localConfig?.let { local ->
                val matchedModel = AvailableModels.models.firstOrNull { it.fileName == local.modelName }
                _state.value = _state.value.copy(
                    localModelPath = local.modelPath,
                    selectedModelId = matchedModel?.id ?: _state.value.selectedModelId
                )
            }
        }
    }

    fun selectMode(mode: AiMode) {
        _state.value = _state.value.copy(selectedMode = mode)
    }

    fun selectProvider(provider: CloudProvider) {
        _state.value = _state.value.copy(selectedProvider = provider)
    }

    fun updateApiKey(key: String) {
        _state.value = _state.value.copy(apiKey = key)
    }

    fun toggleApiKeyVisibility() {
        _state.value = _state.value.copy(isApiKeyVisible = !_state.value.isApiKeyVisible)
    }

    fun updateLocalModelPath(path: String) {
        _state.value = _state.value.copy(localModelPath = path)
    }

    fun selectModel(modelId: String) {
        val model = AvailableModels.getById(modelId) ?: return
        _state.value = _state.value.copy(
            selectedModelId = modelId,
            localModelPath = modelDownloader.getModelPath(model)
        )
    }

    fun downloadModel(modelId: String) {
        val model = AvailableModels.getById(modelId) ?: return

        // Cancel existing download for this model
        downloadJobs[modelId]?.cancel()

        val job = scope.launch {
            modelDownloader.downloadModel(model).collect { progress ->
                val currentProgress = _state.value.downloadProgress.toMutableMap()
                currentProgress[modelId] = progress

                val currentDownloaded = _state.value.downloadedModels.toMutableSet()
                if (progress.status == DownloadStatus.COMPLETED) {
                    currentDownloaded.add(modelId)
                }

                _state.value = _state.value.copy(
                    downloadProgress = currentProgress,
                    downloadedModels = currentDownloaded,
                    // Auto-select model when download completes
                    selectedModelId = if (progress.status == DownloadStatus.COMPLETED) modelId else _state.value.selectedModelId,
                    localModelPath = if (progress.status == DownloadStatus.COMPLETED) modelDownloader.getModelPath(model) else _state.value.localModelPath
                )
            }
        }
        downloadJobs[modelId] = job
    }

    fun cancelDownload(modelId: String) {
        downloadJobs[modelId]?.cancel()
        downloadJobs.remove(modelId)
        val currentProgress = _state.value.downloadProgress.toMutableMap()
        currentProgress[modelId] = (currentProgress[modelId] ?: ModelDownloadProgress(modelId)).copy(
            status = DownloadStatus.PAUSED
        )
        _state.value = _state.value.copy(downloadProgress = currentProgress)
    }

    fun deleteModel(modelId: String) {
        val model = AvailableModels.getById(modelId) ?: return
        modelDownloader.deleteModel(model)
        val currentDownloaded = _state.value.downloadedModels.toMutableSet()
        currentDownloaded.remove(modelId)
        val currentProgress = _state.value.downloadProgress.toMutableMap()
        currentProgress.remove(modelId)
        _state.value = _state.value.copy(
            downloadedModels = currentDownloaded,
            downloadProgress = currentProgress,
            selectedModelId = if (_state.value.selectedModelId == modelId) null else _state.value.selectedModelId,
            localModelPath = if (_state.value.selectedModelId == modelId) "" else _state.value.localModelPath
        )
    }

    fun saveAndContinue() {
        scope.launch {
            _state.value = _state.value.copy(isSaving = true, error = null)
            try {
                val config = when (_state.value.selectedMode) {
                    AiMode.CLOUD -> AiConfig(
                        mode = AiMode.CLOUD,
                        cloudConfig = CloudAiConfig(
                            provider = _state.value.selectedProvider,
                            apiKey = _state.value.apiKey,
                            model = _state.value.selectedProvider.defaultModel
                        )
                    )
                    AiMode.LOCAL -> {
                        val selectedModel = _state.value.selectedModelId?.let { AvailableModels.getById(it) }
                        AiConfig(
                            mode = AiMode.LOCAL,
                            localConfig = LocalAiConfig(
                                modelPath = _state.value.localModelPath,
                                modelName = selectedModel?.fileName ?: "unknown"
                            )
                        )
                    }
                }
                manageSettings.saveConfig(config)
                if (_state.value.selectedMode == AiMode.CLOUD && _state.value.apiKey.isNotBlank()) {
                    manageSettings.saveApiKey(_state.value.selectedProvider.name, _state.value.apiKey)
                }
                _state.value = _state.value.copy(isSaving = false, isComplete = true)
            } catch (e: Exception) {
                _state.value = _state.value.copy(isSaving = false, error = e.message)
            }
        }
    }

    fun onCleared() {
        downloadJobs.values.forEach { it.cancel() }
        modelDownloader.close()
        scope.cancel()
    }
}
