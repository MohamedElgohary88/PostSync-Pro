package com.postsyncpro.presentation

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.postsyncpro.presentation.components.AppSidebar
import com.postsyncpro.presentation.components.CommandPalette
import com.postsyncpro.presentation.navigation.NavigationState
import com.postsyncpro.presentation.navigation.Screen
import com.postsyncpro.presentation.screen.onboarding.OnboardingScreen
import com.postsyncpro.presentation.screen.onboarding.OnboardingState
import com.postsyncpro.presentation.screen.onboarding.OnboardingViewModel
import com.postsyncpro.presentation.screen.sync.SyncScreen
import com.postsyncpro.presentation.screen.sync.SyncViewModel
import com.postsyncpro.presentation.screen.workbench.WorkbenchScreen
import com.postsyncpro.presentation.screen.workbench.WorkbenchViewModel
import com.postsyncpro.presentation.screen.validator.ValidatorScreen
import com.postsyncpro.presentation.screen.validator.ValidatorViewModel
import com.postsyncpro.presentation.screen.fuzz.FuzzScreen
import com.postsyncpro.presentation.screen.fuzz.FuzzViewModel
import com.postsyncpro.presentation.screen.report.ReportScreen
import com.postsyncpro.presentation.screen.report.ReportViewModel
import com.postsyncpro.presentation.screen.mockserver.MockServerScreen
import com.postsyncpro.presentation.screen.mockserver.MockServerViewModel
import com.postsyncpro.presentation.screen.testchain.TestChainScreen
import com.postsyncpro.presentation.screen.testchain.TestChainViewModel
import com.postsyncpro.presentation.screen.coverage.CoverageScreen
import com.postsyncpro.presentation.screen.coverage.CoverageViewModel
import com.postsyncpro.presentation.screen.breakingchange.BreakingChangeScreen
import com.postsyncpro.presentation.screen.breakingchange.BreakingChangeViewModel
import com.postsyncpro.presentation.screen.regression.RegressionScreen
import com.postsyncpro.presentation.screen.regression.RegressionViewModel
import com.postsyncpro.presentation.screen.dependencygraph.DependencyGraphScreen
import com.postsyncpro.presentation.screen.dependencygraph.DependencyGraphViewModel
import com.postsyncpro.presentation.screen.autoflow.AutoFlowScreen
import com.postsyncpro.presentation.screen.autoflow.AutoFlowViewModel
import com.postsyncpro.presentation.screen.settings.SettingsScreen
import com.postsyncpro.presentation.screen.pro.ProFeaturesScreen
import com.postsyncpro.presentation.screen.scheduler.SchedulerScreen
import com.postsyncpro.presentation.screen.scheduler.SchedulerViewModel
import com.postsyncpro.presentation.screen.customfuzz.CustomFuzzRulesScreen
import com.postsyncpro.presentation.screen.customfuzz.CustomFuzzRulesViewModel
import com.postsyncpro.presentation.screen.testhistory.TestHistoryScreen
import com.postsyncpro.presentation.screen.testhistory.TestHistoryViewModel
import com.postsyncpro.presentation.screen.brandedreport.BrandedReportScreen
import com.postsyncpro.presentation.screen.brandedreport.BrandedReportViewModel
import com.postsyncpro.presentation.screen.notifications.NotificationsScreen
import com.postsyncpro.presentation.screen.notifications.NotificationsViewModel
import com.postsyncpro.presentation.screen.issuetracker.IssueTrackerScreen
import com.postsyncpro.presentation.screen.issuetracker.IssueTrackerViewModel
import com.postsyncpro.presentation.screen.apimonitor.ApiMonitorScreen
import com.postsyncpro.presentation.screen.apimonitor.ApiMonitorViewModel
import com.postsyncpro.presentation.screen.multienv.MultiEnvScreen
import com.postsyncpro.presentation.screen.multienv.MultiEnvViewModel
import com.postsyncpro.presentation.screen.cicdexport.CiCdExportScreen
import com.postsyncpro.presentation.screen.cicdexport.CiCdExportViewModel
import com.postsyncpro.presentation.screen.smarttests.SmartTestsScreen
import com.postsyncpro.presentation.screen.smarttests.SmartTestsViewModel
import com.postsyncpro.domain.usecase.ManageSettingsUseCase
import com.postsyncpro.domain.model.AiMode
import com.postsyncpro.presentation.i18n.LocalAppStrings
import com.postsyncpro.presentation.theme.*
import org.koin.core.context.GlobalContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Composable
fun App(commandPaletteSignal: Int = 0) {
    val navigationState = remember { NavigationState() }
    var isCommandPaletteVisible by remember { mutableStateOf(false) }
    val appFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        appFocusRequester.requestFocus()
    }

    LaunchedEffect(commandPaletteSignal) {
        if (commandPaletteSignal > 0) isCommandPaletteVisible = true
    }

    val koin = GlobalContext.get()
    val onboardingViewModel = remember { koin.get<OnboardingViewModel>() }
    val syncViewModel = remember { koin.get<SyncViewModel>() }
    val workbenchViewModel = remember { koin.get<WorkbenchViewModel>() }

    // Track onboarding completion to re-create AI-dependent ViewModels
    val onboardingState by onboardingViewModel.state.collectAsState()
    var viewModelsKey by remember { mutableStateOf(0) }

    // Re-create ViewModels that depend on AI config after onboarding saves the API key
    val validatorViewModel = remember(viewModelsKey) { koin.get<ValidatorViewModel>() }
    val fuzzViewModel = remember(viewModelsKey) { koin.get<FuzzViewModel>() }

    // New module ViewModels
    val reportViewModel = remember(viewModelsKey) { koin.get<ReportViewModel>() }
    val mockServerViewModel = remember { koin.get<MockServerViewModel>() }
    val testChainViewModel = remember { koin.get<TestChainViewModel>() }
    val coverageViewModel = remember(viewModelsKey) { koin.get<CoverageViewModel>() }
    val breakingChangeViewModel = remember { koin.get<BreakingChangeViewModel>() }
    val regressionViewModel = remember { koin.get<RegressionViewModel>() }
    val dependencyGraphViewModel = remember { koin.get<DependencyGraphViewModel>() }
    val autoFlowViewModel = remember { koin.get<AutoFlowViewModel>() }

    // Pro feature ViewModels (no DI deps — standalone)
    val schedulerViewModel = remember { SchedulerViewModel() }
    val customFuzzRulesViewModel = remember { CustomFuzzRulesViewModel() }
    val testHistoryViewModel = remember { TestHistoryViewModel() }
    val brandedReportViewModel = remember { BrandedReportViewModel() }
    val notificationsViewModel = remember { NotificationsViewModel() }
    val issueTrackerViewModel = remember { IssueTrackerViewModel() }
    val apiMonitorViewModel = remember { ApiMonitorViewModel() }
    val multiEnvViewModel = remember { MultiEnvViewModel() }
    val ciCdExportViewModel = remember { CiCdExportViewModel() }
    val smartTestsViewModel = remember { SmartTestsViewModel() }

    LaunchedEffect(Unit) {
        val manageSettings = koin.get<ManageSettingsUseCase>()
        val config = manageSettings.loadConfig()
        if (config != null) {
            navigationState.completeOnboarding()
            viewModelsKey++
        }
    }

    LaunchedEffect(onboardingState.isComplete) {
        if (onboardingState.isComplete) {
            viewModelsKey++ // Force re-creation so they load the saved API key
            navigationState.completeOnboarding()
        }
    }

    PostSyncProTheme(isDarkTheme = navigationState.isDarkTheme, language = navigationState.currentLanguage) {
        val palette = LocalAppPalette.current
        val typography = LocalAppTypography.current

        Surface(
            modifier = Modifier
                .fillMaxSize()
                .focusRequester(appFocusRequester)
                .focusable()
                .onPreviewKeyEvent { event ->
                    if (
                        event.type == KeyEventType.KeyDown &&
                        event.key == Key.K &&
                        (event.isMetaPressed || event.isCtrlPressed)
                    ) {
                        isCommandPaletteVisible = !isCommandPaletteVisible
                        true
                    } else {
                        false
                    }
                },
            color = palette.background
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier.fillMaxSize().then(
                    if (isCommandPaletteVisible) Modifier.clearAndSetSemantics { } else Modifier
                )
            ) {
            if (!navigationState.isOnboardingComplete) {
                Column(modifier = Modifier.fillMaxSize()) {
                    OnboardingTopBar(palette, typography, navigationState)

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(AppDimensions.spacingLg),
                        contentAlignment = Alignment.Center
                    ) {
                        when (navigationState.currentScreen) {
                            Screen.SETTINGS -> SettingsScreen(navigationState = navigationState)
                            Screen.PRO_FEATURES -> ProFeaturesScreen(navigationState = navigationState)
                            Screen.SCHEDULER -> ProScreenWrapper(navigationState) { SchedulerScreen(viewModel = schedulerViewModel) }
                            Screen.CUSTOM_FUZZ_RULES -> ProScreenWrapper(navigationState) { CustomFuzzRulesScreen(viewModel = customFuzzRulesViewModel) }
                            Screen.TEST_HISTORY -> ProScreenWrapper(navigationState) { TestHistoryScreen(viewModel = testHistoryViewModel) }
                            Screen.BRANDED_REPORT -> ProScreenWrapper(navigationState) { BrandedReportScreen(viewModel = brandedReportViewModel) }
                            Screen.NOTIFICATIONS -> ProScreenWrapper(navigationState) { NotificationsScreen(viewModel = notificationsViewModel) }
                            Screen.ISSUE_TRACKER -> ProScreenWrapper(navigationState) { IssueTrackerScreen(viewModel = issueTrackerViewModel) }
                            Screen.API_MONITOR -> ProScreenWrapper(navigationState) { ApiMonitorScreen(viewModel = apiMonitorViewModel) }
                            Screen.MULTI_ENV -> ProScreenWrapper(navigationState) { MultiEnvScreen(viewModel = multiEnvViewModel) }
                            Screen.CICD_EXPORT -> ProScreenWrapper(navigationState) { CiCdExportScreen(viewModel = ciCdExportViewModel) }
                            Screen.SMART_TESTS -> ProScreenWrapper(navigationState) { SmartTestsScreen(viewModel = smartTestsViewModel) }
                            else -> OnboardingScreen(viewModel = onboardingViewModel)
                        }
                    }

                    AppStatusBar(palette, typography, navigationState, onboardingState)
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    AppTopBar(
                        palette = palette,
                        typography = typography,
                        navigationState = navigationState,
                        onOpenCommandPalette = { isCommandPaletteVisible = true }
                    )

                    Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        AppSidebar(navigationState = navigationState)

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(palette.background)
                                .padding(AppDimensions.spacingLg)
                        ) {
                            AnimatedContent(
                                targetState = navigationState.currentScreen,
                                transitionSpec = {
                                    fadeIn(animationSpec = androidx.compose.animation.core.tween(200)) togetherWith
                                            fadeOut(animationSpec = androidx.compose.animation.core.tween(200))
                                }
                            ) { screen ->
                                when (screen) {
                                    Screen.ONBOARDING -> OnboardingScreen(viewModel = onboardingViewModel)
                                    Screen.WORKBENCH -> WorkbenchScreen(viewModel = workbenchViewModel)
                                    Screen.SYNC -> SyncScreen(viewModel = syncViewModel)
                                    Screen.VALIDATOR -> ValidatorScreen(viewModel = validatorViewModel)
                                    Screen.FUZZ -> FuzzScreen(viewModel = fuzzViewModel)
                                    Screen.REPORT -> ReportScreen(viewModel = reportViewModel)
                                    Screen.MOCK_SERVER -> MockServerScreen(viewModel = mockServerViewModel)
                                    Screen.TEST_CHAIN -> TestChainScreen(viewModel = testChainViewModel)
                                    Screen.COVERAGE -> CoverageScreen(viewModel = coverageViewModel)
                                    Screen.BREAKING_CHANGE -> BreakingChangeScreen(viewModel = breakingChangeViewModel)
                                    Screen.REGRESSION -> RegressionScreen(viewModel = regressionViewModel)
                                    Screen.DEPENDENCY_GRAPH -> DependencyGraphScreen(viewModel = dependencyGraphViewModel)
                                    Screen.AUTO_FLOW -> AutoFlowScreen(viewModel = autoFlowViewModel)
                                    Screen.PRO_FEATURES -> ProFeaturesScreen(navigationState = navigationState)
                                    Screen.SCHEDULER -> ProScreenWrapper(navigationState) { SchedulerScreen(viewModel = schedulerViewModel) }
                                    Screen.CUSTOM_FUZZ_RULES -> ProScreenWrapper(navigationState) { CustomFuzzRulesScreen(viewModel = customFuzzRulesViewModel) }
                                    Screen.TEST_HISTORY -> ProScreenWrapper(navigationState) { TestHistoryScreen(viewModel = testHistoryViewModel) }
                                    Screen.BRANDED_REPORT -> ProScreenWrapper(navigationState) { BrandedReportScreen(viewModel = brandedReportViewModel) }
                                    Screen.NOTIFICATIONS -> ProScreenWrapper(navigationState) { NotificationsScreen(viewModel = notificationsViewModel) }
                                    Screen.ISSUE_TRACKER -> ProScreenWrapper(navigationState) { IssueTrackerScreen(viewModel = issueTrackerViewModel) }
                                    Screen.API_MONITOR -> ProScreenWrapper(navigationState) { ApiMonitorScreen(viewModel = apiMonitorViewModel) }
                                    Screen.MULTI_ENV -> ProScreenWrapper(navigationState) { MultiEnvScreen(viewModel = multiEnvViewModel) }
                                    Screen.CICD_EXPORT -> ProScreenWrapper(navigationState) { CiCdExportScreen(viewModel = ciCdExportViewModel) }
                                    Screen.SMART_TESTS -> ProScreenWrapper(navigationState) { SmartTestsScreen(viewModel = smartTestsViewModel) }
                                    Screen.SETTINGS -> SettingsScreen(navigationState = navigationState)
                                }
                            }
                        }
                    }

                    AppStatusBar(palette, typography, navigationState, onboardingState)
                }
            }
            }

            CommandPalette(
                visible = isCommandPaletteVisible,
                navigationState = navigationState,
                onDismiss = {
                    isCommandPaletteVisible = false
                    appFocusRequester.requestFocus()
                }
            )
            }
        }
    }

    DisposableEffect(validatorViewModel) {
        onDispose { validatorViewModel.onCleared() }
    }
    DisposableEffect(fuzzViewModel) {
        onDispose { fuzzViewModel.onCleared() }
    }
    DisposableEffect(reportViewModel) {
        onDispose { reportViewModel.onCleared() }
    }
    DisposableEffect(coverageViewModel) {
        onDispose { coverageViewModel.onCleared() }
    }

    DisposableEffect(Unit) {
        onDispose {
            onboardingViewModel.onCleared()
            syncViewModel.onCleared()
            workbenchViewModel.onCleared()
            mockServerViewModel.onCleared()
            testChainViewModel.onCleared()
            breakingChangeViewModel.onCleared()
            regressionViewModel.onCleared()
            dependencyGraphViewModel.onCleared()
            autoFlowViewModel.onCleared()
            schedulerViewModel.onCleared()
            customFuzzRulesViewModel.onCleared()
            testHistoryViewModel.onCleared()
            brandedReportViewModel.onCleared()
            notificationsViewModel.onCleared()
            issueTrackerViewModel.onCleared()
            apiMonitorViewModel.onCleared()
            multiEnvViewModel.onCleared()
            ciCdExportViewModel.onCleared()
            smartTestsViewModel.onCleared()
        }
    }
}

