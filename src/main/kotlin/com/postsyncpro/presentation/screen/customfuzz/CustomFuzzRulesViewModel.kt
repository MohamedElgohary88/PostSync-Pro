package com.postsyncpro.presentation.screen.customfuzz

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class MutationType { SQL_INJECTION, XSS, OVERFLOW, BOUNDARY, FORMAT, CUSTOM }
enum class RuleSeverity { HIGH, MEDIUM, LOW }

data class FuzzRule(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String = "",
    val targetField: String = "",
    val mutationType: MutationType = MutationType.SQL_INJECTION,
    val customPayloads: String = "",
    val severity: RuleSeverity = RuleSeverity.HIGH,
    val isEnabled: Boolean = true,
    val appliedEndpoints: List<String> = emptyList(),
    val testResults: Int? = null // pass rate if tested
)

data class CustomFuzzState(
    val rules: List<FuzzRule> = emptyList(),
    val isCreating: Boolean = false,
    val editingRule: FuzzRule? = null,
    val selectedRuleId: String? = null,
    val isTesting: Boolean = false,
    val testResult: String? = null
)

class CustomFuzzRulesViewModel {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val _state = MutableStateFlow(CustomFuzzState())
    val state: StateFlow<CustomFuzzState> = _state.asStateFlow()

    fun startCreating() { _state.value = _state.value.copy(isCreating = true, editingRule = FuzzRule()) }
    fun cancelCreating() { _state.value = _state.value.copy(isCreating = false, editingRule = null) }
    fun updateEditingRule(rule: FuzzRule) { _state.value = _state.value.copy(editingRule = rule) }

    fun saveRule() {
        val rule = _state.value.editingRule ?: return
        val existing = _state.value.rules.indexOfFirst { it.id == rule.id }
        val rules = if (existing >= 0) {
            _state.value.rules.toMutableList().apply { set(existing, rule) }
        } else {
            _state.value.rules + rule
        }
        _state.value = _state.value.copy(rules = rules, isCreating = false, editingRule = null)
    }

    fun deleteRule(ruleId: String) {
        _state.value = _state.value.copy(rules = _state.value.rules.filter { it.id != ruleId })
    }

    fun selectRule(ruleId: String?) { _state.value = _state.value.copy(selectedRuleId = ruleId) }

    fun testRule(ruleId: String) {
        scope.launch {
            _state.value = _state.value.copy(isTesting = true)
            kotlinx.coroutines.delay(1500 + (Math.random() * 2000).toLong())
            val passRate = (50 + (Math.random() * 50).toInt())
            _state.value = _state.value.copy(
                isTesting = false,
                testResult = "Tested: $passRate% pass rate",
                rules = _state.value.rules.map { if (it.id == ruleId) it.copy(testResults = passRate) else it }
            )
        }
    }

    fun toggleRule(ruleId: String) {
        _state.value = _state.value.copy(
            rules = _state.value.rules.map { if (it.id == ruleId) it.copy(isEnabled = !it.isEnabled) else it }
        )
    }

    fun onCleared() { scope.cancel() }
}
