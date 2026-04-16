package com.postsyncpro.presentation.screen.apimonitor

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.postsyncpro.presentation.i18n.LocalAppStrings
import com.postsyncpro.presentation.theme.*
import com.postsyncpro.presentation.components.SectionCard

private val TealAccent = BrandColors.Teal
private val TealAccentLight = Color(0xFF2DD4BF)
private val TealAccentDark = Color(0xFF0D9488)

@Composable
fun ApiMonitorScreen(viewModel: ApiMonitorViewModel) {
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
        // Left panel - Endpoint list + Add form
        Column(
            modifier = Modifier
                .weight(0.35f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(AppDimensions.spacingMd)
        ) {
            // Header with title and monitoring toggle
            HeaderSection(
                state = state,
                strings = strings,
                palette = palette,
                typography = typography,
                onStartMonitoring = viewModel::startMonitoring,
                onStopMonitoring = viewModel::stopMonitoring,
                onStartAdding = viewModel::startAdding
            )

            // Add endpoint form
            AnimatedVisibility(
                visible = state.isAdding,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                state.editingEndpoint?.let { editing ->
                    AddEndpointForm(
                        endpoint = editing,
                        strings = strings,
                        palette = palette,
                        typography = typography,
                        onUpdate = viewModel::updateEditing,
                        onSave = viewModel::saveEndpoint,
                        onCancel = viewModel::cancelAdding
                    )
                }
            }

            // Endpoints list
            if (state.endpoints.isEmpty()) {
                EmptyState(strings = strings, palette = palette, typography = typography)
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(AppDimensions.spacingSm)
                ) {
                    items(state.endpoints, key = { it.id }) { endpoint ->
                        EndpointCard(
                            endpoint = endpoint,
                            isSelected = state.selectedEndpointId == endpoint.id,
                            strings = strings,
                            palette = palette,
                            typography = typography,
                            onSelect = { viewModel.selectEndpoint(endpoint.id) },
                            onDelete = { viewModel.deleteEndpoint(endpoint.id) }
                        )
                    }
                }
            }
        }

        // Right panel - Details for selected endpoint
        Column(
            modifier = Modifier
                .weight(0.65f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(AppDimensions.spacingMd)
        ) {
            val selected = state.endpoints.find { it.id == state.selectedEndpointId }
            if (selected != null) {
                EndpointDetailPanel(
                    endpoint = selected,
                    strings = strings,
                    palette = palette,
                    typography = typography
                )
            } else {
                NoSelectionPlaceholder(strings = strings, palette = palette, typography = typography)
            }
        }
    }
}

// ─── Header Section ──────────────────────────────────────────────────

@Composable
private fun HeaderSection(
    state: ApiMonitorState,
    strings: com.postsyncpro.presentation.i18n.AppStrings,
    palette: AppPalette,
    typography: AppTextStyles,
    onStartMonitoring: () -> Unit,
    onStopMonitoring: () -> Unit,
    onStartAdding: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppDimensions.cardRadius))
            .background(palette.surface)
            .border(AppDimensions.borderWidth, palette.border, RoundedCornerShape(AppDimensions.cardRadius))
            .padding(AppDimensions.spacingLg),
        verticalArrangement = Arrangement.spacedBy(AppDimensions.spacingMd)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = strings.apiMonitorTitle,
                    style = typography.heading2,
                    color = palette.textPrimary
                )
                Spacer(modifier = Modifier.height(AppDimensions.spacingXs))
                Text(
                    text = strings.apiMonitorSubtitle,
                    style = typography.bodySmall,
                    color = palette.textSecondary
                )
            }

            // Monitoring status indicator
            if (state.isMonitoring) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppDimensions.spacingXs)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(StatusColors.Success)
                    )
                    Text(
                        text = strings.running,
                        style = typography.label,
                        color = StatusColors.Success
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppDimensions.spacingSm)
        ) {
            // Start/Stop monitoring button
            var monitorHover by remember { mutableStateOf(false) }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(AppDimensions.buttonRadius))
                    .background(
                        if (state.isMonitoring) {
                            if (monitorHover) StatusColors.Error.copy(alpha = 0.9f) else StatusColors.Error.copy(alpha = 0.8f)
                        } else {
                            if (monitorHover) TealAccentLight else TealAccent
                        }
                    )
                    .clickable {
                        if (state.isMonitoring) onStopMonitoring() else onStartMonitoring()
                    }
                    .hoverable(remember { androidx.compose.foundation.interaction.MutableInteractionSource() })
                    .padding(horizontal = AppDimensions.spacingLg, vertical = AppDimensions.spacingSm),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppDimensions.spacingSm)
                ) {
                    Icon(
                        imageVector = if (state.isMonitoring) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(AppDimensions.iconSizeSm)
                    )
                    Text(
                        text = if (state.isMonitoring) strings.apiMonitorStopMonitoring else strings.apiMonitorStartMonitoring,
                        style = typography.button,
                        color = Color.White
                    )
                }
            }

            // Add endpoint button
            var addHover by remember { mutableStateOf(false) }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(AppDimensions.buttonRadius))
                    .background(if (addHover) palette.surfaceHover else palette.surfaceVariant)
                    .border(AppDimensions.borderWidth, palette.border, RoundedCornerShape(AppDimensions.buttonRadius))
                    .clickable { onStartAdding() }
                    .hoverable(remember { androidx.compose.foundation.interaction.MutableInteractionSource() })
                    .padding(horizontal = AppDimensions.spacingLg, vertical = AppDimensions.spacingSm),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppDimensions.spacingSm)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = TealAccent,
                        modifier = Modifier.size(AppDimensions.iconSizeSm)
                    )
                    Text(
                        text = strings.apiMonitorAddEndpoint,
                        style = typography.button,
                        color = palette.textPrimary
                    )
                }
            }
        }
    }
}

