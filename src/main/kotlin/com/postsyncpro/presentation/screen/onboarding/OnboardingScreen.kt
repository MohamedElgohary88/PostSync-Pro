package com.postsyncpro.presentation.screen.onboarding

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.postsyncpro.domain.model.*
import com.postsyncpro.presentation.components.FilePickerButton
import com.postsyncpro.presentation.components.StatusBadge
import com.postsyncpro.presentation.components.BadgeType
import com.postsyncpro.presentation.i18n.LocalAppStrings
import com.postsyncpro.presentation.theme.*

// ─── Public Entry Point ─────────────────────────────────────────

@Composable
fun OnboardingScreen(viewModel: OnboardingViewModel) {
    val state by viewModel.state.collectAsState()
    val palette = LocalAppPalette.current
    val typography = LocalAppTypography.current
    val strings = LocalAppStrings.current

    var currentStep by remember { mutableStateOf(0) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ── Step indicator bar ──
            StepIndicatorBar(currentStep = currentStep)

            // ── Scrollable content ──
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(40.dp))

                AnimatedContent(
                    targetState = currentStep,
                    transitionSpec = {
                        val direction = if (targetState > initialState) {
                            AnimatedContentTransitionScope.SlideDirection.Left
                        } else {
                            AnimatedContentTransitionScope.SlideDirection.Right
                        }
                        slideIntoContainer(direction, tween(400, easing = EaseOutCubic)) +
                                fadeIn(tween(300, 100)) togetherWith
                                slideOutOfContainer(direction, tween(400, easing = EaseInCubic)) +
                                fadeOut(tween(200))
                    },
                    modifier = Modifier.widthIn(max = 720.dp)
                ) { step ->
                    when (step) {
                        0 -> SystemCheckStep(
                            state = state,
                            onContinue = { currentStep = 1 }
                        )
                        1 -> AiSetupStep(
                            state = state,
                            viewModel = viewModel,
                            onBack = { currentStep = 0 },
                            onContinue = { currentStep = 2 }
                        )
                        2 -> ConfirmationStep(
                            state = state,
                            onBack = { currentStep = 1 },
                            onGetStarted = { viewModel.saveAndContinue() }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(48.dp))
            }
        }

        // ── Error snackbar ──
        state.error?.let { error ->
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(AppDimensions.spacingLg),
                shape = RoundedCornerShape(AppDimensions.cardRadius),
                color = StatusColors.Error.copy(alpha = 0.15f),
                border = BorderStroke(AppDimensions.borderWidth, StatusColors.Error.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier.padding(AppDimensions.spacingMd),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppDimensions.spacingSm)
                ) {
                    Icon(
                        Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = StatusColors.Error,
                        modifier = Modifier.size(AppDimensions.iconSizeMd)
                    )
                    Text(
                        text = error,
                        style = typography.bodySmall,
                        color = StatusColors.Error
                    )
                }
            }
        }
    }
}

// ─── Step Indicator Bar ─────────────────────────────────────────

@Composable
private fun StepIndicatorBar(currentStep: Int) {
    val palette = LocalAppPalette.current
    val typography = LocalAppTypography.current

    val strings = LocalAppStrings.current
    val steps = listOf(strings.stepSystemCheck, strings.stepAiSetup, strings.stepConfirm)

    Surface(
        color = palette.surface,
        border = BorderStroke(AppDimensions.borderWidth, palette.borderSubtle)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppDimensions.spacingXl, vertical = AppDimensions.spacingMd),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            steps.forEachIndexed { index, label ->
                val isActive = index == currentStep
                val isCompleted = index < currentStep

                // Connector line before (except first)
                if (index > 0) {
                    Box(
                        modifier = Modifier
                            .width(48.dp)
                            .height(2.dp)
                            .clip(RoundedCornerShape(1.dp))
                            .background(
                                if (isCompleted || isActive) BrandColors.Orange.copy(alpha = 0.6f)
                                else palette.borderSubtle
                            )
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppDimensions.spacingSm)
                ) {
                    // Step circle
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    isCompleted -> BrandColors.Orange
                                    isActive -> BrandColors.Orange.copy(alpha = 0.15f)
                                    else -> palette.surfaceVariant
                                }
                            )
                            .then(
                                if (isActive) Modifier.border(
                                    1.5.dp,
                                    BrandColors.Orange,
                                    CircleShape
                                ) else Modifier
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isCompleted) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = palette.textOnAccent,
                                modifier = Modifier.size(14.dp)
                            )
                        } else {
                            Text(
                                text = "${index + 1}",
                                style = typography.caption.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                ),
                                color = if (isActive) BrandColors.Orange else palette.textTertiary
                            )
                        }
                    }

                    Text(
                        text = label,
                        style = typography.label,
                        color = when {
                            isActive -> palette.textPrimary
                            isCompleted -> BrandColors.Orange
                            else -> palette.textTertiary
                        }
                    )
                }
            }
        }
    }
}

// ─── Step 0: System Check ───────────────────────────────────────

