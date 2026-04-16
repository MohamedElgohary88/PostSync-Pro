package com.postsyncpro.presentation.screen.issuetracker

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.postsyncpro.presentation.components.SectionCard
import com.postsyncpro.presentation.i18n.LocalAppStrings
import com.postsyncpro.presentation.theme.*

private val AccentOrange = BrandColors.OrangeDark

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
fun IssueTrackerScreen(viewModel: IssueTrackerViewModel) {
    val state by viewModel.state.collectAsState()
    val strings = LocalAppStrings.current
    val palette = LocalAppPalette.current
    val typography = LocalAppTypography.current

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
            .padding(AppDimensions.spacingMd),
        horizontalArrangement = Arrangement.spacedBy(AppDimensions.spacingMd)
    ) {
        // ── Left Panel: Provider + Connection ──
        Column(
            modifier = Modifier
                .weight(0.4f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(AppDimensions.spacingMd)
        ) {
            // Header
            Column(verticalArrangement = Arrangement.spacedBy(AppDimensions.spacingXs)) {
                Text(
                    text = strings.issueTrackerTitle,
                    style = typography.heading1.copy(color = palette.textPrimary)
                )
                Text(
                    text = strings.issueTrackerSubtitle,
                    style = typography.body.copy(color = palette.textSecondary)
                )
            }

            // Provider Selector
            SectionCard(
                title = strings.issueTrackerProvider,
                accentColor = AccentOrange
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AppDimensions.spacingSm)
                ) {
                    IssueProvider.entries.forEach { provider ->
                        val label = when (provider) {
                            IssueProvider.JIRA -> strings.issueTrackerJira
                            IssueProvider.GITHUB -> strings.issueTrackerGitHub
                            IssueProvider.LINEAR -> strings.issueTrackerLinear
                        }
                        val isSelected = state.config.provider == provider
                        ProviderCard(
                            label = label,
                            isSelected = isSelected,
                            palette = palette,
                            typography = typography,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                viewModel.updateConfig(state.config.copy(provider = provider))
                            }
                        )
                    }
                }
            }

            // Connection Form
            SectionCard(
                title = strings.issueTrackerConnect,
                accentColor = AccentOrange
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(AppDimensions.spacingMd)
                ) {
                    // Project URL
                    Column(verticalArrangement = Arrangement.spacedBy(AppDimensions.spacingXs)) {
                        Text(
                            text = strings.issueTrackerProjectUrl,
                            style = typography.label.copy(color = palette.textSecondary)
                        )
                        OutlinedTextField(
                            value = state.config.projectUrl,
                            onValueChange = {
                                viewModel.updateConfig(state.config.copy(projectUrl = it))
                            },
                            placeholder = {
                                Text(
                                    strings.issueTrackerProjectUrlPlaceholder,
                                    style = typography.bodySmall.copy(color = palette.textTertiary)
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(AppDimensions.inputRadius),
                            colors = issueTrackerTextFieldColors(palette),
                            singleLine = true,
                            enabled = !state.config.isConnected,
                            textStyle = typography.body.copy(color = palette.textPrimary)
                        )
                    }

                    // API Token
                    Column(verticalArrangement = Arrangement.spacedBy(AppDimensions.spacingXs)) {
                        Text(
                            text = strings.issueTrackerApiToken,
                            style = typography.label.copy(color = palette.textSecondary)
                        )
                        OutlinedTextField(
                            value = state.config.apiToken,
                            onValueChange = {
                                viewModel.updateConfig(state.config.copy(apiToken = it))
                            },
                            placeholder = {
                                Text(
                                    strings.issueTrackerApiTokenPlaceholder,
                                    style = typography.bodySmall.copy(color = palette.textTertiary)
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(AppDimensions.inputRadius),
                            colors = issueTrackerTextFieldColors(palette),
                            singleLine = true,
                            enabled = !state.config.isConnected,
                            textStyle = typography.body.copy(color = palette.textPrimary)
                        )
                    }

                    // Project Key
                    Column(verticalArrangement = Arrangement.spacedBy(AppDimensions.spacingXs)) {
                        Text(
                            text = strings.issueTrackerProjectKey,
                            style = typography.label.copy(color = palette.textSecondary)
                        )
                        OutlinedTextField(
                            value = state.config.projectKey,
                            onValueChange = {
                                viewModel.updateConfig(state.config.copy(projectKey = it))
                            },
                            placeholder = {
                                Text(
                                    strings.issueTrackerProjectKeyPlaceholder,
                                    style = typography.bodySmall.copy(color = palette.textTertiary)
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(AppDimensions.inputRadius),
                            colors = issueTrackerTextFieldColors(palette),
                            singleLine = true,
                            enabled = !state.config.isConnected,
                            textStyle = typography.body.copy(color = palette.textPrimary)
                        )
                    }

                    // Connection status message
                    AnimatedVisibility(visible = state.connectionMessage != null) {
                        state.connectionMessage?.let { msg ->
                            val isSuccess = state.config.isConnected
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        if (isSuccess) StatusColors.Success.copy(alpha = 0.1f)
                                        else StatusColors.Error.copy(alpha = 0.1f),
                                        RoundedCornerShape(AppDimensions.cardRadius)
                                    )
                                    .padding(AppDimensions.spacingSm),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(AppDimensions.spacingSm)
                            ) {
                                Icon(
                                    imageVector = if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Error,
                                    contentDescription = null,
                                    tint = if (isSuccess) StatusColors.Success else StatusColors.Error,
                                    modifier = Modifier.size(AppDimensions.iconSizeSm)
                                )
                                Text(
                                    text = msg,
                                    style = typography.bodySmall.copy(
                                        color = if (isSuccess) StatusColors.Success else StatusColors.Error
                                    )
                                )
                            }
                        }
                    }

                    // Connect / Disconnect button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(AppDimensions.spacingSm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (state.config.isConnected) {
                            // Status badge
                            Row(
                                modifier = Modifier
                                    .background(
                                        StatusColors.Success.copy(alpha = 0.1f),
                                        RoundedCornerShape(AppDimensions.chipRadius)
                                    )
                                    .padding(horizontal = AppDimensions.spacingSm, vertical = AppDimensions.spacingXs),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(AppDimensions.spacingXs)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(StatusColors.Success)
                                )
                                Text(
                                    text = strings.issueTrackerConnected,
                                    style = typography.label.copy(color = StatusColors.Success)
                                )
                            }

                            Spacer(Modifier.weight(1f))

                            // Disconnect button
                            var disconnectHover by remember { mutableStateOf(false) }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(AppDimensions.buttonRadius))
                                    .background(
                                        if (disconnectHover) StatusColors.Error.copy(alpha = 0.15f)
                                        else palette.surfaceVariant
                                    )
                                    .onPointerEvent(PointerEventType.Enter) { disconnectHover = true }
                                    .onPointerEvent(PointerEventType.Exit) { disconnectHover = false }
                                    .clickable { viewModel.disconnect() }
                                    .padding(horizontal = AppDimensions.spacingLg, vertical = AppDimensions.spacingSm)
                            ) {
                                Text(
                                    text = strings.issueTrackerDisconnected,
                                    style = typography.button.copy(
                                        color = if (disconnectHover) StatusColors.Error else palette.textSecondary
                                    )
                                )
                            }
                        } else {
                            Spacer(Modifier.weight(1f))

                            // Connect button
                            var connectHover by remember { mutableStateOf(false) }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(AppDimensions.buttonRadius))
                                    .background(
                                        if (state.isConnecting) AccentOrange.copy(alpha = 0.6f)
                                        else if (connectHover) AccentOrange.copy(alpha = 0.85f)
                                        else AccentOrange
                                    )
                                    .onPointerEvent(PointerEventType.Enter) { connectHover = true }
                                    .onPointerEvent(PointerEventType.Exit) { connectHover = false }
                                    .clickable(enabled = !state.isConnecting) { viewModel.connect() }
                                    .padding(horizontal = AppDimensions.spacingXl, vertical = AppDimensions.spacingSm)
                            ) {
                                if (state.isConnecting) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(AppDimensions.spacingSm),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(AppDimensions.iconSizeSm),
                                            color = Color.White,
                                            strokeWidth = 2.dp
                                        )
                                        Text(
                                            text = strings.issueTrackerConnect,
                                            style = typography.button.copy(color = Color.White)
                                        )
                                    }
                                } else {
                                    Text(
                                        text = strings.issueTrackerConnect,
                                        style = typography.button.copy(color = Color.White)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Options
            SectionCard(
                title = strings.issueTrackerTemplate,
                accentColor = AccentOrange,
                collapsible = true
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(AppDimensions.spacingSm)
                ) {
                    // Auto-create on failure toggle
                    OptionRow(
                        label = strings.issueTrackerAutoCreate,
                        checked = state.config.autoCreateOnFailure,
                        palette = palette,
                        typography = typography,
                        onCheckedChange = {
                            viewModel.updateConfig(state.config.copy(autoCreateOnFailure = it))
                        }
                    )

                    HorizontalDivider(color = palette.divider, thickness = 1.dp)

                    // Include Request
                    OptionRow(
                        label = strings.issueTrackerIncludeRequest,
                        checked = state.config.includeRequest,
                        palette = palette,
                        typography = typography,
                        onCheckedChange = {
                            viewModel.updateConfig(state.config.copy(includeRequest = it))
                        }
                    )

                    // Include Response
                    OptionRow(
                        label = strings.issueTrackerIncludeResponse,
                        checked = state.config.includeResponse,
                        palette = palette,
                        typography = typography,
                        onCheckedChange = {
                            viewModel.updateConfig(state.config.copy(includeResponse = it))
                        }
                    )

                    // Include Screenshot
                    OptionRow(
                        label = strings.issueTrackerIncludeScreenshot,
                        checked = state.config.includeScreenshot,
                        palette = palette,
                        typography = typography,
                        onCheckedChange = {
                            viewModel.updateConfig(state.config.copy(includeScreenshot = it))
                        }
                    )
                }
            }
        }

        // ── Right Panel: Recent Issues ──
        Column(
            modifier = Modifier
                .weight(0.6f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(AppDimensions.spacingMd)
        ) {
            SectionCard(
                title = strings.issueTrackerRecentIssues,
                badge = if (state.recentIssues.isNotEmpty()) "${state.recentIssues.size}" else null,
                accentColor = AccentOrange,
                modifier = Modifier.fillMaxWidth().weight(1f)
            ) {
                if (state.recentIssues.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(AppDimensions.spacingXxl),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(AppDimensions.spacingSm)
                        ) {
                            Icon(
                                imageVector = Icons.Default.BugReport,
                                contentDescription = null,
                                tint = palette.textTertiary,
                                modifier = Modifier.size(AppDimensions.iconSizeLarge)
                            )
                            Text(
                                text = strings.issueTrackerNoIssues,
                                style = typography.body.copy(color = palette.textTertiary),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(AppDimensions.spacingSm)
                    ) {
                        items(state.recentIssues, key = { it.id }) { issue ->
                            IssueRow(issue = issue, palette = palette, typography = typography)
                        }
                    }
                }
            }
        }
    }
}