// ─── Add Endpoint Form ───────────────────────────────────────────────

@Composable
private fun AddEndpointForm(
    endpoint: MonitoredEndpoint,
    strings: com.postsyncpro.presentation.i18n.AppStrings,
    palette: AppPalette,
    typography: AppTextStyles,
    onUpdate: (MonitoredEndpoint) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppDimensions.cardRadius))
            .background(palette.surface)
            .border(AppDimensions.borderWidth, TealAccent.copy(alpha = 0.4f), RoundedCornerShape(AppDimensions.cardRadius))
            .padding(AppDimensions.spacingLg),
        verticalArrangement = Arrangement.spacedBy(AppDimensions.spacingMd)
    ) {
        Text(
            text = strings.apiMonitorAddEndpoint,
            style = typography.heading3,
            color = TealAccent
        )

        // URL input
        OutlinedTextField(
            value = endpoint.url,
            onValueChange = { onUpdate(endpoint.copy(url = it)) },
            label = { Text(strings.apiMonitorUrl, style = typography.bodySmall) },
            placeholder = { Text(strings.apiMonitorUrlPlaceholder, style = typography.bodySmall, color = palette.textTertiary) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = TealAccent,
                unfocusedBorderColor = palette.inputBorder,
                cursorColor = TealAccent,
                focusedLabelColor = TealAccent,
                unfocusedLabelColor = palette.textSecondary,
                focusedTextColor = palette.textPrimary,
                unfocusedTextColor = palette.textPrimary
            ),
            shape = RoundedCornerShape(AppDimensions.inputRadius)
        )

        // Expected status
        OutlinedTextField(
            value = endpoint.expectedStatus.toString(),
            onValueChange = { value ->
                value.toIntOrNull()?.let { onUpdate(endpoint.copy(expectedStatus = it)) }
            },
            label = { Text(strings.apiMonitorExpectedStatus, style = typography.bodySmall) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = TealAccent,
                unfocusedBorderColor = palette.inputBorder,
                cursorColor = TealAccent,
                focusedLabelColor = TealAccent,
                unfocusedLabelColor = palette.textSecondary,
                focusedTextColor = palette.textPrimary,
                unfocusedTextColor = palette.textPrimary
            ),
            shape = RoundedCornerShape(AppDimensions.inputRadius)
        )

        // Check interval selector
        Text(
            text = strings.apiMonitorCheckInterval,
            style = typography.label,
            color = palette.textSecondary
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppDimensions.spacingXs)
        ) {
            val intervalLabels = mapOf(
                CheckInterval.EVERY_30_SEC to strings.apiMonitorEvery30Sec,
                CheckInterval.EVERY_1_MIN to strings.apiMonitorEvery1Min,
                CheckInterval.EVERY_5_MIN to strings.apiMonitorEvery5Min,
                CheckInterval.EVERY_15_MIN to strings.apiMonitorEvery15Min,
                CheckInterval.EVERY_30_MIN to strings.apiMonitorEvery30Min
            )
            CheckInterval.entries.forEach { interval ->
                val isSelected = endpoint.checkInterval == interval
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(AppDimensions.chipRadius))
                        .background(if (isSelected) TealAccent.copy(alpha = 0.2f) else palette.surfaceVariant)
                        .border(
                            AppDimensions.borderWidth,
                            if (isSelected) TealAccent else palette.border,
                            RoundedCornerShape(AppDimensions.chipRadius)
                        )
                        .clickable { onUpdate(endpoint.copy(checkInterval = interval)) }
                        .padding(horizontal = AppDimensions.spacingSm, vertical = AppDimensions.spacingXs),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = intervalLabels[interval] ?: interval.label,
                        style = typography.caption,
                        color = if (isSelected) TealAccent else palette.textSecondary
                    )
                }
            }
        }

        // Alert toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = strings.apiMonitorAlertOnDown,
                style = typography.body,
                color = palette.textPrimary
            )
            Switch(
                checked = endpoint.alertOnDown,
                onCheckedChange = { onUpdate(endpoint.copy(alertOnDown = it)) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = TealAccent,
                    uncheckedThumbColor = palette.textTertiary,
                    uncheckedTrackColor = palette.surfaceVariant
                )
            )
        }

        // Save / Cancel buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppDimensions.spacingSm, Alignment.End)
        ) {
            var cancelHover by remember { mutableStateOf(false) }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(AppDimensions.buttonRadius))
                    .background(if (cancelHover) palette.surfaceHover else palette.surfaceVariant)
                    .clickable { onCancel() }
                    .hoverable(remember { androidx.compose.foundation.interaction.MutableInteractionSource() })
                    .padding(horizontal = AppDimensions.spacingLg, vertical = AppDimensions.spacingSm),
                contentAlignment = Alignment.Center
            ) {
                Text(text = strings.cancel, style = typography.button, color = palette.textSecondary)
            }

            var saveHover by remember { mutableStateOf(false) }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(AppDimensions.buttonRadius))
                    .background(if (saveHover) TealAccentLight else TealAccent)
                    .clickable { onSave() }
                    .hoverable(remember { androidx.compose.foundation.interaction.MutableInteractionSource() })
                    .padding(horizontal = AppDimensions.spacingLg, vertical = AppDimensions.spacingSm),
                contentAlignment = Alignment.Center
            ) {
                Text(text = strings.save, style = typography.button, color = Color.White)
            }
        }
    }
}