@Composable
private fun SystemCheckStep(
    state: OnboardingState,
    onContinue: () -> Unit
) {
    val palette = LocalAppPalette.current
    val typography = LocalAppTypography.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppDimensions.spacingXl),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ── Gradient welcome text ──
        val strings = LocalAppStrings.current
        Text(
            text = strings.welcomeTo,
            style = typography.body.copy(letterSpacing = 2.sp),
            color = palette.textTertiary
        )
        Spacer(modifier = Modifier.height(AppDimensions.spacingSm))

        Box {
            // Shadow layer for gradient illusion
            Text(
                text = strings.appName,
                style = typography.heading1.copy(
                    fontSize = 36.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-1).sp
                ),
                color = BrandColors.Orange.copy(alpha = 0.25f),
                modifier = Modifier.graphicsLayer {
                    translationY = 2f
                    scaleX = 1.01f
                }
            )
            Text(
                text = strings.appName,
                style = typography.heading1.copy(
                    fontSize = 36.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-1).sp
                ),
                color = BrandColors.Orange
            )
        }

        Spacer(modifier = Modifier.height(AppDimensions.spacingXs))
        Text(
            text = strings.onboardingSubtitle,
            style = typography.body.copy(letterSpacing = 0.5.sp),
            color = palette.textSecondary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(AppDimensions.spacingXxl))

        // ── System profile loading or display ──
        if (state.systemProfile == null) {
            CircularProgressIndicator(
                color = BrandColors.Orange,
                strokeWidth = 2.dp,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(AppDimensions.spacingSm))
            Text(
                text = strings.detectingSystem,
                style = typography.bodySmall,
                color = palette.textTertiary
            )
        } else {
            val profile = state.systemProfile

            // ── Section label ──
            SectionLabel(text = strings.systemProfile)
            Spacer(modifier = Modifier.height(AppDimensions.spacingMd))

            // ── Stat pills row ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppDimensions.spacingSm)
            ) {
                StatPill(
                    icon = Icons.Default.Memory,
                    label = strings.ram,
                    value = "${profile.totalRamMb / 1024} GB",
                    modifier = Modifier.weight(1f)
                )
                StatPill(
                    icon = Icons.Default.DeveloperBoard,
                    label = strings.cpu,
                    value = "${profile.cpuCores} cores",
                    modifier = Modifier.weight(1f)
                )
                StatPill(
                    icon = Icons.Default.Computer,
                    label = strings.os,
                    value = profile.osName,
                    modifier = Modifier.weight(1f)
                )
                StatPill(
                    icon = Icons.Default.Architecture,
                    label = strings.arch,
                    value = profile.osArch,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(AppDimensions.spacingLg))

            // ── AI Recommendation banner ──
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(AppDimensions.cardRadius),
                color = BrandColors.Orange.copy(alpha = 0.06f),
                border = BorderStroke(AppDimensions.borderWidth, BrandColors.Orange.copy(alpha = 0.15f))
            ) {
                Row(
                    modifier = Modifier.padding(AppDimensions.spacingLg),
                    horizontalArrangement = Arrangement.spacedBy(AppDimensions.spacingMd)
                ) {
                    // Gradient left accent bar
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(48.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(
                                Brush.verticalGradient(
                                    listOf(BrandColors.Orange, BrandColors.Purple)
                                )
                            )
                    )
                    Column(
                        verticalArrangement = Arrangement.spacedBy(AppDimensions.spacingXs)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(AppDimensions.spacingSm)
                        ) {
                            Icon(
                                Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = BrandColors.Orange,
                                modifier = Modifier.size(AppDimensions.iconSizeMd)
                            )
                            Text(
                                text = "${strings.recommended}: ${profile.recommendedMode.displayName}",
                                style = typography.heading3,
                                color = palette.textPrimary
                            )
                        }
                        Text(
                            text = profile.recommendation,
                            style = typography.bodySmall,
                            color = palette.textSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(AppDimensions.spacingXxl))

            // ── Pulsing Continue button ──
            PulsingContinueButton(
                text = strings.continueToSetup,
                onClick = onContinue
            )
        }
    }
}

