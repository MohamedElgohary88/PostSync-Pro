package com.postsyncpro.presentation.screen.issuetracker

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class IssueProvider { JIRA, GITHUB, LINEAR }

data class IssueTrackerConfig(
    val provider: IssueProvider = IssueProvider.JIRA,
    val projectUrl: String = "",
    val apiToken: String = "",
    val projectKey: String = "",
    val isConnected: Boolean = false,
    val autoCreateOnFailure: Boolean = false,
    val includeRequest: Boolean = true,
    val includeResponse: Boolean = true,
    val includeScreenshot: Boolean = false
)

data class CreatedIssue(
    val id: String = java.util.UUID.randomUUID().toString(),
    val title: String,
    val issueKey: String,
    val provider: IssueProvider,
    val url: String,
    val createdAt: String,
    val status: String = "Open"
)

data class IssueTrackerState(
    val config: IssueTrackerConfig = IssueTrackerConfig(),
    val isConnecting: Boolean = false,
    val recentIssues: List<CreatedIssue> = emptyList(),
    val connectionMessage: String? = null
)

class IssueTrackerViewModel {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val _state = MutableStateFlow(IssueTrackerState())
    val state: StateFlow<IssueTrackerState> = _state.asStateFlow()

    fun updateConfig(config: IssueTrackerConfig) {
        _state.value = _state.value.copy(config = config, connectionMessage = null)
    }

    fun connect() {
        scope.launch {
            _state.value = _state.value.copy(isConnecting = true, connectionMessage = null)
            kotlinx.coroutines.delay(1500)
            val connected = _state.value.config.apiToken.isNotBlank() && _state.value.config.projectUrl.isNotBlank()
            _state.value = _state.value.copy(
                isConnecting = false,
                config = _state.value.config.copy(isConnected = connected),
                connectionMessage = if (connected) "Connected successfully" else "Connection failed - check credentials"
            )
        }
    }

    fun disconnect() {
        _state.value = _state.value.copy(
            config = _state.value.config.copy(isConnected = false),
            connectionMessage = null
        )
    }

    fun createBug(testName: String, errorDetails: String) {
        scope.launch {
            val provider = _state.value.config.provider
            val key = _state.value.config.projectKey.ifBlank { "QA" }
            val num = (100 + _state.value.recentIssues.size)
            val issue = CreatedIssue(
                title = "Failed: $testName",
                issueKey = "$key-$num",
                provider = provider,
                url = "${_state.value.config.projectUrl}/issue/$key-$num",
                createdAt = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
            )
            _state.value = _state.value.copy(recentIssues = listOf(issue) + _state.value.recentIssues)
        }
    }

    fun onCleared() { scope.cancel() }
}
