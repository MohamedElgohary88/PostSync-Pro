package com.postsyncpro.domain.model

data class AiConfig(
    val mode: AiMode,
    val cloudConfig: CloudAiConfig? = null,
    val localConfig: LocalAiConfig? = null
)

enum class AiMode(val displayName: String) {
    CLOUD("Cloud API (BYOK)"),
    LOCAL("Local Offline AI")
}

data class CloudAiConfig(
    val provider: CloudProvider,
    val apiKey: String,
    val model: String = ""
)

enum class CloudProvider(val displayName: String, val defaultModel: String, val baseUrl: String) {
    GEMINI("Google Gemini", "gemini-2.0-flash", "https://generativelanguage.googleapis.com/v1beta"),
    GROQ("Groq", "llama-3.3-70b-versatile", "https://api.groq.com/openai/v1")
}

data class LocalAiConfig(
    val modelPath: String,
    val modelName: String,
    val contextSize: Int = 2048,
    val threads: Int = 4
)

data class SystemProfile(
    val totalRamMb: Long,
    val availableRamMb: Long,
    val osName: String,
    val osArch: String,
    val cpuCores: Int,
    val recommendedMode: AiMode,
    val recommendation: String
)

// ─── Downloadable AI Models ─────────────────────────────────────
data class DownloadableModel(
    val id: String,
    val name: String,
    val description: String,
    val fileName: String,
    val downloadUrl: String,
    val sizeBytes: Long,
    val sizeDisplay: String,
    val minRamGb: Int,
    val quantization: String,
    val parameters: String,
    val contextLength: Int,
    val recommended: Boolean = false,
    /** Expected SHA-256 for the exact GGUF payload. */
    val sha256: String = ""
)

data class ModelDownloadProgress(
    val modelId: String,
    val bytesDownloaded: Long = 0,
    val totalBytes: Long = 0,
    val progress: Float = 0f,
    val status: DownloadStatus = DownloadStatus.IDLE,
    val error: String? = null
)

enum class DownloadStatus {
    IDLE, DOWNLOADING, PAUSED, COMPLETED, FAILED
}

object AvailableModels {
    private const val HF_BASE = "https://huggingface.co"

    val models = listOf(
        DownloadableModel(
            id = "phi3-mini",
            name = "Phi-3 Mini",
            description = "Microsoft's compact model. Best balance of speed and quality for QA tasks.",
            fileName = "Phi-3-mini-4k-instruct-q4.gguf",
            downloadUrl = "$HF_BASE/microsoft/Phi-3-mini-4k-instruct-gguf/resolve/main/Phi-3-mini-4k-instruct-q4.gguf",
            sizeBytes = 2_393_231_072L,
            sizeDisplay = "2.3 GB",
            minRamGb = 4,
            quantization = "Q4_K_M",
            parameters = "3.8B",
            contextLength = 4096,
            sha256 = "8a83c7fb9049a9b2e92266fa7ad04933bb53aa1e85136b7b30f1b8000ff2edef",
            recommended = true
        ),
        DownloadableModel(
            id = "llama32-1b",
            name = "Llama 3.2 1B",
            description = "Meta's smallest model. Ultra-fast inference, ideal for quick analysis.",
            fileName = "Llama-3.2-1B-Instruct-Q4_K_M.gguf",
            downloadUrl = "$HF_BASE/bartowski/Llama-3.2-1B-Instruct-GGUF/resolve/main/Llama-3.2-1B-Instruct-Q4_K_M.gguf",
            sizeBytes = 807_694_464L,
            sizeDisplay = "0.75 GB",
            minRamGb = 2,
            quantization = "Q4_K_M",
            parameters = "1B",
            contextLength = 8192,
            sha256 = "6f85a640a97cf2bf5b8e764087b1e83da0fdb51d7c9fab7d0fece9385611df83",
            recommended = false
        ),
        DownloadableModel(
            id = "llama32-3b",
            name = "Llama 3.2 3B",
            description = "Meta's balanced model. Great quality for SRS analysis and rule extraction.",
            fileName = "Llama-3.2-3B-Instruct-Q4_K_M.gguf",
            downloadUrl = "$HF_BASE/bartowski/Llama-3.2-3B-Instruct-GGUF/resolve/main/Llama-3.2-3B-Instruct-Q4_K_M.gguf",
            sizeBytes = 2_019_377_696L,
            sizeDisplay = "1.9 GB",
            minRamGb = 4,
            quantization = "Q4_K_M",
            parameters = "3B",
            contextLength = 8192,
            sha256 = "6c1a2b41161032677be168d354123594c0e6e67d2b9227c84f296ad037c728ff",
            recommended = false
        )
    )

    fun getById(id: String): DownloadableModel? = models.find { it.id == id }
}
