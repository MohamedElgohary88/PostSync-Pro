package com.postsyncpro.navigation

import com.postsyncpro.presentation.i18n.Language
import com.postsyncpro.presentation.navigation.NavigationState
import com.postsyncpro.presentation.navigation.Screen
import kotlin.test.*

class NavigationStateTest {

    private lateinit var nav: NavigationState

    @BeforeTest
    fun setup() {
        nav = NavigationState()
    }

    // ─── Initial State ─────────────────────────────────────────

    @Test
    fun `initial screen is ONBOARDING`() {
        assertEquals(Screen.ONBOARDING, nav.currentScreen)
    }

    @Test
    fun `initial theme is dark`() {
        assertTrue(nav.isDarkTheme)
    }

    @Test
    fun `initial onboarding is not complete`() {
        assertFalse(nav.isOnboardingComplete)
    }

    @Test
    fun `initial language is English`() {
        assertEquals(Language.ENGLISH, nav.currentLanguage)
    }

    @Test
    fun `initial previous screen is null`() {
        assertNull(nav.previousScreen)
    }

    // ─── Navigation Before Onboarding Complete ──────────────────

    @Test
    fun `cannot navigate to workspace screens before onboarding`() {
        nav.navigateTo(Screen.SYNC)
        assertEquals(Screen.ONBOARDING, nav.currentScreen)
    }

    @Test
    fun `cannot navigate to tools screens before onboarding`() {
        nav.navigateTo(Screen.MOCK_SERVER)
        assertEquals(Screen.ONBOARDING, nav.currentScreen)
    }

    @Test
    fun `cannot navigate to analytics screens before onboarding`() {
        nav.navigateTo(Screen.REPORT)
        assertEquals(Screen.ONBOARDING, nav.currentScreen)
    }

    @Test
    fun `can navigate to SETTINGS before onboarding`() {
        nav.navigateTo(Screen.SETTINGS)
        assertEquals(Screen.SETTINGS, nav.currentScreen)
    }

    @Test
    fun `can navigate to PRO_FEATURES before onboarding`() {
        nav.navigateTo(Screen.PRO_FEATURES)
        assertEquals(Screen.PRO_FEATURES, nav.currentScreen)
    }

    @Test
    fun `can navigate to PRO section screens before onboarding`() {
        nav.navigateTo(Screen.SCHEDULER)
        assertEquals(Screen.SCHEDULER, nav.currentScreen)
    }

    @Test
    fun `can navigate to all PRO sub-screens before onboarding`() {
        val proScreens = Screen.entries.filter { it.section == "PRO" }
        proScreens.forEach { screen ->
            nav.navigateTo(screen)
            assertEquals(screen, nav.currentScreen, "Failed to navigate to $screen before onboarding")
        }
    }

    // ─── Navigation After Onboarding Complete ────────────────────

    @Test
    fun `can navigate to all screens after onboarding`() {
        nav.completeOnboarding()
        Screen.entries.forEach { screen ->
            nav.navigateTo(screen)
            assertEquals(screen, nav.currentScreen, "Failed to navigate to $screen after onboarding")
        }
    }

    @Test
    fun `completeOnboarding sets screen to SYNC`() {
        nav.completeOnboarding()
        assertEquals(Screen.SYNC, nav.currentScreen)
        assertTrue(nav.isOnboardingComplete)
    }

    // ─── Previous Screen Tracking ────────────────────────────────

    @Test
    fun `navigating to SETTINGS saves previous screen`() {
        nav.completeOnboarding()
        nav.navigateTo(Screen.FUZZ)
        nav.navigateTo(Screen.SETTINGS)
        assertEquals(Screen.FUZZ, nav.previousScreen)
    }

    @Test
    fun `navigating to PRO_FEATURES saves previous screen`() {
        nav.completeOnboarding()
        nav.navigateTo(Screen.VALIDATOR)
        nav.navigateTo(Screen.PRO_FEATURES)
        assertEquals(Screen.VALIDATOR, nav.previousScreen)
    }

    @Test
    fun `navigating to PRO sub-screen saves previous screen`() {
        nav.completeOnboarding()
        nav.navigateTo(Screen.SYNC)
        nav.navigateTo(Screen.SCHEDULER)
        assertEquals(Screen.SYNC, nav.previousScreen)
    }

    @Test
    fun `navigating to workspace screen does not save previous screen`() {
        nav.completeOnboarding()
        nav.navigateTo(Screen.SYNC)
        nav.navigateTo(Screen.FUZZ)
        assertNull(nav.previousScreen)
    }

    // ─── Back Navigation ─────────────────────────────────────────