// ─── Step 1: AI Setup ───────────────────────────────────────────

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun AiSetupStep(
    state: OnboardingState,
    viewModel: OnboardingViewModel,
    onBack: () -> Unit,
    onContinue: () -> Unit
) {
    val palette = LocalAppPalette.current
    val typography = LocalAppTypography.current

    val strings = LocalAppStrings.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppDimensions.spacingXl),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = strings.chooseAiEngine,
            style = typography.heading1,
            color = palette.textPrimary
        )
        Spacer(modifier = Modifier.height(AppDimensions.spacingXs))
        Text(
            text = strings.selectAiEngineDesc,
            style = typography.body,
            color = palette.textSecondary
        )

        Spacer(modifier = Modifier.height(AppDimensions.spacingXl))

        // ── Mode selection cards ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppDimensions.spacingLg)
        ) {
            ModeSelectionCard(
                icon = Icons.Default.Cloud,
                title = strings.cloudApi,
                subtitle = strings.bringYourKey,
                description = strings.cloudApiDesc,
                isSelected = state.selectedMode == AiMode.CLOUD,
                onClick = { viewModel.selectMode(AiMode.CLOUD) },
                modifier = Modifier.weight(1f)
            )
            ModeSelectionCard(
                icon = Icons.Default.DesktopWindows,
                title = strings.localAi,
                subtitle = strings.fullyOffline,
                description = strings.localAiDesc,
                isSelected = state.selectedMode == AiMode.LOCAL,
                onClick = { viewModel.selectMode(AiMode.LOCAL) },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(AppDimensions.spacingXl))

        HorizontalDivider(color = palette.divider, thickness = AppDimensions.borderWidth)

        Spacer(modifier = Modifier.height(AppDimensions.spacingXl))

        // ── Mode-specific configuration ──
        AnimatedContent(
            targetState = state.selectedMode,
            transitionSpec = {
                fadeIn(tween(250)) + expandVertically(tween(300)) togetherWith
                        fadeOut(tween(150)) + shrinkVertically(tween(200))
            }
        ) { mode ->
            when (mode) {
                AiMode.CLOUD -> CloudSetupPanel(state = state, viewModel = viewModel)
                AiMode.LOCAL -> LocalSetupPanel(state = state, viewModel = viewModel)
            }
        }

        Spacer(modifier = Modifier.height(AppDimensions.spacingXxl))

        // ── Navigation buttons ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            OutlinedButton(
                onClick = onBack,
                shape = RoundedCornerShape(AppDimensions.buttonRadius),
                border = BorderStroke(AppDimensions.borderWidth, palette.border),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = palette.textSecondary
                )
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null,
                    modifier = Modifier.size(AppDimensions.iconSizeSm)
                )
                Spacer(modifier = Modifier.width(AppDimensions.spacingSm))
                Text(strings.back, style = typography.button)
            }

            val canContinue = when (state.selectedMode) {
                AiMode.CLOUD -> state.apiKey.isNotBlank()
                AiMode.LOCAL -> state.localModelPath.isNotBlank() || state.selectedModelId != null
            }

            Button(
                onClick = onContinue,
                enabled = canContinue,
                shape = RoundedCornerShape(AppDimensions.buttonRadius),
                colors = ButtonDefaults.buttonColors(
                    containerColor = BrandColors.Orange,
                    contentColor = palette.textOnAccent,
                    disabledContainerColor = palette.surfaceVariant,
                    disabledContentColor = palette.textTertiary
                )
            ) {
                Text(strings.continueText, style = typography.button)
                Spacer(modifier = Modifier.width(AppDimensions.spacingSm))
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(AppDimensions.iconSizeSm)
                )
            }
        }
    }
}

// ─── Mode Selection Card ────────────────────────────────────────

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun ModeSelectionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    description: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalAppPalette.current
    val typography = LocalAppTypography.current
    var isHovered by remember { mutableStateOf(false) }

    val cardBg = when {
        isSelected -> BrandColors.Orange.copy(alpha = 0.06f)
        isHovered -> palette.surfaceHover
        else -> palette.surface
    }

    val borderColor = when {
        isSelected -> BrandColors.Orange.copy(alpha = 0.4f)
        else -> palette.borderSubtle
    }

    val elevation by animateDpAsState(
        targetValue = if (isSelected) 6.dp else 0.dp,
        animationSpec = tween(200)
    )

    Surface(
        modifier = modifier
            .shadow(
                elevation = elevation,
                shape = RoundedCornerShape(AppDimensions.cardRadius),
                ambientColor = if (isSelected) BrandColors.Orange.copy(alpha = 0.2f) else Color.Transparent,
                spotColor = if (isSelected) BrandColors.Orange.copy(alpha = 0.15f) else Color.Transparent
            )
            .onPointerEvent(PointerEventType.Enter) { isHovered = true }
            .onPointerEvent(PointerEventType.Exit) { isHovered = false },
        onClick = onClick,
        shape = RoundedCornerShape(AppDimensions.cardRadius),
        color = cardBg,
        border = BorderStroke(AppDimensions.borderWidth, borderColor)
    ) {
        Column {
            // Gradient top border accent
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(
                        if (isSelected) Brush.horizontalGradient(
                            listOf(BrandColors.Orange, BrandColors.OrangeLight, BrandColors.Purple)
                        ) else Brush.horizontalGradient(
                            listOf(palette.borderSubtle, palette.borderSubtle)
                        )
                    )
            )

            Column(
                modifier = Modifier.padding(AppDimensions.spacingXl),
                verticalArrangement = Arrangement.spacedBy(AppDimensions.spacingMd)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Icon
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(AppDimensions.cardRadius))
                            .background(
                                if (isSelected) BrandColors.Orange.copy(alpha = 0.12f)
                                else palette.surfaceVariant
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            icon,
                            contentDescription = null,
                            tint = if (isSelected) BrandColors.Orange else palette.textTertiary,
                            modifier = Modifier.size(AppDimensions.iconSizeLg)
                        )
                    }

                    // Radio indicator
                    RadioButton(
                        selected = isSelected,
                        onClick = onClick,
                        colors = RadioButtonDefaults.colors(
                            selectedColor = BrandColors.Orange,
                            unselectedColor = palette.textTertiary
                        )
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(AppDimensions.spacingXs)) {
                    Text(
                        text = title,
                        style = typography.heading2,
                        color = if (isSelected) palette.textPrimary else palette.textSecondary
                    )
                    Text(
                        text = subtitle,
                        style = typography.label,
                        color = if (isSelected) BrandColors.Orange else palette.textTertiary
                    )
                }

                Text(
                    text = description,
                    style = typography.bodySmall,
                    color = palette.textTertiary
                )
            }
        }
    }
}

