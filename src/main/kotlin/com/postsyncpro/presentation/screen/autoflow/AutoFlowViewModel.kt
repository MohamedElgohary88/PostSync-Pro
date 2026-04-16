package com.postsyncpro.presentation.screen.autoflow

import com.postsyncpro.data.parser.SwaggerParser
import com.postsyncpro.domain.model.*
import com.postsyncpro.domain.repository.TestChainRepository
import com.postsyncpro.domain.usecase.AutoFlowUseCase
import com.postsyncpro.domain.usecase.MergeCollectionsUseCase
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.*
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class AutoFlowViewModel(
    private val autoFlowUseCase: AutoFlowUseCase,
    private val mergeUseCase: MergeCollectionsUseCase,
    private val chainRepository: TestChainRepository
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val _state = MutableStateFlow(AutoFlowState())
    val state: StateFlow<AutoFlowState> = _state.asStateFlow()

    private var swaggerSpec: SwaggerSpec? = null
    private var postmanCollection: PostmanCollection? = null

    fun loadFile(file: File) {
        scope.launch {
            try {
                _state.value = _state.value.copy(error = null, isPlanning = true)
                val jsonContent = withContext(Dispatchers.IO) { file.readText() }

                // Auto-detect: Swagger has "openapi" or "swagger" key, Postman has "info.schema" with postman URL
                val isPostman = jsonContent.contains("schema.getpostman.com") ||
                        (jsonContent.contains("\"item\"") && jsonContent.contains("\"info\"") && !jsonContent.contains("\"openapi\"") && !jsonContent.contains("\"swagger\""))

                if (isPostman) {
                    loadPostmanCollection(jsonContent)
                } else {
                    loadSwaggerSpec(jsonContent)
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    error = "Failed to load file: ${e.message}",
                    isPlanning = false
                )
            }
        }
    }

    private suspend fun loadSwaggerSpec(jsonContent: String) {
        val spec = mergeUseCase.parseSwaggerSpec(jsonContent)
        swaggerSpec = spec
        postmanCollection = null

        val baseUrl = spec.servers.firstOrNull()?.url ?: "http://localhost:8080"
        val plan = autoFlowUseCase.buildFlowPlan(spec)

        _state.value = _state.value.copy(
            config = _state.value.config.copy(
                baseUrl = baseUrl,
                swaggerSpec = spec,
                postmanCollection = null,
                sourceType = FlowSourceType.SWAGGER,
                sourceName = spec.info.title.ifBlank { "Swagger API" },
                sourceVersion = spec.info.version
            ),
            plan = plan,
            totalSteps = plan.totalSteps,
            isPlanning = false,
            stepResults = emptyList(),
            variables = emptyMap(),
            currentStepIndex = -1,
            exportedFilePath = null
        )
    }

    private suspend fun loadPostmanCollection(jsonContent: String) {
        val collection = mergeUseCase.parsePostmanCollection(jsonContent)
        postmanCollection = collection
        swaggerSpec = null

        // Try to extract base URL from collection variables or first request
        val baseUrl = extractBaseUrlFromCollection(collection)
        val plan = autoFlowUseCase.buildFlowPlanFromPostman(collection)

        _state.value = _state.value.copy(
            config = _state.value.config.copy(
                baseUrl = baseUrl,
                swaggerSpec = null,
                postmanCollection = collection,
                sourceType = FlowSourceType.POSTMAN,
                sourceName = collection.info.name,
                sourceVersion = ""
            ),
            plan = plan,
            totalSteps = plan.totalSteps,
            isPlanning = false,
            stepResults = emptyList(),
            variables = emptyMap(),
            currentStepIndex = -1,
            exportedFilePath = null
        )
    }

    private fun extractBaseUrlFromCollection(collection: PostmanCollection): String {
        // Check collection variables for baseUrl / base_url
        val baseUrlVar = collection.variable.find {
            it.key.lowercase() in listOf("baseurl", "base_url", "base-url", "url", "host")
        }
        if (baseUrlVar != null && baseUrlVar.value.isNotBlank()) return baseUrlVar.value

        // Fallback: extract from first request's raw URL
        val firstItem = flattenItems(collection.item).firstOrNull { it.request?.url != null }
        val rawUrl = firstItem?.request?.url?.raw ?: return "http://localhost:8080"
        return try {
            val url = rawUrl.replace(Regex("\\{\\{.*?}}"), "localhost")
            val parts = url.split("/")
            if (parts.size >= 3) parts.take(3).joinToString("/") else "http://localhost:8080"
        } catch (e: Exception) { "http://localhost:8080" }
    }

    private fun flattenItems(items: List<PostmanItem>): List<PostmanItem> {
        val result = mutableListOf<PostmanItem>()
        for (item in items) {
            if (item.request != null) result.add(item)
            if (item.item.isNotEmpty()) result.addAll(flattenItems(item.item))
        }
        return result
    }

    fun updateBaseUrl(url: String) {
        _state.value = _state.value.copy(
            config = _state.value.config.copy(baseUrl = url)
        )
    }

    fun runFullFlow() {
        val plan = _state.value.plan ?: return
        val baseUrl = _state.value.config.baseUrl.ifBlank { return }

        scope.launch {
            _state.value = _state.value.copy(
                isRunning = true,
                isPaused = false,
                currentStepIndex = 0,
                stepResults = emptyList(),
                variables = emptyMap(),
                error = null,
                exportedFilePath = null
            )

            val allSteps = plan.phases.flatMap { it.steps }
            val variables = mutableMapOf<String, String>()
            val results = mutableListOf<FlowStepResult>()

            for ((index, step) in allSteps.withIndex()) {
                if (_state.value.isPaused) {
                    _state.value = _state.value.copy(isRunning = false)
                    return@launch
                }

                _state.value = _state.value.copy(currentStepIndex = index)

                val result = executeStep(step, baseUrl, variables)
                results.add(result)

                // Accumulate extracted variables
                variables.putAll(result.extractedValues)

                _state.value = _state.value.copy(
                    stepResults = results.toList(),
                    variables = variables.toMap()
                )

                // Small delay between requests
                delay(300)
            }

            _state.value = _state.value.copy(isRunning = false)
        }
    }

    fun pauseFlow() {
        _state.value = _state.value.copy(isPaused = true)
    }

    fun resumeFlow() {
        val plan = _state.value.plan ?: return
        val baseUrl = _state.value.config.baseUrl.ifBlank { return }
        val allSteps = plan.phases.flatMap { it.steps }
        val startIndex = _state.value.stepResults.size

        if (startIndex >= allSteps.size) return

        scope.launch {
            _state.value = _state.value.copy(isRunning = true, isPaused = false)

            val variables = _state.value.variables.toMutableMap()
            val results = _state.value.stepResults.toMutableList()

            for (index in startIndex until allSteps.size) {
                if (_state.value.isPaused) {
                    _state.value = _state.value.copy(isRunning = false)
                    return@launch
                }

                val step = allSteps[index]
                _state.value = _state.value.copy(currentStepIndex = index)

                val result = executeStep(step, baseUrl, variables)
                results.add(result)
                variables.putAll(result.extractedValues)

                _state.value = _state.value.copy(
                    stepResults = results.toList(),
                    variables = variables.toMap()
                )

                delay(300)
            }

            _state.value = _state.value.copy(isRunning = false)
        }
    }

    fun resetFlow() {
        _state.value = _state.value.copy(
            isRunning = false,
            isPaused = false,
            currentStepIndex = -1,
            stepResults = emptyList(),
            variables = emptyMap(),
            error = null,
            exportedFilePath = null
        )
    }

    fun exportResults(outputDir: File) {
        val currentState = _state.value
        if (currentState.stepResults.isEmpty()) return

        scope.launch {
            _state.value = _state.value.copy(isExporting = true)
            try {
                val export = buildExport(currentState)
                val jsonContent = buildExportJson(export)

                val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
                val fileName = "auto_flow_results_$timestamp.json"
                val outputFile = File(outputDir, fileName)

                withContext(Dispatchers.IO) {
                    outputFile.parentFile?.mkdirs()
                    outputFile.writeText(jsonContent)
                }

                _state.value = _state.value.copy(
                    isExporting = false,
                    exportedFilePath = outputFile.absolutePath
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isExporting = false,
                    error = "Export failed: ${e.message}"
                )
            }
        }
    }

    fun onCleared() {
        scope.cancel()
    }

    // ─── Private execution ──────────────────────────────────

    private suspend fun executeStep(
        step: FlowStep,
        baseUrl: String,
        variables: Map<String, String>
    ): FlowStepResult {
        val startTime = System.currentTimeMillis()

        try {
            // Resolve path parameters from variables
            var resolvedPath = step.path
            val pathParamRegex = Regex("\\{(\\w+)}")
            val pathParams = pathParamRegex.findAll(step.path).map { it.groupValues[1] }.toList()

            for (param in pathParams) {
                // Try to find matching variable: resource_id, id, etc.
                val resource = extractResourceFromPath(step.path)
                val value = variables["${resource}_id"]
                    ?: variables[param]
                    ?: variables["${param}_id"]
                    ?: "1"
                resolvedPath = resolvedPath.replace("{$param}", value)
            }

            // Resolve body template variables
            var resolvedBody = step.bodyTemplate
            variables.forEach { (key, value) ->
                resolvedBody = resolvedBody.replace("\${$key}", value)
            }

            // Build headers
            val headers = mutableMapOf<String, String>()
            if (step.requiresAuth) {
                val token = variables["access_token"] ?: variables["token"] ?: ""
                if (token.isNotBlank()) {
                    headers["Authorization"] = "Bearer $token"
                }
            }
            if (resolvedBody.isNotBlank() && step.method in listOf("POST", "PUT", "PATCH")) {
                headers["Content-Type"] = "application/json"
            }

            // Execute via chain repository
            val node = TestChainNode(
                id = step.id,
                endpoint = "${step.method} ${step.path}",
                method = step.method,
                path = resolvedPath,
                label = step.label,
                headers = headers,
                bodyTemplate = resolvedBody,
                extractors = step.extractors.map { ResponseExtractor(it.variableName, it.jsonPath) }
            )

            val result = chainRepository.executeNode(node, baseUrl, emptyMap())

            return FlowStepResult(
                step = step,
                httpStatus = result.httpStatus,
                requestHeaders = headers,
                requestBody = resolvedBody,
                responseBody = result.responseBody,
                responseTimeMs = result.responseTimeMs,
                passed = result.passed,
                extractedValues = result.extractedValues,
                error = result.error
            )
        } catch (e: Exception) {
            return FlowStepResult(
                step = step,
                httpStatus = 0,
                requestHeaders = emptyMap(),
                requestBody = step.bodyTemplate,
                responseBody = "",
                responseTimeMs = System.currentTimeMillis() - startTime,
                passed = false,
                error = e.message ?: "Unknown error"
            )
        }
    }

    private fun extractResourceFromPath(path: String): String {
        val segments = path.split("/").filter { it.isNotBlank() && !it.startsWith("{") }
        val meaningful = segments.filter { it.lowercase() !in listOf("api", "v1", "v2", "v3", "auth") }
        return meaningful.lastOrNull()?.lowercase() ?: "item"
    }

    private fun buildExport(state: AutoFlowState): AutoFlowExport {
        return AutoFlowExport(
            apiTitle = state.config.sourceName,
            apiVersion = state.config.sourceVersion,
            baseUrl = state.config.baseUrl,
            executedAt = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
            totalEndpoints = state.stepResults.size,
            passed = state.passedSteps,
            failed = state.failedSteps,
            passRate = "${(state.passRate * 100).toInt()}%",
            avgResponseTimeMs = state.avgResponseTime,
            endpoints = state.stepResults.mapIndexed { index, result ->
                ExportEndpointDetail(
                    order = index + 1,
                    method = result.step.method,
                    path = result.step.path,
                    category = result.step.category.displayName,
                    httpStatus = result.httpStatus,
                    passed = result.passed,
                    responseTimeMs = result.responseTimeMs,
                    requestHeaders = result.requestHeaders,
                    requestBody = result.requestBody.ifBlank { null },
                    responseBody = result.responseBody.ifBlank { null },
                    extractedVariables = result.extractedValues
                )
            }
        )
    }

    private fun buildExportJson(export: AutoFlowExport): String {
        val sb = StringBuilder()
        sb.appendLine("{")
        sb.appendLine("  \"api_title\": \"${escJson(export.apiTitle)}\",")
        sb.appendLine("  \"api_version\": \"${escJson(export.apiVersion)}\",")
        sb.appendLine("  \"base_url\": \"${escJson(export.baseUrl)}\",")
        sb.appendLine("  \"executed_at\": \"${escJson(export.executedAt)}\",")
        sb.appendLine("  \"summary\": {")
        sb.appendLine("    \"total_endpoints\": ${export.totalEndpoints},")
        sb.appendLine("    \"passed\": ${export.passed},")
        sb.appendLine("    \"failed\": ${export.failed},")
        sb.appendLine("    \"pass_rate\": \"${export.passRate}\",")
        sb.appendLine("    \"avg_response_time_ms\": ${export.avgResponseTimeMs}")
        sb.appendLine("  },")
        sb.appendLine("  \"endpoints\": [")

        export.endpoints.forEachIndexed { index, ep ->
            sb.appendLine("    {")
            sb.appendLine("      \"order\": ${ep.order},")
            sb.appendLine("      \"method\": \"${ep.method}\",")
            sb.appendLine("      \"path\": \"${escJson(ep.path)}\",")
            sb.appendLine("      \"category\": \"${ep.category}\",")
            sb.appendLine("      \"http_status\": ${ep.httpStatus},")
            sb.appendLine("      \"passed\": ${ep.passed},")
            sb.appendLine("      \"response_time_ms\": ${ep.responseTimeMs},")

            // Request headers
            sb.appendLine("      \"request_headers\": {")
            ep.requestHeaders.entries.forEachIndexed { hIdx, (k, v) ->
                val comma = if (hIdx < ep.requestHeaders.size - 1) "," else ""
                sb.appendLine("        \"${escJson(k)}\": \"${escJson(v)}\"$comma")
            }
            sb.appendLine("      },")

            // Request body
            if (ep.requestBody != null) {
                sb.appendLine("      \"request_body\": ${ep.requestBody},")
            } else {
                sb.appendLine("      \"request_body\": null,")
            }

            // Response body (try to parse as JSON, fallback to string)
            if (ep.responseBody != null) {
                val isJson = ep.responseBody.trimStart().startsWith("{") || ep.responseBody.trimStart().startsWith("[")
                if (isJson) {
                    sb.appendLine("      \"response_body\": ${ep.responseBody},")
                } else {
                    sb.appendLine("      \"response_body\": \"${escJson(ep.responseBody)}\",")
                }
            } else {
                sb.appendLine("      \"response_body\": null,")
            }

            // Extracted variables
            sb.appendLine("      \"extracted_variables\": {")
            ep.extractedVariables.entries.forEachIndexed { vIdx, (k, v) ->
                val comma = if (vIdx < ep.extractedVariables.size - 1) "," else ""
                sb.appendLine("        \"${escJson(k)}\": \"${escJson(v)}\"$comma")
            }
            sb.appendLine("      }")

            val endComma = if (index < export.endpoints.size - 1) "," else ""
            sb.appendLine("    }$endComma")
        }

        sb.appendLine("  ]")
        sb.appendLine("}")
        return sb.toString()
    }

    private fun escJson(s: String): String = s
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")
}