@Composable
private fun OnboardingTopBar(
    palette: AppPalette,
    typography: AppTextStyles,
    navigationState: NavigationState
) {
    Column {
        // Gradient accent line matching main TopBar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(
                    androidx.compose.ui.graphics.Brush.horizontalGradient(
                        listOf(palette.accent, BrandColors.Purple, palette.accentHover)
                    )
                )
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(AppDimensions.topBarHeight - 2.dp)
                .background(palette.topBar)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Brand dot with glow
                Box(contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(palette.accent.copy(alpha = 0.3f))
                    )
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(palette.accent)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    LocalAppStrings.current.appName,
                    style = typography.heading3.copy(color = palette.textPrimary, fontWeight = FontWeight.Bold)
                )
            }
            Spacer(Modifier.weight(1f))

            // Pro button with gradient
            val onbProInteraction = remember { MutableInteractionSource() }
            val onbProHovered by onbProInteraction.collectIsHoveredAsState()
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        if (onbProHovered)
                            androidx.compose.ui.graphics.Brush.horizontalGradient(
                                listOf(palette.accentHover, BrandColors.Purple)
                            )
                        else
                            androidx.compose.ui.graphics.Brush.horizontalGradient(
                                listOf(palette.accent, BrandColors.Purple)
                            )
                    )
                    .hoverable(onbProInteraction)
                    .clickable { navigationState.navigateTo(Screen.PRO_FEATURES) }
                    .padding(horizontal = 14.dp, vertical = 5.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        "PRO",
                        style = typography.caption.copy(
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.sp
                        )
                    )
                }
            }

            Spacer(Modifier.width(8.dp))

            // Settings button
            val onbSettingsInteraction = remember { MutableInteractionSource() }
            val onbSettingsHovered by onbSettingsInteraction.collectIsHoveredAsState()
            IconButton(
                onClick = { navigationState.navigateTo(Screen.SETTINGS) },
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(AppDimensions.buttonRadius))
                    .background(if (onbSettingsHovered) palette.surfaceHover else palette.topBar)
                    .hoverable(onbSettingsInteraction)
            ) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = LocalAppStrings.current.settings,
                    tint = palette.textSecondary,
                    modifier = Modifier.size(AppDimensions.iconSizeMd)
                )
            }
        }
    }
}