// ─── Cloud Setup Panel ──────────────────────────────────────────

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun CloudSetupPanel(
    state: OnboardingState,
    viewModel: OnboardingViewModel
) {
    val palette = LocalAppPalette.current
    val typography = LocalAppTypography.current

    val strings = LocalAppStrings.current
    var showApiKeyHelp by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(AppDimensions.spacingLg)
    ) {
        SectionLabel(text = strings.provider)

        // ── Segmented control ──
        ProviderSegmentedControl(
            selectedProvider = state.selectedProvider,
            onSelect = { viewModel.selectProvider(it) }
        )

        // ── Active model chip ──
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppDimensions.spacingSm)
        ) {
            Text(
                text = strings.model,
                style = typography.bodySmall,
                color = palette.textTertiary
            )
            Surface(
                shape = RoundedCornerShape(AppDimensions.chipRadius),
                color = BrandColors.Purple.copy(alpha = 0.12f),
                border = BorderStroke(AppDimensions.borderWidth, BrandColors.Purple.copy(alpha = 0.25f))
            ) {
                Text(
                    text = state.selectedProvider.defaultModel,
                    style = typography.codeSmall,
                    color = BrandColors.PurpleLight,
                    modifier = Modifier.padding(
                        horizontal = AppDimensions.spacingSm,
                        vertical = AppDimensions.spacingXs
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(AppDimensions.spacingXs))

        SectionLabel(text = strings.apiKey)

        // ── API Key input ──
        OutlinedTextField(
            value = state.apiKey,
            onValueChange = { viewModel.updateApiKey(it) },
            modifier = Modifier.fillMaxWidth(),
            textStyle = typography.code.copy(color = palette.textPrimary),
            placeholder = {
                Text(
                    text = when (state.selectedProvider) {
                        CloudProvider.GEMINI -> "AIzaSy..."
                        CloudProvider.GROQ -> "gsk_..."
                    },
                    style = typography.code,
                    color = palette.textTertiary
                )
            },
            singleLine = true,
            visualTransformation = if (state.isApiKeyVisible) VisualTransformation.None
            else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { viewModel.toggleApiKeyVisibility() }) {
                    Icon(
                        if (state.isApiKeyVisible) Icons.Default.VisibilityOff
                        else Icons.Default.Visibility,
                        contentDescription = "Toggle visibility",
                        tint = palette.textTertiary,
                        modifier = Modifier.size(AppDimensions.iconSizeMd)
                    )
                }
            },
            shape = RoundedCornerShape(AppDimensions.inputRadius),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = BrandColors.Orange,
                unfocusedBorderColor = palette.inputBorder,
                focusedContainerColor = palette.inputBackground,
                unfocusedContainerColor = palette.inputBackground,
                cursorColor = BrandColors.Orange
            )
        )

        // ── API key help link ──
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(AppDimensions.buttonRadius))
                .clickable { showApiKeyHelp = !showApiKeyHelp }
                .padding(vertical = AppDimensions.spacingXs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppDimensions.spacingXs)
        ) {
            Icon(
                if (showApiKeyHelp) Icons.Default.ExpandLess else Icons.Default.HelpOutline,
                contentDescription = null,
                tint = BrandColors.Orange.copy(alpha = 0.8f),
                modifier = Modifier.size(AppDimensions.iconSizeSm)
            )
            Text(
                text = strings.dontHaveKey,
                style = typography.bodySmall.copy(color = BrandColors.Orange.copy(alpha = 0.8f))
            )
        }

        AnimatedVisibility(
            visible = showApiKeyHelp,
            enter = expandVertically(tween(200)) + fadeIn(tween(200)),
            exit = shrinkVertically(tween(150)) + fadeOut(tween(100))
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(AppDimensions.cardRadius),
                color = palette.codeBg,
                border = BorderStroke(AppDimensions.borderWidth, palette.borderSubtle)
            ) {
                Column(
                    modifier = Modifier.padding(AppDimensions.spacingLg),
                    verticalArrangement = Arrangement.spacedBy(AppDimensions.spacingMd)
                ) {
                    ApiKeyHelpItem(
                        provider = "Google Gemini",
                        url = "aistudio.google.com",
                        description = "Free tier available. Navigate to API Keys section."
                    )
                    HorizontalDivider(color = palette.divider)
                    ApiKeyHelpItem(
                        provider = "Groq",
                        url = "console.groq.com/keys",
                        description = "Create a free account and generate an API key."
                    )
                }
            }
        }
    }
}

@Composable
private fun ApiKeyHelpItem(provider: String, url: String, description: String) {
    val palette = LocalAppPalette.current
    val typography = LocalAppTypography.current

    Column(verticalArrangement = Arrangement.spacedBy(AppDimensions.spacingXs)) {
        Text(
            text = provider,
            style = typography.heading3.copy(fontSize = 13.sp),
            color = palette.textPrimary
        )
        Text(
            text = url,
            style = typography.codeSmall,
            color = BrandColors.Orange
        )
        Text(
            text = description,
            style = typography.caption,
            color = palette.textTertiary
        )
    }
}

// ─── Provider Segmented Control ─────────────────────────────────

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun ProviderSegmentedControl(
    selectedProvider: CloudProvider,
    onSelect: (CloudProvider) -> Unit
) {
    val palette = LocalAppPalette.current
    val typography = LocalAppTypography.current

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AppDimensions.cardRadius),
        color = palette.surfaceVariant,
        border = BorderStroke(AppDimensions.borderWidth, palette.borderSubtle)
    ) {
        Row(
            modifier = Modifier.padding(AppDimensions.spacingXs)
        ) {
            CloudProvider.entries.forEach { provider ->
                val isSelected = provider == selectedProvider
                var isHovered by remember { mutableStateOf(false) }

                val bgColor = when {
                    isSelected -> BrandColors.Orange
                    isHovered -> palette.surfaceHover
                    else -> Color.Transparent
                }

                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .onPointerEvent(PointerEventType.Enter) { isHovered = true }
                        .onPointerEvent(PointerEventType.Exit) { isHovered = false },
                    onClick = { onSelect(provider) },
                    shape = RoundedCornerShape(AppDimensions.buttonRadius),
                    color = bgColor
                ) {
                    Box(
                        modifier = Modifier.padding(
                            horizontal = AppDimensions.spacingLg,
                            vertical = AppDimensions.spacingMd
                        ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = provider.displayName,
                            style = typography.button,
                            color = if (isSelected) palette.textOnAccent else palette.textSecondary
                        )
                    }
                }
            }
        }
    }
}

