package com.postsyncpro.presentation.screen.notifications

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.postsyncpro.presentation.i18n.LocalAppStrings
import com.postsyncpro.presentation.theme.*

@Composable
fun NotificationsScreen(viewModel: NotificationsViewModel) {
    val state by viewModel.state.collectAsState()
    val strings = LocalAppStrings.current
    val palette = LocalAppPalette.current
    val typography = LocalAppTypography.current

    val accent = BrandColors.Green

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
            .padding(AppDimensions.spacingMd),
        horizontalArrangement = Arrangement.spacedBy(AppDimensions.spacingMd)
    ) {
        // ── Left Panel: Channel Editor ──────────────────────────
        Column(
            modifier = Modifier
                .weight(0.45f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(AppDimensions.spacingMd)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = strings.notificationsTitle,
                        style = typography.heading1.copy(color = palette.textPrimary)
                    )
                    Text(
                        text = strings.notificationsSubtitle,
                        style = typography.bodySmall.copy(color = palette.textSecondary)
                    )
                }

                if (!state.isAdding && state.editingChannel == null) {
                    val addHover = remember { MutableInteractionSource() }
                    val isAddHovered by addHover.collectIsHoveredAsState()
                    Button(
                        onClick = { viewModel.startAdding() },
                        modifier = Modifier.hoverable(addHover),
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = if (isAddHovered) accent.copy(alpha = 0.85f) else accent,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(AppDimensions.buttonRadius)
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(AppDimensions.iconSizeSm)
                        )
                        Spacer(Modifier.width(AppDimensions.spacingXs))
                        Text(
                            text = strings.notificationsChannel,
                            style = typography.button.copy(color = Color.White)
                        )
                    }
                }
            }

            // ── Editor Card ─────────────────────────────────────
            AnimatedVisibility(
                visible = state.isAdding || state.editingChannel != null,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                val channel = state.editingChannel ?: NotificationChannel()

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(AppDimensions.cardRadius))
                        .background(palette.surface)
                        .border(
                            AppDimensions.borderWidth,
                            accent.copy(alpha = 0.3f),
                            RoundedCornerShape(AppDimensions.cardRadius)
                        )
                        .padding(AppDimensions.spacingLg),
                    verticalArrangement = Arrangement.spacedBy(AppDimensions.spacingMd)
                ) {
                    // Provider Tabs
                    ProviderTabs(
                        selected = channel.provider,
                        onSelect = { viewModel.updateEditing(channel.copy(provider = it)) },
                        accent = accent,
                        palette = palette,
                        typography = typography,
                        strings = strings
                    )

                    Divider(color = palette.divider)

                    // Webhook URL or Email input
                    if (channel.provider == NotificationProvider.EMAIL) {
                        InputField(
                            label = strings.notificationsEmailAddress,
                            value = channel.emailAddress,
                            placeholder = strings.notificationsEmailPlaceholder,
                            onValueChange = { viewModel.updateEditing(channel.copy(emailAddress = it)) },
                            palette = palette,
                            typography = typography,
                            accent = accent
                        )
                    } else {
                        InputField(
                            label = strings.notificationsWebhookUrl,
                            value = channel.webhookUrl,
                            placeholder = strings.notificationsWebhookPlaceholder,
                            onValueChange = { viewModel.updateEditing(channel.copy(webhookUrl = it)) },
                            palette = palette,
                            typography = typography,
                            accent = accent,
                            isSecret = true
                        )
                    }

                    Divider(color = palette.divider)

                    // Event Triggers
                    Text(
                        text = strings.notificationsOnRegression.substringBefore(" ").let { "Event Triggers" },
                        style = typography.heading3.copy(color = palette.textPrimary)
                    )

                    EventCheckbox(
                        label = strings.notificationsOnRegression,
                        checked = channel.onRegression,
                        onCheckedChange = { viewModel.updateEditing(channel.copy(onRegression = it)) },
                        accent = accent,
                        palette = palette,
                        typography = typography
                    )
                    EventCheckbox(
                        label = strings.notificationsOnPassRateDrop,
                        checked = channel.onPassRateDrop,
                        onCheckedChange = { viewModel.updateEditing(channel.copy(onPassRateDrop = it)) },
                        accent = accent,
                        palette = palette,
                        typography = typography
                    )
                    EventCheckbox(
                        label = strings.notificationsOnScheduleComplete,
                        checked = channel.onScheduleComplete,
                        onCheckedChange = { viewModel.updateEditing(channel.copy(onScheduleComplete = it)) },
                        accent = accent,
                        palette = palette,
                        typography = typography
                    )
                    EventCheckbox(
                        label = strings.notificationsOnBreakingChange,
                        checked = channel.onBreakingChange,
                        onCheckedChange = { viewModel.updateEditing(channel.copy(onBreakingChange = it)) },
                        accent = accent,
                        palette = palette,
                        typography = typography
                    )

                    Divider(color = palette.divider)

                    // Threshold Slider
                    Column(
                        verticalArrangement = Arrangement.spacedBy(AppDimensions.spacingXs)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = strings.notificationsThreshold,
                                style = typography.body.copy(color = palette.textPrimary, fontWeight = FontWeight.Medium)
                            )
                            Text(
                                text = "${channel.threshold}%",
                                style = typography.body.copy(color = accent, fontWeight = FontWeight.Bold)
                            )
                        }
                        Slider(
                            value = channel.threshold.toFloat(),
                            onValueChange = { viewModel.updateEditing(channel.copy(threshold = it.toInt())) },
                            valueRange = 0f..100f,
                            steps = 19,
                            colors = SliderDefaults.colors(
                                thumbColor = accent,
                                activeTrackColor = accent,
                                inactiveTrackColor = palette.border
                            )
                        )
                    }

                    Divider(color = palette.divider)

                    // Action Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(AppDimensions.spacingSm, Alignment.End)
                    ) {
                        val cancelHover = remember { MutableInteractionSource() }
                        val isCancelHovered by cancelHover.collectIsHoveredAsState()
                        OutlinedButton(
                            onClick = { viewModel.cancelAdding() },
                            modifier = Modifier.hoverable(cancelHover),
                            colors = ButtonDefaults.outlinedButtonColors(
                                backgroundColor = if (isCancelHovered) palette.surfaceHover else Color.Transparent,
                                contentColor = palette.textSecondary
                            ),
                            border = BorderStroke(AppDimensions.borderWidth, palette.border),
                            shape = RoundedCornerShape(AppDimensions.buttonRadius)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = null,
                                modifier = Modifier.size(AppDimensions.iconSizeSm)
                            )
                            Spacer(Modifier.width(AppDimensions.spacingXs))
                            Text("Cancel", style = typography.button)
                        }

                        val saveHover = remember { MutableInteractionSource() }
                        val isSaveHovered by saveHover.collectIsHoveredAsState()
                        Button(
                            onClick = { viewModel.saveChannel() },
                            modifier = Modifier.hoverable(saveHover),
                            colors = ButtonDefaults.buttonColors(
                                backgroundColor = if (isSaveHovered) accent.copy(alpha = 0.85f) else accent,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(AppDimensions.buttonRadius)
                        ) {
                            Icon(
                                Icons.Default.Save,
                                contentDescription = null,
                                modifier = Modifier.size(AppDimensions.iconSizeSm)
                            )
                            Spacer(Modifier.width(AppDimensions.spacingXs))
                            Text(
                                text = strings.notificationsSave,
                                style = typography.button.copy(color = Color.White)
                            )
                        }
                    }
                }
            }

            // ── Test Result Banner ──────────────────────────────
            AnimatedVisibility(
                visible = state.testResult != null,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                val isSuccess = state.testResult == "success"
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (isSuccess) StatusColors.Success.copy(alpha = 0.1f) else StatusColors.Error.copy(alpha = 0.1f),
                            RoundedCornerShape(AppDimensions.cardRadius)
                        )
                        .border(
                            AppDimensions.borderWidth,
                            if (isSuccess) StatusColors.Success.copy(alpha = 0.3f) else StatusColors.Error.copy(alpha = 0.3f),
                            RoundedCornerShape(AppDimensions.cardRadius)
                        )
                        .padding(AppDimensions.spacingMd),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppDimensions.spacingSm)
                ) {
                    Icon(
                        imageVector = if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Error,
                        contentDescription = null,
                        tint = if (isSuccess) StatusColors.Success else StatusColors.Error,
                        modifier = Modifier.size(AppDimensions.iconSizeMd)
                    )
                    Text(
                        text = if (isSuccess) strings.notificationsConnectionSuccess else strings.notificationsConnectionFailed,
                        style = typography.body.copy(
                            color = if (isSuccess) StatusColors.Success else StatusColors.Error,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
            }
        }

        // ── Right Panel: Configured Channels List ───────────────
        Column(
            modifier = Modifier
                .weight(0.55f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(AppDimensions.spacingMd)
        ) {
            // Channels header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(18.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(accent)
                )
                Spacer(Modifier.width(AppDimensions.spacingSm))
                Text(
                    text = strings.notificationsChannel,
                    style = typography.heading2.copy(color = palette.textPrimary)
                )
                Spacer(Modifier.width(AppDimensions.spacingSm))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(AppDimensions.chipRadius))
                        .background(accent.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "${state.channels.size}",
                        style = typography.caption.copy(color = accent)
                    )
                }
            }

            if (state.channels.isEmpty()) {
                // Empty state
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(AppDimensions.cardRadius))
                        .background(palette.surface)
                        .border(AppDimensions.borderWidth, palette.border, RoundedCornerShape(AppDimensions.cardRadius)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(AppDimensions.spacingSm)
                    ) {
                        Icon(
                            Icons.Default.NotificationsNone,
                            contentDescription = null,
                            tint = palette.textTertiary,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = strings.notificationsNoChannels,
                            style = typography.body.copy(color = palette.textTertiary)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(AppDimensions.spacingSm)
                ) {
                    items(state.channels, key = { it.id }) { channel ->
                        ChannelCard(
                            channel = channel,
                            isTesting = state.testingChannelId == channel.id,
                            onToggle = { viewModel.toggleChannel(channel.id) },
                            onEdit = { viewModel.updateEditing(channel); viewModel.startAdding() },
                            onDelete = { viewModel.deleteChannel(channel.id) },
                            onTest = { viewModel.testConnection(channel.id) },
                            accent = accent,
                            palette = palette,
                            typography = typography,
                            strings = strings
                        )
                    }
                }
            }
        }
    }
}