// ─── Endpoint Card ───────────────────────────────────────────────────

@Composable
private fun EndpointCard(
    endpoint: MonitoredEndpoint,
    isSelected: Boolean,
    strings: com.postsyncpro.presentation.i18n.AppStrings,
    palette: AppPalette,
    typography: AppTextStyles,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    var isHovered by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppDimensions.cardRadius))
            .background(
                when {
                    isSelected -> TealAccent.copy(alpha = 0.08f)
                    isHovered -> palette.surfaceHover
                    else -> palette.surface
                }
            )
            .border(
                AppDimensions.borderWidth,
                if (isSelected) TealAccent.copy(alpha = 0.5f) else palette.border,
                RoundedCornerShape(AppDimensions.cardRadius)
            )
            .clickable { onSelect() }
            .hoverable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
            )
            .padding(AppDimensions.spacingMd),
        verticalArrangement = Arrangement.spacedBy(AppDimensions.spacingSm)
    ) {
        // URL + Status badge row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = endpoint.url.ifEmpty { strings.apiMonitorUrl },
                style = typography.body.copy(fontWeight = FontWeight.Medium),
                color = if (endpoint.url.isNotEmpty()) palette.textPrimary else palette.textTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(AppDimensions.spacingSm))
            StatusBadge(status = endpoint.status, strings = strings, typography = typography)
        }

        // Stats row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppDimensions.spacingLg)
        ) {
            // Uptime
            Column {
                Text(
                    text = strings.apiMonitorUptime,
                    style = typography.caption,
                    color = palette.textTertiary
                )
                Text(
                    text = "%.1f%%".format(endpoint.uptime),
                    style = typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    color = when {
                        endpoint.uptime >= 99.0 -> StatusColors.Success
                        endpoint.uptime >= 95.0 -> StatusColors.Warning
                        else -> StatusColors.Error
                    }
                )
            }

            // Avg Response Time
            Column {
                Text(
                    text = strings.apiMonitorResponseTime,
                    style = typography.caption,
                    color = palette.textTertiary
                )
                Text(
                    text = "${endpoint.avgResponseTime}${strings.ms}",
                    style = typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    color = when {
                        endpoint.avgResponseTime < 200 -> StatusColors.Success
                        endpoint.avgResponseTime < 400 -> StatusColors.Warning
                        else -> StatusColors.Error
                    }
                )
            }

            // Last Check
            if (endpoint.lastCheck != null) {
                Column {
                    Text(
                        text = strings.apiMonitorLastCheck,
                        style = typography.caption,
                        color = palette.textTertiary
                    )
                    Text(
                        text = endpoint.lastCheck,
                        style = typography.bodySmall,
                        color = palette.textSecondary
                    )
                }
            }
        }

        // Mini response time chart
        if (endpoint.responseHistory.isNotEmpty()) {
            MiniResponseChart(
                history = endpoint.responseHistory,
                palette = palette,
                modifier = Modifier.fillMaxWidth().height(24.dp)
            )
        }

        // Delete button row (visible on hover or selection)
        AnimatedVisibility(visible = isSelected || isHovered) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                var deleteHover by remember { mutableStateOf(false) }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(AppDimensions.chipRadius))
                        .background(if (deleteHover) StatusColors.Error.copy(alpha = 0.15f) else Color.Transparent)
                        .clickable { onDelete() }
                        .hoverable(remember { androidx.compose.foundation.interaction.MutableInteractionSource() })
                        .padding(horizontal = AppDimensions.spacingSm, vertical = AppDimensions.spacingXs)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AppDimensions.spacingXs)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = strings.delete,
                            tint = StatusColors.Error,
                            modifier = Modifier.size(AppDimensions.iconSizeSm)
                        )
                        Text(
                            text = strings.delete,
                            style = typography.caption,
                            color = StatusColors.Error
                        )
                    }
                }
            }
        }
    }
}