// ─── Local Setup Panel ──────────────────────────────────────────

@Composable
private fun LocalSetupPanel(
    state: OnboardingState,
    viewModel: OnboardingViewModel
) {
    val palette = LocalAppPalette.current
    val typography = LocalAppTypography.current

    val strings = LocalAppStrings.current
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(AppDimensions.spacingLg)
    ) {
        SectionLabel(text = strings.availableModels)

        // ── Model download cards ──
        AvailableModels.models.forEach { model ->
            val downloadProgress = state.downloadProgress[model.id]
            val isDownloaded = state.downloadedModels.contains(model.id)
            val isSelectedModel = state.selectedModelId == model.id

            ModelDownloadCard(
                model = model,
                downloadProgress = downloadProgress,
                isDownloaded = isDownloaded,
                isSelected = isSelectedModel,
                systemRamMb = state.systemProfile?.totalRamMb ?: 0L,
                onDownload = { viewModel.downloadModel(model.id) },
                onCancel = { viewModel.cancelDownload(model.id) },
                onDelete = { viewModel.deleteModel(model.id) },
                onSelect = { viewModel.selectModel(model.id) }
            )
        }

        Spacer(modifier = Modifier.height(AppDimensions.spacingSm))

        HorizontalDivider(color = palette.divider)

        Spacer(modifier = Modifier.height(AppDimensions.spacingSm))

        // ── Custom model from disk ──
        SectionLabel(text = strings.orSelectFromDisk)
        Spacer(modifier = Modifier.height(AppDimensions.spacingXs))

        FilePickerButton(
            label = strings.chooseGgufModel,
            selectedFile = state.localModelPath.takeIf { it.isNotBlank() }?.let {
                it.substringAfterLast("/").substringAfterLast("\\")
            },
            fileExtensions = listOf("gguf"),
            onFileSelected = { file -> viewModel.updateLocalModelPath(file.absolutePath) }
        )

        // ── Info text ──
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppDimensions.spacingSm)
        ) {
            Icon(
                Icons.Default.Info,
                contentDescription = null,
                tint = palette.textTertiary,
                modifier = Modifier.size(AppDimensions.iconSizeSm)
            )
            Text(
                text = "Models are stored in ~/.postsyncpro/models/",
                style = typography.codeSmall,
                color = palette.textTertiary
            )
        }
    }
}