// ── Provider Card ──────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
private fun ProviderCard(
    label: String,
    isSelected: Boolean,
    palette: AppPalette,
    typography: AppTextStyles,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var hovered by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(AppDimensions.cardRadius))
            .background(
                when {
                    isSelected -> AccentOrange.copy(alpha = 0.12f)
                    hovered -> palette.surfaceHover
                    else -> palette.surfaceVariant
                }
            )
            .border(
                width = if (isSelected) 1.5.dp else AppDimensions.borderWidth,
                color = if (isSelected) AccentOrange else palette.border,
                shape = RoundedCornerShape(AppDimensions.cardRadius)
            )
            .onPointerEvent(PointerEventType.Enter) { hovered = true }
            .onPointerEvent(PointerEventType.Exit) { hovered = false }
            .clickable(onClick = onClick)
            .padding(horizontal = AppDimensions.spacingMd, vertical = AppDimensions.spacingSm),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = typography.button.copy(
                color = if (isSelected) AccentOrange else palette.textSecondary,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold
            )
        )
    }
}

// ── Option Toggle Row ──────────────────────────────────────────────

@Composable
private fun OptionRow(
    label: String,
    checked: Boolean,
    palette: AppPalette,
    typography: AppTextStyles,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = AppDimensions.spacingXs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = typography.body.copy(color = palette.textPrimary),
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = AccentOrange,
                uncheckedThumbColor = palette.textTertiary,
                uncheckedTrackColor = palette.surfaceVariant,
                uncheckedBorderColor = palette.border
            )
        )
    }
}

