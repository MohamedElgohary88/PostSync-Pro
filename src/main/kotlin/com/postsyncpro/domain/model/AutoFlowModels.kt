package com.postsyncpro.domain.model

/**
 * Module 12: Auto Flow Runner — fully automatic end-to-end API test flow
 */

enum class FlowSourceType { SWAGGER, POSTMAN }

data class AutoFlowConfig(
    val baseUrl: String = "",
    val swaggerSpec: SwaggerSpec? = null,
    val postmanCollection: PostmanCollection? = null,
    val sourceType: FlowSourceType? = null,
    val sourceName: String = "",
    val sourceVersion: String = "",
    val customRegisterBody: String = "",
    val customLoginBody: String = ""
)

data class AutoFlowPlan(
    val phases: List<FlowPhase>,
    val totalSteps: Int
)

data class FlowPhase(
    val name: String,
    val description: String,
    val steps: List<FlowStep>
)

data class FlowStep(
    val id: String,
    val order: Int,
    val method: String,
    val path: String,
    val label: String,
    val category: FlowCategory,
    val requiresAuth: Boolean,
    val bodyTemplate: String = "",
    val pathParams: Map<String, String> = emptyMap(),
    val extractors: List<FlowExtractor> = emptyList()
)

data class FlowExtractor(
    val variableName: String,
    val jsonPath: String
)

enum class FlowCategory(val displayName: String) {
    AUTH_REGISTER("Register"),
    AUTH_LOGIN("Login"),
    CREATE("Create"),
    READ_ALL("List"),
    READ_ONE("Get"),
    UPDATE("Update"),
    DELETE("Delete"),
    OTHER("Other")
}

data class AutoFlowState(
    val config: AutoFlowConfig = AutoFlowConfig(),
    val plan: AutoFlowPlan? = null,
    val isPlanning: Boolean = false,
    val isRunning: Boolean = false,
    val isPaused: Boolean = false,
    val currentStepIndex: Int = -1,
    val totalSteps: Int = 0,
    val stepResults: List<FlowStepResult> = emptyList(),
    val variables: Map<String, String> = emptyMap(),
    val isExporting: Boolean = false,
    val exportedFilePath: String? = null,
    val error: String? = null
) {
    val isComplete: Boolean get() = stepResults.isNotEmpty() && currentStepIndex >= totalSteps - 1 && !isRunning
    val passedSteps: Int get() = stepResults.count { it.passed }
    val failedSteps: Int get() = stepResults.count { !it.passed }
    val passRate: Float get() = if (stepResults.isNotEmpty()) passedSteps.toFloat() / stepResults.size else 0f
    val avgResponseTime: Long get() = if (stepResults.isNotEmpty()) stepResults.map { it.responseTimeMs }.average().toLong() else 0L
    val currentStep: FlowStep? get() = plan?.phases?.flatMap { it.steps }?.getOrNull(currentStepIndex)
}

data class FlowStepResult(
    val step: FlowStep,
    val httpStatus: Int,
    val requestHeaders: Map<String, String>,
    val requestBody: String,
    val responseBody: String,
    val responseTimeMs: Long,
    val passed: Boolean,
    val extractedValues: Map<String, String> = emptyMap(),
    val error: String? = null
)

data class AutoFlowExport(
    val apiTitle: String,
    val apiVersion: String,
    val baseUrl: String,
    val executedAt: String,
    val totalEndpoints: Int,
    val passed: Int,
    val failed: Int,
    val passRate: String,
    val avgResponseTimeMs: Long,
    val endpoints: List<ExportEndpointDetail>
)

data class ExportEndpointDetail(
    val order: Int,
    val method: String,
    val path: String,
    val category: String,
    val httpStatus: Int,
    val passed: Boolean,
    val responseTimeMs: Long,
    val requestHeaders: Map<String, String>,
    val requestBody: String?,
    val responseBody: String?,
    val extractedVariables: Map<String, String>
)
