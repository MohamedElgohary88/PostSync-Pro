package com.postsyncpro.presentation.screen.brandedreport

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ReportTemplate { MINIMAL, DETAILED, EXECUTIVE }

data class BrandingConfig(
    val companyName: String = "",
    val logoPath: String? = null,
    val primaryColorHex: String = "#FF6B35",
    val footerText: String = "",
    val includeLogo: Boolean = true,
    val includeDate: Boolean = true,
    val includePageNumbers: Boolean = true,
    val template: ReportTemplate = ReportTemplate.DETAILED
)

data class BrandedReportState(
    val config: BrandingConfig = BrandingConfig(),
    val isSaved: Boolean = false,
    val showPreview: Boolean = false,
    val saveMessage: String? = null
)

class BrandedReportViewModel {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val _state = MutableStateFlow(BrandedReportState())
    val state: StateFlow<BrandedReportState> = _state.asStateFlow()

    fun updateConfig(config: BrandingConfig) {
        _state.value = _state.value.copy(config = config, isSaved = false, saveMessage = null)
    }

    fun saveConfig() {
        _state.value = _state.value.copy(isSaved = true, saveMessage = "Branding saved successfully")
    }

    fun resetConfig() {
        _state.value = BrandedReportState()
    }

    fun togglePreview() {
        _state.value = _state.value.copy(showPreview = !_state.value.showPreview)
    }

    fun setLogoPath(path: String) {
        _state.value = _state.value.copy(config = _state.value.config.copy(logoPath = path))
    }

    fun onCleared() { scope.cancel() }
}