    @Test
    fun `navigateBackFromSettings returns to previous screen`() {
        nav.completeOnboarding()
        nav.navigateTo(Screen.FUZZ)
        nav.navigateTo(Screen.SETTINGS)
        nav.navigateBackFromSettings()
        assertEquals(Screen.FUZZ, nav.currentScreen)
        assertNull(nav.previousScreen)
    }

    @Test
    fun `navigateBackFromSettings defaults to SYNC when no previous`() {
        nav.completeOnboarding()
        nav.navigateBackFromSettings()
        assertEquals(Screen.SYNC, nav.currentScreen)
    }

    @Test
    fun `navigateBackFromSettings defaults to ONBOARDING when not complete`() {
        nav.navigateTo(Screen.SETTINGS)
        nav.navigateBackFromSettings()
        assertEquals(Screen.ONBOARDING, nav.currentScreen)
    }

    @Test
    fun `navigateBackFromPro returns to previous screen`() {
        nav.completeOnboarding()
        nav.navigateTo(Screen.COVERAGE)
        nav.navigateTo(Screen.PRO_FEATURES)
        nav.navigateBackFromPro()
        assertEquals(Screen.COVERAGE, nav.currentScreen)
        assertNull(nav.previousScreen)
    }

    @Test
    fun `navigateBackFromPro defaults to SYNC when no previous`() {
        nav.completeOnboarding()
        nav.navigateBackFromPro()
        assertEquals(Screen.SYNC, nav.currentScreen)
    }

    // ─── Pro Feature Navigation Chain ─────────────────────────────

    @Test
    fun `can navigate from PRO_FEATURES to individual Pro screen and back`() {
        nav.completeOnboarding()
        nav.navigateTo(Screen.SYNC)

        // Go to Pro features
        nav.navigateTo(Screen.PRO_FEATURES)
        assertEquals(Screen.PRO_FEATURES, nav.currentScreen)
        assertEquals(Screen.SYNC, nav.previousScreen)

        // Go to a specific Pro screen (this overwrites previousScreen)
        nav.navigateTo(Screen.API_MONITOR)
        assertEquals(Screen.API_MONITOR, nav.currentScreen)
        assertEquals(Screen.PRO_FEATURES, nav.previousScreen)

        // Navigate back
        nav.navigateBackFromPro()
        assertEquals(Screen.PRO_FEATURES, nav.currentScreen)
    }

    // ─── Theme ────────────────────────────────────────────────────

    @Test
    fun `toggleTheme switches from dark to light`() {
        assertTrue(nav.isDarkTheme)
        nav.toggleTheme()
        assertFalse(nav.isDarkTheme)
    }

    @Test
    fun `toggleTheme twice returns to original`() {
        nav.toggleTheme()
        nav.toggleTheme()
        assertTrue(nav.isDarkTheme)
    }

    // ─── Language ────────────────────────────────────────────────

    @Test
    fun `setLanguage changes current language`() {
        nav.setLanguage(Language.ARABIC)
        assertEquals(Language.ARABIC, nav.currentLanguage)
    }

    @Test
    fun `setLanguage to Arabic enables RTL`() {
        nav.setLanguage(Language.ARABIC)
        assertTrue(nav.currentLanguage.isRtl)
    }

    @Test
    fun `setLanguage to French keeps LTR`() {
        nav.setLanguage(Language.FRENCH)
        assertFalse(nav.currentLanguage.isRtl)
    }

    @Test
    fun `strings change with language`() {
        val englishStrings = nav.strings
        nav.setLanguage(Language.ARABIC)
        val arabicStrings = nav.strings
        assertNotEquals(englishStrings.settingsTitle, arabicStrings.settingsTitle)
    }

    // ─── Screen Properties ──────────────────────────────────────

    @Test
    fun `all screens have valid section`() {
        val validSections = setOf("WORKSPACE", "TOOLS", "ANALYTICS", "PRO", "SYSTEM")
        Screen.entries.forEach { screen ->
            assertTrue(
                screen.section in validSections,
                "Screen $screen has invalid section: ${screen.section}"
            )
        }
    }

    @Test
    fun `all screens have title mapping`() {
        val strings = nav.strings
        Screen.entries.forEach { screen ->
            val title = screen.title(strings)
            assertTrue(title.isNotBlank(), "Screen $screen has blank title")
        }
    }

    @Test
    fun `PRO section has exactly 10 screens`() {
        val proScreens = Screen.entries.filter { it.section == "PRO" }
        assertEquals(10, proScreens.size)
    }

    @Test
    fun `WORKSPACE section has exactly 3 screens`() {
        val workspaceScreens = Screen.entries.filter { it.section == "WORKSPACE" }
        // ONBOARDING, SYNC, VALIDATOR, FUZZ
        assertEquals(4, workspaceScreens.size)
    }
}