// ─── Model Download Card ────────────────────────────────────────

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun ModelDownloadCard(
    model: DownloadableModel,
    downloadProgress: ModelDownloadProgress?,
    isDownloaded: Boolean,
    isSelected: Boolean,
    systemRamMb: Long,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    onSelect: () -> Unit
) {
    val palette = LocalAppPalette.current
    val typography = LocalAppTypography.current
    var isHovered by remember { mutableStateOf(false) }

    val status = downloadProgress?.status ?: if (isDownloaded) DownloadStatus.COMPLETED else DownloadStatus.IDLE

    val cardBg = when {
        isSelected && isDownloaded -> StatusColors.Success.copy(alpha = 0.04f)
        isHovered -> palette.surfaceHover
        else -> palette.surface
    }

    val borderColor = when {
        isSelected && isDownloaded -> StatusColors.Success.copy(alpha = 0.3f)
        status == DownloadStatus.DOWNLOADING -> BrandColors.Orange.copy(alpha = 0.3f)
        else -> palette.borderSubtle
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .onPointerEvent(PointerEventType.Enter) { isHovered = true }
            .onPointerEvent(PointerEventType.Exit) { isHovered = false },
        shape = RoundedCornerShape(AppDimensions.cardRadius),
        color = cardBg,
        border = BorderStroke(AppDimensions.borderWidth, borderColor)
    ) {
        Column(
            modifier = Modifier.padding(AppDimensions.spacingLg),
            verticalArrangement = Arrangement.spacedBy(AppDimensions.spacingMd)
        ) {
            // ── Header row: name + badges ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppDimensions.spacingSm)
            ) {
                Text(
                    text = model.name,
                    style = typography.heading3,
                    color = palette.textPrimary
                )

                // Parameters badge
                Surface(
                    shape = RoundedCornerShape(AppDimensions.chipRadius),
                    color = BrandColors.Purple.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = model.parameters,
                        style = typography.caption.copy(fontWeight = FontWeight.Bold),
                        color = BrandColors.PurpleLight,
                        modifier = Modifier.padding(
                            horizontal = AppDimensions.spacingSm,
                            vertical = 2.dp
                        )
                    )
                }

                if (model.recommended) {
                    StatusBadge(text = LocalAppStrings.current.recommended, type = BadgeType.SUCCESS)
                }

                Spacer(modifier = Modifier.weight(1f))

                // Downloaded status or selection
                if (isDownloaded) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AppDimensions.spacingSm)
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = StatusColors.Success,
                            modifier = Modifier.size(AppDimensions.iconSizeMd)
                        )
                        Text(
                            text = LocalAppStrings.current.downloaded,
                            style = typography.label,
                            color = StatusColors.Success
                        )
                    }
                }
            }

            // ── Description ──
            Text(
                text = model.description,
                style = typography.bodySmall,
                color = palette.textSecondary
            )

            // ── Info pills row ──
            Row(
                horizontalArrangement = Arrangement.spacedBy(AppDimensions.spacingSm)
            ) {
                InfoPill(
                    icon = Icons.Default.Storage,
                    text = model.sizeDisplay
                )
                InfoPill(
                    icon = Icons.Default.Memory,
                    text = "${model.minRamGb}GB+ RAM"
                )
                InfoPill(
                    icon = Icons.Default.TextFields,
                    text = "${model.contextLength} ctx"
                )
                InfoPill(
                    icon = Icons.Default.Compress,
                    text = model.quantization
                )
            }

            // ── Action row ──
            when (status) {
                DownloadStatus.IDLE -> {
                    Button(
                        onClick = onDownload,
                        shape = RoundedCornerShape(AppDimensions.buttonRadius),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BrandColors.Orange,
                            contentColor = palette.textOnAccent
                        ),
                        contentPadding = PaddingValues(
                            horizontal = AppDimensions.spacingLg,
                            vertical = AppDimensions.spacingMd
                        )
                    ) {
                        Icon(
                            Icons.Default.Download,
                            contentDescription = null,
                            modifier = Modifier.size(AppDimensions.iconSizeSm)
                        )
                        Spacer(modifier = Modifier.width(AppDimensions.spacingSm))
                        Text(LocalAppStrings.current.download, style = typography.button)
                    }
                }

                DownloadStatus.DOWNLOADING -> {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(AppDimensions.spacingSm)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(AppDimensions.spacingSm)
                        ) {
                            LinearProgressIndicator(
                                progress = { downloadProgress?.progress ?: 0f },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = BrandColors.Orange,
                                trackColor = palette.surfaceVariant
                            )
                            Text(
                                text = "${((downloadProgress?.progress ?: 0f) * 100).toInt()}%",
                                style = typography.label,
                                color = BrandColors.Orange
                            )
                            OutlinedButton(
                                onClick = onCancel,
                                shape = RoundedCornerShape(AppDimensions.buttonRadius),
                                border = BorderStroke(AppDimensions.borderWidth, palette.border),
                                contentPadding = PaddingValues(
                                    horizontal = AppDimensions.spacingMd,
                                    vertical = AppDimensions.spacingXs
                                )
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Cancel",
                                    tint = palette.textSecondary,
                                    modifier = Modifier.size(AppDimensions.iconSizeSm)
                                )
                                Spacer(modifier = Modifier.width(AppDimensions.spacingXs))
                                Text(LocalAppStrings.current.cancel, style = typography.caption, color = palette.textSecondary)
                            }
                        }
                        // Bytes progress text
                        if (downloadProgress != null && downloadProgress.totalBytes > 0) {
                            Text(
                                text = "${formatBytes(downloadProgress.bytesDownloaded)} / ${formatBytes(downloadProgress.totalBytes)}",
                                style = typography.caption,
                                color = palette.textTertiary
                            )
                        }
                    }
                }

                DownloadStatus.PAUSED -> {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(AppDimensions.spacingSm)
                    ) {
                        Button(
                            onClick = onDownload,
                            shape = RoundedCornerShape(AppDimensions.buttonRadius),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = BrandColors.Orange,
                                contentColor = palette.textOnAccent
                            )
                        ) {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(AppDimensions.iconSizeSm)
                            )
                            Spacer(modifier = Modifier.width(AppDimensions.spacingXs))
                            Text(LocalAppStrings.current.resume, style = typography.button)
                        }
                        Text(
                            text = LocalAppStrings.current.paused,
                            style = typography.label,
                            color = palette.textTertiary,
                            modifier = Modifier.align(Alignment.CenterVertically)
                        )
                    }
                }

                DownloadStatus.COMPLETED -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AppDimensions.spacingSm)
                    ) {
                        // "Use this model" radio selection
                        Surface(
                            onClick = onSelect,
                            shape = RoundedCornerShape(AppDimensions.buttonRadius),
                            color = if (isSelected) StatusColors.Success.copy(alpha = 0.1f)
                            else palette.surfaceVariant,
                            border = BorderStroke(
                                AppDimensions.borderWidth,
                                if (isSelected) StatusColors.Success.copy(alpha = 0.4f)
                                else palette.border
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(
                                    horizontal = AppDimensions.spacingMd,
                                    vertical = AppDimensions.spacingSm
                                ),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(AppDimensions.spacingSm)
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = onSelect,
                                    modifier = Modifier.size(18.dp),
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = StatusColors.Success,
                                        unselectedColor = palette.textTertiary
                                    )
                                )
                                Text(
                                    text = if (isSelected) LocalAppStrings.current.selected else LocalAppStrings.current.useThisModel,
                                    style = typography.button,
                                    color = if (isSelected) StatusColors.Success else palette.textSecondary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        // Delete button
                        OutlinedButton(
                            onClick = onDelete,
                            shape = RoundedCornerShape(AppDimensions.buttonRadius),
                            border = BorderStroke(AppDimensions.borderWidth, StatusColors.Error.copy(alpha = 0.3f)),
                            contentPadding = PaddingValues(
                                horizontal = AppDimensions.spacingMd,
                                vertical = AppDimensions.spacingXs
                            ),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = StatusColors.Error.copy(alpha = 0.7f)
                            )
                        ) {
                            Icon(
                                Icons.Default.DeleteOutline,
                                contentDescription = "Delete model",
                                modifier = Modifier.size(AppDimensions.iconSizeSm)
                            )
                            Spacer(modifier = Modifier.width(AppDimensions.spacingXs))
                            Text(LocalAppStrings.current.delete, style = typography.caption)
                        }
                    }
                }

                DownloadStatus.FAILED -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AppDimensions.spacingSm)
                    ) {
                        Icon(
                            Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = StatusColors.Error,
                            modifier = Modifier.size(AppDimensions.iconSizeSm)
                        )
                        Text(
                            text = downloadProgress?.error ?: "Download failed",
                            style = typography.bodySmall,
                            color = StatusColors.Error
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Button(
                            onClick = onDownload,
                            shape = RoundedCornerShape(AppDimensions.buttonRadius),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = BrandColors.Orange,
                                contentColor = palette.textOnAccent
                            )
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(AppDimensions.iconSizeSm)
                            )
                            Spacer(modifier = Modifier.width(AppDimensions.spacingXs))
                            Text(LocalAppStrings.current.retry, style = typography.button)
                        }
                    }
                }
            }
        }
    }
}

