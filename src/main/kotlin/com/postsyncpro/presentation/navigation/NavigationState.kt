package com.postsyncpro.presentation.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.postsyncpro.presentation.i18n.AppStrings
import com.postsyncpro.presentation.i18n.Language
import com.postsyncpro.presentation.i18n.stringsFor

enum class Screen(val icon: String, val section: String = "WORKSPACE") {
    ONBOARDING("settings"),
    SYNC("sync"),
    VALIDATOR("verified"),
    FUZZ("bug_report"),
    REPORT("description", "ANALYTICS"),
    MOCK_SERVER("dns", "TOOLS"),
    TEST_CHAIN("account_tree", "TOOLS"),
    COVERAGE("grid_on", "ANALYTICS"),
    BREAKING_CHANGE("compare_arrows", "ANALYTICS"),
    REGRESSION("timeline", "ANALYTICS"),
    DEPENDENCY_GRAPH("hub", "ANALYTICS"),
    AUTO_FLOW("rocket_launch", "TOOLS"),
    // Pro Features
    PRO_FEATURES("star", "SYSTEM"),
    SCHEDULER("schedule", "PRO"),
    CUSTOM_FUZZ_RULES("tune", "PRO"),
    TEST_HISTORY("history", "PRO"),
    BRANDED_REPORT("branding_watermark", "PRO"),
    NOTIFICATIONS("notifications", "PRO"),
    ISSUE_TRACKER("bug_report", "PRO"),
    API_MONITOR("monitor_heart", "PRO"),
    MULTI_ENV("cloud_sync", "PRO"),
    CICD_EXPORT("terminal", "PRO"),
    SMART_TESTS("psychology", "PRO"),
    SETTINGS("settings", "SYSTEM");

    fun title(strings: AppStrings): String = when (this) {
        ONBOARDING -> strings.screenSetup
        SYNC -> strings.screenPostmanSync
        VALIDATOR -> strings.screenSrsValidator
        FUZZ -> strings.screenFuzzTesting
        REPORT -> strings.screenQaReport
        MOCK_SERVER -> strings.screenMockServer
        TEST_CHAIN -> strings.screenTestChain
        COVERAGE -> strings.screenCoverageMap
        BREAKING_CHANGE -> strings.screenChangeDetector
        REGRESSION -> strings.screenRegression
        DEPENDENCY_GRAPH -> strings.screenDependencies
        AUTO_FLOW -> strings.screenAutoFlow
        PRO_FEATURES -> strings.screenProFeatures
        SCHEDULER -> strings.screenScheduler
        CUSTOM_FUZZ_RULES -> strings.screenCustomFuzzRules
        TEST_HISTORY -> strings.screenTestHistory
        BRANDED_REPORT -> strings.screenBrandedReport
        NOTIFICATIONS -> strings.screenNotifications
        ISSUE_TRACKER -> strings.screenIssueTracker
        API_MONITOR -> strings.screenApiMonitor
        MULTI_ENV -> strings.screenMultiEnv
        CICD_EXPORT -> strings.screenCiCdExport
        SMART_TESTS -> strings.screenSmartTests
        SETTINGS -> strings.screenSettings
    }

    fun sectionTitle(strings: AppStrings): String = when (section) {
        "WORKSPACE" -> strings.sectionWorkspace
        "TOOLS" -> strings.sectionTools
        "ANALYTICS" -> strings.sectionAnalytics
        "PRO" -> strings.sectionPro
        else -> section
    }
}

class NavigationState {
    var currentScreen by mutableStateOf(Screen.ONBOARDING)
        private set

    var isDarkTheme by mutableStateOf(true)
        private set

    var isOnboardingComplete by mutableStateOf(false)
        private set

    var currentLanguage by mutableStateOf(Language.ENGLISH)
        private set

    val strings: AppStrings get() = stringsFor(currentLanguage)

    var previousScreen by mutableStateOf<Screen?>(null)
        private set

    fun navigateTo(screen: Screen) {
        if (isOnboardingComplete || screen == Screen.ONBOARDING || screen == Screen.SETTINGS || screen == Screen.PRO_FEATURES || screen.section == "PRO") {
            if (screen == Screen.SETTINGS || screen == Screen.PRO_FEATURES || screen.section == "PRO") {
                previousScreen = currentScreen
            }
            currentScreen = screen
        }
    }

    fun navigateBackFromSettings() {
        currentScreen = previousScreen ?: if (isOnboardingComplete) Screen.SYNC else Screen.ONBOARDING
        previousScreen = null
    }

    fun navigateBackFromPro() {
        currentScreen = previousScreen ?: if (isOnboardingComplete) Screen.SYNC else Screen.ONBOARDING
        previousScreen = null
    }

    fun completeOnboarding() {
        isOnboardingComplete = true
        currentScreen = Screen.SYNC
    }

    fun toggleTheme() {
        isDarkTheme = !isDarkTheme
    }

    fun setLanguage(language: Language) {
        currentLanguage = language
    }

    fun resetToOnboarding() {
        isOnboardingComplete = false
        currentScreen = Screen.ONBOARDING
        previousScreen = null
    }
}