@Composable
private fun AppTopBar(
    palette: AppPalette,
    typography: AppTextStyles,
    navigationState: NavigationState,
    onOpenCommandPalette: () -> Unit
) {
    Column {
        // Subtle accent gradient line at the very top
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(
                    androidx.compose.ui.graphics.Brush.horizontalGradient(
                        listOf(palette.accent, BrandColors.Purple, palette.accentHover)
                    )
                )
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(AppDimensions.topBarHeight - 2.dp)
                .background(palette.topBar)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Brand dot with glow effect
                Box(contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(palette.accent.copy(alpha = 0.3f))
                    )
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(palette.accent)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    LocalAppStrings.current.appName,
                    style = typography.heading3.copy(color = palette.textPrimary, fontWeight = FontWeight.Bold)
                )
            }

            Spacer(Modifier.width(24.dp))

            NavigationHistoryButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = LocalAppStrings.current.navigateBack,
                enabled = navigationState.canNavigateBack,
                palette = palette,
                onClick = navigationState::navigateBack
            )
            Spacer(Modifier.width(4.dp))
            NavigationHistoryButton(
                icon = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = LocalAppStrings.current.navigateForward,
                enabled = navigationState.canNavigateForward,
                palette = palette,
                onClick = navigationState::navigateForward
            )

            Spacer(Modifier.width(12.dp))

            // Breadcrumb with subtle pill background
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(AppDimensions.chipRadius))
                    .background(palette.surfaceVariant.copy(alpha = 0.5f))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val strings = LocalAppStrings.current
                    Text(strings.workspace, style = typography.caption.copy(color = palette.textTertiary))
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = palette.textTertiary,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        navigationState.currentScreen.title(strings),
                        style = typography.caption.copy(color = palette.textPrimary, fontWeight = FontWeight.Medium)
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            val commandInteraction = remember { MutableInteractionSource() }
            val commandHovered by commandInteraction.collectIsHoveredAsState()
            val commandShortcut = remember {
                if (System.getProperty("os.name").contains("mac", ignoreCase = true)) "⌘K" else "Ctrl K"
            }
            val quickOpenLabel = LocalAppStrings.current.quickOpen
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(AppDimensions.buttonRadius))
                    .background(if (commandHovered) palette.surfaceHover else palette.surfaceVariant.copy(alpha = 0.55f))
                    .border(1.dp, palette.border, RoundedCornerShape(AppDimensions.buttonRadius))
                    .hoverable(commandInteraction)
                    .semantics {
                        role = Role.Button
                        contentDescription = quickOpenLabel
                    }
                    .clickable(onClick = onOpenCommandPalette)
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = null,
                    tint = palette.textTertiary,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    quickOpenLabel,
                    style = typography.caption.copy(color = palette.textSecondary)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    commandShortcut,
                    style = typography.caption.copy(color = palette.textTertiary, fontWeight = FontWeight.SemiBold)
                )
            }

            Spacer(Modifier.weight(1f))

            // Pro button with gradient background
            val proInteraction = remember { MutableInteractionSource() }
            val proHovered by proInteraction.collectIsHoveredAsState()
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        if (proHovered)
                            androidx.compose.ui.graphics.Brush.horizontalGradient(
                                listOf(palette.accentHover, BrandColors.Purple)
                            )
                        else
                            androidx.compose.ui.graphics.Brush.horizontalGradient(
                                listOf(palette.accent, BrandColors.Purple)
                            )
                    )
                    .hoverable(proInteraction)
                    .clickable { navigationState.navigateTo(Screen.PRO_FEATURES) }
                    .padding(horizontal = 14.dp, vertical = 5.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        "PRO",
                        style = typography.caption.copy(
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.sp
                        )
                    )
                }
            }

            Spacer(Modifier.width(8.dp))

        ThemeToggleButton(palette, navigationState)

        Spacer(Modifier.width(8.dp))

        val settingsInteraction = remember { MutableInteractionSource() }
        val settingsHovered by settingsInteraction.collectIsHoveredAsState()
        IconButton(
            onClick = { navigationState.navigateTo(Screen.SETTINGS) },
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(AppDimensions.buttonRadius))
                .background(if (settingsHovered) palette.surfaceHover else palette.topBar)
                .hoverable(settingsInteraction)
        ) {
            Icon(
                Icons.Default.Settings,
                contentDescription = LocalAppStrings.current.settings,
                tint = palette.textSecondary,
                modifier = Modifier.size(AppDimensions.iconSizeMd)
            )
        }
    } // Row
    } // Column
}