// ── Provider Tab Selector ───────────────────────────────────────
@Composable
private fun ProviderTabs(
    selected: NotificationProvider,
    onSelect: (NotificationProvider) -> Unit,
    accent: Color,
    palette: AppPalette,
    typography: AppTextStyles,
    strings: com.postsyncpro.presentation.i18n.AppStrings
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AppDimensions.spacingXs)
    ) {
        data class ProviderTab(
            val provider: NotificationProvider,
            val label: String,
            val icon: ImageVector
        )

        val tabs = listOf(
            ProviderTab(NotificationProvider.SLACK, strings.notificationsSlack, Icons.Default.Tag),
            ProviderTab(NotificationProvider.TEAMS, strings.notificationsTeams, Icons.Default.Groups),
            ProviderTab(NotificationProvider.DISCORD, strings.notificationsDiscord, Icons.Default.Forum),
            ProviderTab(NotificationProvider.EMAIL, strings.notificationsEmail, Icons.Default.Email)
        )

        tabs.forEach { tab ->
            val isSelected = selected == tab.provider
            val hoverSource = remember { MutableInteractionSource() }
            val isHovered by hoverSource.collectIsHoveredAsState()

            val bgColor = when {
                isSelected -> accent.copy(alpha = 0.15f)
                isHovered -> palette.surfaceHover
                else -> Color.Transparent
            }
            val contentColor = if (isSelected) accent else palette.textSecondary

            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(AppDimensions.buttonRadius))
                    .background(bgColor)
                    .border(
                        AppDimensions.borderWidth,
                        if (isSelected) accent.copy(alpha = 0.4f) else palette.border,
                        RoundedCornerShape(AppDimensions.buttonRadius)
                    )
                    .hoverable(hoverSource)
                    .clickable { onSelect(tab.provider) }
                    .padding(horizontal = AppDimensions.spacingSm, vertical = AppDimensions.spacingSm),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = tab.icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(AppDimensions.iconSizeSm)
                )
                Spacer(Modifier.width(AppDimensions.spacingXs))
                Text(
                    text = tab.label,
                    style = typography.bodySmall.copy(
                        color = contentColor,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ── Text Input Field ────────────────────────────────────────────
@Composable
private fun InputField(
    label: String,
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    palette: AppPalette,
    typography: AppTextStyles,
    accent: Color,
    isSecret: Boolean = false
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(AppDimensions.spacingXs)
    ) {
        Text(
            text = label,
            style = typography.label.copy(color = palette.textSecondary)
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            textStyle = typography.code.copy(color = palette.textPrimary),
            singleLine = true,
            visualTransformation = if (isSecret) PasswordVisualTransformation() else VisualTransformation.None,
            placeholder = {
                Text(
                    text = placeholder,
                    style = typography.code.copy(color = palette.textTertiary)
                )
            },
            colors = TextFieldDefaults.outlinedTextFieldColors(
                textColor = palette.textPrimary,
                backgroundColor = palette.inputBackground,
                focusedBorderColor = accent,
                unfocusedBorderColor = palette.inputBorder,
                cursorColor = accent,
                placeholderColor = palette.textTertiary
            ),
            shape = RoundedCornerShape(AppDimensions.inputRadius)
        )
    }
}

// ── Event Checkbox Row ──────────────────────────────────────────
@Composable
private fun EventCheckbox(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    accent: Color,
    palette: AppPalette,
    typography: AppTextStyles
) {
    val hoverSource = remember { MutableInteractionSource() }
    val isHovered by hoverSource.collectIsHoveredAsState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppDimensions.buttonRadius))
            .background(if (isHovered) palette.surfaceHover else Color.Transparent)
            .hoverable(hoverSource)
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = AppDimensions.spacingSm, vertical = AppDimensions.spacingXs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppDimensions.spacingSm)
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = CheckboxDefaults.colors(
                checkedColor = accent,
                uncheckedColor = palette.textTertiary,
                checkmarkColor = Color.White
            )
        )
        Text(
            text = label,
            style = typography.body.copy(color = palette.textPrimary)
        )
    }
}