// ─── Status Badge ────────────────────────────────────────────────────

@Composable
private fun StatusBadge(
    status: HealthStatus,
    strings: com.postsyncpro.presentation.i18n.AppStrings,
    typography: AppTextStyles
) {
    val (color, label) = when (status) {
        HealthStatus.HEALTHY -> StatusColors.Success to strings.apiMonitorHealthy
        HealthStatus.DEGRADED -> StatusColors.Warning to strings.apiMonitorDegraded
        HealthStatus.DOWN -> StatusColors.Error to strings.apiMonitorDown
    }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(AppDimensions.chipRadius))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = AppDimensions.spacingSm, vertical = AppDimensions.spacingXs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppDimensions.spacingXs)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = label,
            style = typography.caption.copy(fontWeight = FontWeight.SemiBold),
            color = color
        )
    }
}

// ─── Mini Response Chart ─────────────────────────────────────────────

@Composable
private fun MiniResponseChart(
    history: List<Long>,
    palette: AppPalette,
    modifier: Modifier = Modifier
) {
    val maxVal = (history.maxOrNull() ?: 1L).coerceAtLeast(1L)

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(AppDimensions.chipRadius))
            .background(palette.codeBg)
            .padding(horizontal = AppDimensions.spacingXs, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(1.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        history.forEach { time ->
            val fraction = (time.toFloat() / maxVal).coerceIn(0f, 1f)
            val barColor = when {
                time < 200 -> StatusColors.Success
                time < 400 -> StatusColors.Warning
                else -> StatusColors.Error
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(fraction)
                    .clip(RoundedCornerShape(topStart = 1.dp, topEnd = 1.dp))
                    .background(barColor.copy(alpha = 0.7f))
            )
        }
    }
}

// ─── Endpoint Detail Panel ───────────────────────────────────────────