// ─── Step 2: Confirmation ───────────────────────────────────────

@Composable
private fun ConfirmationStep(
    state: OnboardingState,
    onBack: () -> Unit,
    onGetStarted: () -> Unit
) {
    val palette = LocalAppPalette.current
    val typography = LocalAppTypography.current

    val strings = LocalAppStrings.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppDimensions.spacingXl),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ── Checkmark icon ──
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(StatusColors.Success.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = StatusColors.Success,
                modifier = Modifier.size(32.dp)
            )
        }

        Spacer(modifier = Modifier.height(AppDimensions.spacingLg))

        Text(
            text = strings.readyToLaunch,
            style = typography.heading1,
            color = palette.textPrimary
        )
        Spacer(modifier = Modifier.height(AppDimensions.spacingXs))
        Text(
            text = strings.reviewConfig,
            style = typography.body,
            color = palette.textSecondary
        )

        Spacer(modifier = Modifier.height(AppDimensions.spacingXl))

        // ── Summary card ──
        Surface(
            modifier = Modifier.fillMaxWidth().widthIn(max = 480.dp),
            shape = RoundedCornerShape(AppDimensions.cardRadius),
            color = palette.surface,
            border = BorderStroke(AppDimensions.borderWidth, palette.borderSubtle)
        ) {
            Column(
                modifier = Modifier.padding(AppDimensions.spacingXl),
                verticalArrangement = Arrangement.spacedBy(AppDimensions.spacingLg)
            ) {
                Text(
                    text = strings.configSummary,
                    style = typography.heading3,
                    color = palette.textPrimary
                )

                HorizontalDivider(color = palette.divider)

                SummaryRow(
                    icon = Icons.Default.Settings,
                    label = strings.aiMode,
                    value = state.selectedMode.displayName
                )

                when (state.selectedMode) {
                    AiMode.CLOUD -> {
                        SummaryRow(
                            icon = Icons.Default.Cloud,
                            label = strings.provider,
                            value = state.selectedProvider.displayName
                        )
                        SummaryRow(
                            icon = Icons.Default.SmartToy,
                            label = strings.model.removeSuffix(":"),
                            value = state.selectedProvider.defaultModel
                        )
                        SummaryRow(
                            icon = Icons.Default.Key,
                            label = strings.apiKey,
                            value = if (state.apiKey.isNotBlank())
                                "${state.apiKey.take(6)}${"*".repeat(12)}"
                            else "Not set"
                        )
                    }
                    AiMode.LOCAL -> {
                        val selectedModel = state.selectedModelId?.let { id ->
                            AvailableModels.getById(id)
                        }
                        SummaryRow(
                            icon = Icons.Default.SmartToy,
                            label = strings.model.removeSuffix(":"),
                            value = selectedModel?.name
                                ?: state.localModelPath.substringAfterLast("/").ifBlank { "Custom model" }
                        )
                        if (selectedModel != null) {
                            SummaryRow(
                                icon = Icons.Default.Storage,
                                label = "Size",
                                value = selectedModel.sizeDisplay
                            )
                        }
                    }
                }

                state.systemProfile?.let { profile ->
                    HorizontalDivider(color = palette.divider)
                    SummaryRow(
                        icon = Icons.Default.Computer,
                        label = strings.system,
                        value = "${profile.osName} ${profile.osArch} / ${profile.cpuCores} cores / ${profile.totalRamMb / 1024}GB RAM"
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(AppDimensions.spacingXxl))

        // ── Navigation buttons ──
        Row(
            modifier = Modifier.fillMaxWidth().widthIn(max = 480.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            OutlinedButton(
                onClick = onBack,
                shape = RoundedCornerShape(AppDimensions.buttonRadius),
                border = BorderStroke(AppDimensions.borderWidth, palette.border),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = palette.textSecondary)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null,
                    modifier = Modifier.size(AppDimensions.iconSizeSm)
                )
                Spacer(modifier = Modifier.width(AppDimensions.spacingSm))
                Text(strings.back, style = typography.button)
            }

            // ── Animated "Get Started" button ──
            PulsingContinueButton(
                text = strings.getStarted,
                icon = Icons.Default.RocketLaunch,
                onClick = onGetStarted,
                isLoading = state.isSaving
            )
        }
    }
}