@Composable
private fun NavigationHistoryButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    palette: AppPalette,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(30.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) palette.textSecondary else palette.textTertiary.copy(alpha = 0.42f),
            modifier = Modifier.size(17.dp)
        )
    }
}

@Composable
private fun ThemeToggleButton(
    palette: AppPalette,
    navigationState: NavigationState
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    IconButton(
        onClick = { navigationState.toggleTheme() },
        modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(AppDimensions.buttonRadius))
            .background(if (isHovered) palette.surfaceHover else palette.topBar)
            .hoverable(interactionSource)
    ) {
        Icon(
            imageVector = if (navigationState.isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
            contentDescription = LocalAppStrings.current.toggleTheme,
            tint = palette.accent,
            modifier = Modifier.size(AppDimensions.iconSizeMd)
        )
    }
}

@Composable
private fun AppStatusBar(
    palette: AppPalette,
    typography: AppTextStyles,
    navigationState: NavigationState,
    onboardingState: OnboardingState
) {
    val strings = LocalAppStrings.current
    val modeLabel = if (onboardingState.selectedMode == AiMode.LOCAL) {
        strings.localAi
    } else {
        "${strings.cloudApi}: ${onboardingState.selectedProvider.displayName}"
    }
    val currentTime = remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            currentTime.value = LocalDateTime.now()
            kotlinx.coroutines.delay(60_000)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(AppDimensions.statusBarHeight)
            .background(palette.background)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Session state. This is deliberately not a green "connected" light:
        // provider reachability is only known after a real request succeeds.
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(BrandColors.Blue.copy(alpha = 0.2f))
            )
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(BrandColors.Blue)
            )
        }
        Spacer(Modifier.width(6.dp))
        Text(
            "${strings.localSession} · $modeLabel",
            style = typography.caption.copy(color = palette.textSecondary, fontWeight = FontWeight.Medium)
        )

        Spacer(Modifier.width(12.dp))
        Text("\u2022", style = typography.caption.copy(color = palette.textTertiary))
        Spacer(Modifier.width(12.dp))

        Text(
            navigationState.currentScreen.title(LocalAppStrings.current),
            style = typography.caption.copy(color = palette.textSecondary)
        )

        Spacer(Modifier.weight(1f))

        Text(
            currentTime.value.format(DateTimeFormatter.ofPattern("HH:mm")),
            style = typography.caption.copy(color = palette.textTertiary)
        )
    }
}