// ── Issue Row ──────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
private fun IssueRow(
    issue: CreatedIssue,
    palette: AppPalette,
    typography: AppTextStyles
) {
    var hovered by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppDimensions.cardRadius))
            .background(if (hovered) palette.surfaceHover else palette.surfaceVariant)
            .border(
                width = AppDimensions.borderWidth,
                color = palette.borderSubtle,
                shape = RoundedCornerShape(AppDimensions.cardRadius)
            )
            .onPointerEvent(PointerEventType.Enter) { hovered = true }
            .onPointerEvent(PointerEventType.Exit) { hovered = false }
            .padding(AppDimensions.spacingMd),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppDimensions.spacingMd)
    ) {
        // Provider icon indicator
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(AppDimensions.chipRadius))
                .background(AccentOrange.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = when (issue.provider) {
                    IssueProvider.JIRA -> "J"
                    IssueProvider.GITHUB -> "G"
                    IssueProvider.LINEAR -> "L"
                },
                style = typography.button.copy(color = AccentOrange)
            )
        }

        // Issue details
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(AppDimensions.spacingXs)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(AppDimensions.spacingSm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Issue key badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(AppDimensions.chipRadius))
                        .background(AccentOrange.copy(alpha = 0.15f))
                        .padding(horizontal = AppDimensions.spacingSm, vertical = 2.dp)
                ) {
                    Text(
                        text = issue.issueKey,
                        style = typography.caption.copy(
                            color = AccentOrange,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }

                Text(
                    text = issue.title,
                    style = typography.body.copy(color = palette.textPrimary),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(AppDimensions.spacingMd),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = issue.createdAt,
                    style = typography.caption.copy(color = palette.textTertiary)
                )
                Text(
                    text = issue.url,
                    style = typography.caption.copy(color = palette.textTertiary),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Status badge
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(AppDimensions.chipRadius))
                .background(StatusColors.Success.copy(alpha = 0.1f))
                .padding(horizontal = AppDimensions.spacingSm, vertical = 2.dp)
        ) {
            Text(
                text = issue.status,
                style = typography.caption.copy(color = StatusColors.Success)
            )
        }
    }
}

// ── TextField Colors Helper ────────────────────────────────────────

@Composable
private fun issueTrackerTextFieldColors(palette: AppPalette) = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = palette.inputBackground,
    unfocusedContainerColor = palette.inputBackground,
    disabledContainerColor = palette.inputBackground.copy(alpha = 0.5f),
    focusedBorderColor = AccentOrange,
    unfocusedBorderColor = palette.inputBorder,
    disabledBorderColor = palette.inputBorder.copy(alpha = 0.5f),
    cursorColor = AccentOrange,
    focusedTextColor = palette.textPrimary,
    unfocusedTextColor = palette.textPrimary,
    disabledTextColor = palette.textTertiary
)