// ── Channel Card ────────────────────────────────────────────────
@Composable
private fun ChannelCard(
    channel: NotificationChannel,
    isTesting: Boolean,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onTest: () -> Unit,
    accent: Color,
    palette: AppPalette,
    typography: AppTextStyles,
    strings: com.postsyncpro.presentation.i18n.AppStrings
) {
    val hoverSource = remember { MutableInteractionSource() }
    val isHovered by hoverSource.collectIsHoveredAsState()

    val providerIcon = when (channel.provider) {
        NotificationProvider.SLACK -> Icons.Default.Tag
        NotificationProvider.TEAMS -> Icons.Default.Groups
        NotificationProvider.DISCORD -> Icons.Default.Forum
        NotificationProvider.EMAIL -> Icons.Default.Email
    }

    val providerLabel = when (channel.provider) {
        NotificationProvider.SLACK -> strings.notificationsSlack
        NotificationProvider.TEAMS -> strings.notificationsTeams
        NotificationProvider.DISCORD -> strings.notificationsDiscord
        NotificationProvider.EMAIL -> strings.notificationsEmail
    }

    val target = if (channel.provider == NotificationProvider.EMAIL) {
        channel.emailAddress
    } else {
        maskWebhookTarget(channel.webhookUrl)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppDimensions.cardRadius))
            .background(if (isHovered) palette.surfaceHover else palette.surface)
            .border(
                AppDimensions.borderWidth,
                if (channel.isEnabled) accent.copy(alpha = 0.25f) else palette.border,
                RoundedCornerShape(AppDimensions.cardRadius)
            )
            .hoverable(hoverSource)
            .padding(AppDimensions.spacingMd),
        verticalArrangement = Arrangement.spacedBy(AppDimensions.spacingSm)
    ) {
        // Top row: provider + toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppDimensions.spacingSm)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(AppDimensions.buttonRadius))
                        .background(accent.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = providerIcon,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(AppDimensions.iconSizeSm)
                    )
                }
                Column {
                    Text(
                        text = providerLabel,
                        style = typography.body.copy(
                            color = palette.textPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                    Text(
                        text = target.ifEmpty { "--" },
                        style = typography.codeSmall.copy(color = palette.textTertiary),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppDimensions.spacingXs)
            ) {
                // Connection status dot
                if (channel.isConnected) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(StatusColors.Success)
                    )
                }

                // Enable/Disable badge
                Text(
                    text = if (channel.isEnabled) strings.notificationsEnabled else strings.notificationsDisabled,
                    style = typography.caption.copy(
                        color = if (channel.isEnabled) accent else palette.textTertiary,
                        fontWeight = FontWeight.Medium
                    ),
                    modifier = Modifier
                        .background(
                            if (channel.isEnabled) accent.copy(alpha = 0.1f) else palette.surfaceVariant,
                            RoundedCornerShape(AppDimensions.chipRadius)
                        )
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                )

                Switch(
                    checked = channel.isEnabled,
                    onCheckedChange = { onToggle() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = accent,
                        checkedTrackColor = accent.copy(alpha = 0.4f),
                        uncheckedThumbColor = palette.textTertiary,
                        uncheckedTrackColor = palette.border
                    )
                )
            }
        }

        // Event tags
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppDimensions.spacingXs)
        ) {
            if (channel.onRegression) EventTag("Regression", accent, palette, typography)
            if (channel.onPassRateDrop) EventTag("Pass Rate", accent, palette, typography)
            if (channel.onScheduleComplete) EventTag("Schedule", accent, palette, typography)
            if (channel.onBreakingChange) EventTag("Breaking", accent, palette, typography)
        }

        // Threshold bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppDimensions.spacingSm)
        ) {
            Text(
                text = strings.notificationsThreshold,
                style = typography.caption.copy(color = palette.textTertiary)
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(palette.border)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(channel.threshold / 100f)
                        .clip(RoundedCornerShape(2.dp))
                        .background(accent)
                )
            }
            Text(
                text = "${channel.threshold}%",
                style = typography.caption.copy(color = accent, fontWeight = FontWeight.Bold)
            )
        }

        Divider(color = palette.divider)

        // Action buttons row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppDimensions.spacingSm, Alignment.End)
        ) {
            // Test Connection
            val testHover = remember { MutableInteractionSource() }
            val isTestHovered by testHover.collectIsHoveredAsState()
            OutlinedButton(
                onClick = onTest,
                enabled = !isTesting,
                modifier = Modifier.hoverable(testHover),
                colors = ButtonDefaults.outlinedButtonColors(
                    backgroundColor = if (isTestHovered) accent.copy(alpha = 0.08f) else Color.Transparent,
                    contentColor = accent
                ),
                border = BorderStroke(AppDimensions.borderWidth, accent.copy(alpha = 0.4f)),
                shape = RoundedCornerShape(AppDimensions.buttonRadius)
            ) {
                if (isTesting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(AppDimensions.iconSizeSm),
                        color = accent,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        Icons.Default.NetworkCheck,
                        contentDescription = null,
                        modifier = Modifier.size(AppDimensions.iconSizeSm)
                    )
                }
                Spacer(Modifier.width(AppDimensions.spacingXs))
                Text(
                    text = strings.notificationsTestConnection,
                    style = typography.button.copy(color = accent)
                )
            }

            // Edit
            val editHover = remember { MutableInteractionSource() }
            val isEditHovered by editHover.collectIsHoveredAsState()
            IconButton(
                onClick = onEdit,
                modifier = Modifier
                    .size(32.dp)
                    .hoverable(editHover)
                    .clip(RoundedCornerShape(AppDimensions.buttonRadius))
                    .background(if (isEditHovered) palette.surfaceHover else Color.Transparent)
            ) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = "Edit",
                    tint = palette.textSecondary,
                    modifier = Modifier.size(AppDimensions.iconSizeSm)
                )
            }

            // Delete
            val deleteHover = remember { MutableInteractionSource() }
            val isDeleteHovered by deleteHover.collectIsHoveredAsState()
            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .size(32.dp)
                    .hoverable(deleteHover)
                    .clip(RoundedCornerShape(AppDimensions.buttonRadius))
                    .background(if (isDeleteHovered) StatusColors.Error.copy(alpha = 0.1f) else Color.Transparent)
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = if (isDeleteHovered) StatusColors.Error else palette.textTertiary,
                    modifier = Modifier.size(AppDimensions.iconSizeSm)
                )
            }
        }
    }
}

private fun maskWebhookTarget(value: String): String {
    if (value.isBlank()) return ""
    return runCatching {
        val uri = java.net.URI(value)
        val origin = listOfNotNull(uri.scheme, uri.host).joinToString("://")
        if (origin.isBlank()) "••••••••" else "$origin/••••"
    }.getOrDefault("••••••••")
}

// ── Small Event Tag ─────────────────────────────────────────────
@Composable
private fun EventTag(
    label: String,
    accent: Color,
    palette: AppPalette,
    typography: AppTextStyles
) {
    Text(
        text = label,
        style = typography.caption.copy(color = accent),
        modifier = Modifier
            .background(accent.copy(alpha = 0.1f), RoundedCornerShape(AppDimensions.chipRadius))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}
