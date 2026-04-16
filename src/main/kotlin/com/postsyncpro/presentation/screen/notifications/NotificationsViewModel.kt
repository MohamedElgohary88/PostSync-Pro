package com.postsyncpro.presentation.screen.notifications

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class NotificationProvider { SLACK, TEAMS, DISCORD, EMAIL }

data class NotificationChannel(
    val id: String = java.util.UUID.randomUUID().toString(),
    val provider: NotificationProvider = NotificationProvider.SLACK,
    val webhookUrl: String = "",
    val emailAddress: String = "",
    val isEnabled: Boolean = true,
    val onRegression: Boolean = true,
    val onPassRateDrop: Boolean = true,
    val onScheduleComplete: Boolean = false,
    val onBreakingChange: Boolean = true,
    val threshold: Int = 80, // alert if pass rate drops below this
    val isConnected: Boolean = false
)

data class NotificationsState(
    val channels: List<NotificationChannel> = emptyList(),
    val isAdding: Boolean = false,
    val editingChannel: NotificationChannel? = null,
    val testingChannelId: String? = null,
    val testResult: String? = null
)

class NotificationsViewModel {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val _state = MutableStateFlow(NotificationsState())
    val state: StateFlow<NotificationsState> = _state.asStateFlow()

    fun startAdding() { _state.value = _state.value.copy(isAdding = true, editingChannel = NotificationChannel()) }
    fun cancelAdding() { _state.value = _state.value.copy(isAdding = false, editingChannel = null) }
    fun updateEditing(channel: NotificationChannel) { _state.value = _state.value.copy(editingChannel = channel) }

    fun saveChannel() {
        val channel = _state.value.editingChannel ?: return
        val existing = _state.value.channels.indexOfFirst { it.id == channel.id }
        val channels = if (existing >= 0) {
            _state.value.channels.toMutableList().apply { set(existing, channel) }
        } else {
            _state.value.channels + channel
        }
        _state.value = _state.value.copy(channels = channels, isAdding = false, editingChannel = null)
    }

    fun deleteChannel(id: String) {
        _state.value = _state.value.copy(channels = _state.value.channels.filter { it.id != id })
    }

    fun toggleChannel(id: String) {
        _state.value = _state.value.copy(
            channels = _state.value.channels.map { if (it.id == id) it.copy(isEnabled = !it.isEnabled) else it }
        )
    }

    fun testConnection(channelId: String) {
        scope.launch {
            _state.value = _state.value.copy(testingChannelId = channelId, testResult = null)
            kotlinx.coroutines.delay(1500)
            val success = Math.random() > 0.2
            _state.value = _state.value.copy(
                testingChannelId = null,
                testResult = if (success) "success" else "failed",
                channels = _state.value.channels.map {
                    if (it.id == channelId) it.copy(isConnected = success) else it
                }
            )
        }
    }

    fun onCleared() { scope.cancel() }
}