@Composable
private fun EndpointDetailPanel(
    endpoint: MonitoredEndpoint,
    strings: com.postsyncpro.presentation.i18n.AppStrings,
    palette: AppPalette,
    typography: AppTextStyles
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(AppDimensions.spacingMd)
    ) {
        // Overview stats cards
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppDimensions.spacingMd)
        ) {
            StatCard(
                label = strings.apiMonitorStatus,
                value = when (endpoint.status) {
                    HealthStatus.HEALTHY -> strings.apiMonitorHealthy
                    HealthStatus.DEGRADED -> strings.apiMonitorDegraded
                    HealthStatus.DOWN -> strings.apiMonitorDown
                },
                color = when (endpoint.status) {
                    HealthStatus.HEALTHY -> StatusColors.Success
                    HealthStatus.DEGRADED -> StatusColors.Warning
                    HealthStatus.DOWN -> StatusColors.Error
                },
                palette = palette,
                typography = typography,
                modifier = Modifier.weight(1f)
            )
            StatCard(
                label = strings.apiMonitorUptime,
                value = "%.1f%%".format(endpoint.uptime),
                color = when {
                    endpoint.uptime >= 99.0 -> StatusColors.Success
                    endpoint.uptime >= 95.0 -> StatusColors.Warning
                    else -> StatusColors.Error
                },
                palette = palette,
                typography = typography,
                modifier = Modifier.weight(1f)
            )
            StatCard(
                label = strings.apiMonitorResponseTime,
                value = "${endpoint.avgResponseTime}${strings.ms}",
                color = when {
                    endpoint.avgResponseTime < 200 -> StatusColors.Success
                    endpoint.avgResponseTime < 400 -> StatusColors.Warning
                    else -> StatusColors.Error
                },
                palette = palette,
                typography = typography,
                modifier = Modifier.weight(1f)
            )
            StatCard(
                label = strings.apiMonitorLastCheck,
                value = endpoint.lastCheck ?: "-",
                color = TealAccent,
                palette = palette,
                typography = typography,
                modifier = Modifier.weight(1f)
            )
        }

        // Response time history chart
        SectionCard(
            title = strings.apiMonitorResponseHistory,
            accentColor = TealAccent
        ) {
            if (endpoint.responseHistory.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = strings.apiMonitorNoEndpoints,
                        style = typography.bodySmall,
                        color = palette.textTertiary
                    )
                }
            } else {
                ResponseTimeChart(
                    history = endpoint.responseHistory,
                    palette = palette,
                    typography = typography,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                )
            }
        }

        // Endpoint details
        SectionCard(
            title = strings.details,
            accentColor = TealAccent
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(AppDimensions.spacingSm)
            ) {
                DetailRow(label = strings.apiMonitorUrl, value = endpoint.url, palette = palette, typography = typography)
                DetailRow(label = strings.apiMonitorExpectedStatus, value = endpoint.expectedStatus.toString(), palette = palette, typography = typography)
                DetailRow(label = strings.apiMonitorCheckInterval, value = endpoint.checkInterval.label, palette = palette, typography = typography)
                DetailRow(
                    label = strings.apiMonitorAlertOnDown,
                    value = if (endpoint.alertOnDown) strings.apiMonitorAlertOnDown else "-",
                    palette = palette,
                    typography = typography
                )
            }
        }

        // Incidents list
        SectionCard(
            title = strings.apiMonitorIncidents,
            badge = "${endpoint.incidents.size}",
            accentColor = if (endpoint.incidents.isNotEmpty()) StatusColors.Error else TealAccent
        ) {
            if (endpoint.incidents.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(AppDimensions.spacingMd),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = strings.apiMonitorNoIncidents,
                        style = typography.bodySmall,
                        color = palette.textTertiary,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(AppDimensions.spacingXs)
                ) {
                    endpoint.incidents.forEach { incident ->
                        IncidentRow(incident = incident, palette = palette, typography = typography)
                    }
                }
            }
        }
    }
}

// ─── Stat Card ───────────────────────────────────────────────────────

@Composable
private fun StatCard(
    label: String,
    value: String,
    color: Color,
    palette: AppPalette,
    typography: AppTextStyles,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(AppDimensions.cardRadius))
            .background(palette.surface)
            .border(AppDimensions.borderWidth, palette.border, RoundedCornerShape(AppDimensions.cardRadius))
            .padding(AppDimensions.spacingMd),
        verticalArrangement = Arrangement.spacedBy(AppDimensions.spacingXs)
    ) {
        Text(
            text = label,
            style = typography.caption,
            color = palette.textTertiary
        )
        Text(
            text = value,
            style = typography.heading3,
            color = color
        )
    }
}