@Composable
private fun ProScreenWrapper(
    navigationState: NavigationState,
    content: @Composable () -> Unit
) {
    val palette = LocalAppPalette.current
    val typography = LocalAppTypography.current
    val strings = LocalAppStrings.current
    val usesSimulatedData = navigationState.currentScreen in setOf(
        Screen.SCHEDULER,
        Screen.CUSTOM_FUZZ_RULES,
        Screen.TEST_HISTORY,
        Screen.BRANDED_REPORT,
        Screen.NOTIFICATIONS,
        Screen.ISSUE_TRACKER,
        Screen.API_MONITOR,
        Screen.MULTI_ENV,
        Screen.SMART_TESTS
    )

    Column(modifier = Modifier.fillMaxSize()) {
        // Back to Pro Features header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val backInteraction = remember { MutableInteractionSource() }
            val backHovered by backInteraction.collectIsHoveredAsState()
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(AppDimensions.buttonRadius))
                    .background(if (backHovered) palette.accent.copy(alpha = 0.1f) else Color.Transparent)
                    .hoverable(backInteraction)
                    .clickable { navigationState.navigateBackFromPro() }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = strings.back,
                    tint = palette.accent,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    strings.proTitle,
                    style = typography.bodySmall.copy(color = palette.accent, fontWeight = FontWeight.SemiBold)
                )
            }
            Spacer(Modifier.width(12.dp))
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = palette.textTertiary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                navigationState.currentScreen.title(strings),
                style = typography.heading3.copy(color = palette.textPrimary, fontWeight = FontWeight.Bold)
            )
        }
        if (usesSimulatedData) {
            Spacer(Modifier.height(12.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = palette.warning.copy(alpha = if (palette.isDark) 0.10f else 0.08f),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    palette.warning.copy(alpha = 0.34f)
                ),
                shape = RoundedCornerShape(AppDimensions.buttonRadius)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Science,
                        contentDescription = null,
                        tint = palette.warning,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(Modifier.width(9.dp))
                    Text(
                        strings.previewFeature,
                        style = typography.bodySmall.copy(
                            color = palette.textPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        strings.simulatedDataNotice,
                        style = typography.bodySmall.copy(color = palette.textSecondary)
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Box(modifier = Modifier.weight(1f)) {
            content()
        }
    }
}