// ─── Shared Components ──────────────────────────────────────────

@Composable
private fun SectionLabel(text: String) {
    val palette = LocalAppPalette.current
    val typography = LocalAppTypography.current

    Text(
        text = text,
        style = typography.label.copy(
            letterSpacing = 1.2.sp,
            fontWeight = FontWeight.SemiBold
        ),
        color = palette.textTertiary
    )
}

@Composable
private fun StatPill(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    val palette = LocalAppPalette.current
    val typography = LocalAppTypography.current

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(AppDimensions.cardRadius),
        color = palette.surface,
        border = BorderStroke(AppDimensions.borderWidth, palette.borderSubtle)
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = AppDimensions.spacingMd,
                vertical = AppDimensions.spacingSm
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppDimensions.spacingSm)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = BrandColors.Orange.copy(alpha = 0.7f),
                modifier = Modifier.size(AppDimensions.iconSizeSm)
            )
            Column {
                Text(
                    text = label,
                    style = typography.caption,
                    color = palette.textTertiary
                )
                Text(
                    text = value,
                    style = typography.label.copy(fontWeight = FontWeight.Bold),
                    color = palette.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun InfoPill(
    icon: ImageVector,
    text: String
) {
    val palette = LocalAppPalette.current
    val typography = LocalAppTypography.current

    Surface(
        shape = RoundedCornerShape(AppDimensions.chipRadius),
        color = palette.surfaceVariant,
        border = BorderStroke(AppDimensions.borderWidth, palette.borderSubtle)
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = AppDimensions.spacingSm,
                vertical = AppDimensions.spacingXs
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppDimensions.spacingXs)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = palette.textTertiary,
                modifier = Modifier.size(12.dp)
            )
            Text(
                text = text,
                style = typography.caption,
                color = palette.textSecondary
            )
        }
    }
}

@Composable
private fun SummaryRow(
    icon: ImageVector,
    label: String,
    value: String
) {
    val palette = LocalAppPalette.current
    val typography = LocalAppTypography.current

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppDimensions.spacingMd)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = palette.textTertiary,
            modifier = Modifier.size(AppDimensions.iconSizeMd)
        )
        Text(
            text = label,
            style = typography.bodySmall,
            color = palette.textTertiary,
            modifier = Modifier.width(80.dp)
        )
        Text(
            text = value,
            style = typography.body.copy(fontWeight = FontWeight.Medium),
            color = palette.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun PulsingContinueButton(
    text: String,
    icon: ImageVector = Icons.AutoMirrored.Filled.ArrowForward,
    onClick: () -> Unit,
    isLoading: Boolean = false
) {
    val palette = LocalAppPalette.current
    val typography = LocalAppTypography.current

    val infiniteTransition = rememberInfiniteTransition()
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        )
    )

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        )
    )

    Button(
        onClick = onClick,
        enabled = !isLoading,
        modifier = Modifier
            .graphicsLayer {
                scaleX = pulseScale
                scaleY = pulseScale
                alpha = if (isLoading) 0.7f else pulseAlpha.coerceIn(0.85f, 1f)
            }
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(AppDimensions.buttonRadius),
                ambientColor = BrandColors.Orange.copy(alpha = 0.3f),
                spotColor = BrandColors.Orange.copy(alpha = 0.2f)
            ),
        shape = RoundedCornerShape(AppDimensions.buttonRadius),
        colors = ButtonDefaults.buttonColors(
            containerColor = BrandColors.Orange,
            contentColor = palette.textOnAccent,
            disabledContainerColor = BrandColors.Orange.copy(alpha = 0.5f),
            disabledContentColor = palette.textOnAccent.copy(alpha = 0.7f)
        ),
        contentPadding = PaddingValues(
            horizontal = AppDimensions.spacingXl,
            vertical = AppDimensions.spacingMd
        )
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(AppDimensions.iconSizeSm),
                color = palette.textOnAccent,
                strokeWidth = 2.dp
            )
            Spacer(modifier = Modifier.width(AppDimensions.spacingSm))
            Text(LocalAppStrings.current.saving, style = typography.button)
        } else {
            Text(text, style = typography.button)
            Spacer(modifier = Modifier.width(AppDimensions.spacingSm))
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(AppDimensions.iconSizeSm)
            )
        }
    }
}

// ─── Utility ────────────────────────────────────────────────────

private fun formatBytes(bytes: Long): String {
    return when {
        bytes >= 1_073_741_824 -> String.format("%.1f GB", bytes / 1_073_741_824.0)
        bytes >= 1_048_576 -> String.format("%.0f MB", bytes / 1_048_576.0)
        bytes >= 1024 -> String.format("%.0f KB", bytes / 1024.0)
        else -> "$bytes B"
    }
}

private fun isConfigValid(state: OnboardingState): Boolean {
    return when (state.selectedMode) {
        AiMode.CLOUD -> state.apiKey.isNotBlank()
        AiMode.LOCAL -> state.localModelPath.isNotBlank() || state.selectedModelId != null
    }
}

// Easing aliases for Compose Desktop compatibility
private val EaseOutCubic: Easing = CubicBezierEasing(0.33f, 1f, 0.68f, 1f)
private val EaseInCubic: Easing = CubicBezierEasing(0.32f, 0f, 0.67f, 0f)
private val EaseInOutCubic: Easing = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)