// ─── Response Time Chart (larger) ────────────────────────────────────

@Composable
private fun ResponseTimeChart(
    history: List<Long>,
    palette: AppPalette,
    typography: AppTextStyles,
    modifier: Modifier = Modifier
) {
    val maxVal = (history.maxOrNull() ?: 1L).coerceAtLeast(1L)

    Column(modifier = modifier) {
        // Y-axis label
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = "${maxVal}${LocalAppStrings.current.ms}", style = typography.caption, color = palette.textTertiary)
            Text(text = "0${LocalAppStrings.current.ms}", style = typography.caption, color = palette.textTertiary)
        }

        // Chart bars
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(AppDimensions.chipRadius))
                .background(palette.codeBg)
                .padding(horizontal = AppDimensions.spacingSm, vertical = AppDimensions.spacingXs),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            history.forEach { time ->
                val fraction = (time.toFloat() / maxVal).coerceIn(0.02f, 1f)
                val barColor = when {
                    time < 200 -> StatusColors.Success
                    time < 400 -> StatusColors.Warning
                    else -> StatusColors.Error
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(fraction)
                        .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                        .background(barColor.copy(alpha = 0.8f))
                )
            }
        }
    }
}

// ─── Detail Row ──────────────────────────────────────────────────────

@Composable
private fun DetailRow(
    label: String,
    value: String,
    palette: AppPalette,
    typography: AppTextStyles
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(palette.codeBg, RoundedCornerShape(AppDimensions.chipRadius))
            .padding(horizontal = AppDimensions.spacingSm, vertical = AppDimensions.spacingXs),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = typography.bodySmall, color = palette.textSecondary)
        Text(
            text = value,
            style = typography.bodySmall.copy(fontWeight = FontWeight.Medium),
            color = palette.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ─── Incident Row ────────────────────────────────────────────────────

@Composable
private fun IncidentRow(
    incident: Incident,
    palette: AppPalette,
    typography: AppTextStyles
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppDimensions.chipRadius))
            .background(StatusColors.ErrorBackground)
            .border(AppDimensions.borderWidth, StatusColors.Error.copy(alpha = 0.2f), RoundedCornerShape(AppDimensions.chipRadius))
            .padding(AppDimensions.spacingSm),
        horizontalArrangement = Arrangement.spacedBy(AppDimensions.spacingSm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            tint = StatusColors.Error,
            modifier = Modifier.size(AppDimensions.iconSizeSm)
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(AppDimensions.spacingSm)
            ) {
                Text(
                    text = incident.timestamp,
                    style = typography.caption.copy(fontWeight = FontWeight.SemiBold),
                    color = palette.textPrimary
                )
                Text(
                    text = "${incident.statusCode}",
                    style = typography.caption,
                    color = StatusColors.Error
                )
            }
            Text(
                text = "${incident.message} (${incident.duration})",
                style = typography.caption,
                color = palette.textSecondary
            )
        }
    }
}

// ─── Empty / Placeholder States ──────────────────────────────────────

@Composable
private fun EmptyState(
    strings: com.postsyncpro.presentation.i18n.AppStrings,
    palette: AppPalette,
    typography: AppTextStyles
) {
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
                imageVector = Icons.Default.MonitorHeart,
                contentDescription = null,
                tint = palette.textTertiary,
                modifier = Modifier.size(AppDimensions.iconSizeLarge)
            )
            Text(
                text = strings.apiMonitorNoEndpoints,
                style = typography.body,
                color = palette.textTertiary,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun NoSelectionPlaceholder(
    strings: com.postsyncpro.presentation.i18n.AppStrings,
    palette: AppPalette,
    typography: AppTextStyles
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(AppDimensions.cardRadius))
            .background(palette.surface)
            .border(AppDimensions.borderWidth, palette.border, RoundedCornerShape(AppDimensions.cardRadius)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AppDimensions.spacingMd)
        ) {
            Icon(
                imageVector = Icons.Default.MonitorHeart,
                contentDescription = null,
                tint = TealAccent.copy(alpha = 0.4f),
                modifier = Modifier.size(48.dp)
            )
            Text(
                text = strings.apiMonitorSubtitle,
                style = typography.body,
                color = palette.textTertiary,
                textAlign = TextAlign.Center
            )
        }
    }
}
