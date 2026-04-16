package com.postsyncpro.presentation.screen.scheduler

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.postsyncpro.presentation.i18n.LocalAppStrings
import com.postsyncpro.presentation.theme.*
import com.postsyncpro.presentation.components.*

@Composable
fun SchedulerScreen(viewModel: SchedulerViewModel) {
    val state by viewModel.state.collectAsState()
    val strings = LocalAppStrings.current
    val palette = LocalAppPalette.current
    val typography = LocalAppTypography.current

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
            .padding(AppDimensions.spacingLg),
        horizontalArrangement = Arrangement.spacedBy(AppDimensions.spacingLg)
    ) {
        // Left panel - Job list
        Column(
            modifier = Modifier
                .weight(0.45f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(AppDimensions.spacingMd)
        ) {
            // Header
            SchedulerHeader(
                strings = strings,
                palette = palette,
                typography = typography,
                onNewSchedule = { viewModel.startCreating() }
            )

            // Create form
            AnimatedVisibility(
                visible = state.isCreating,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                state.editingJob?.let { editingJob ->
                    CreateJobForm(
                        job = editingJob,
                        strings = strings,
                        palette = palette,
                        typography = typography,
                        onUpdate = { viewModel.updateEditingJob(it) },
                        onSave = { viewModel.saveJob() },
                        onCancel = { viewModel.cancelCreating() }
                    )
                }
            }

            // Job list
            if (state.jobs.isEmpty() && !state.isCreating) {
                EmptyJobsState(strings = strings, palette = palette, typography = typography)
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(AppDimensions.spacingSm)
                ) {
                    items(state.jobs, key = { it.id }) { job ->
                        JobCard(
                            job = job,
                            isSelected = state.selectedJobId == job.id,
                            isRunning = state.isRunning[job.id] == true,
                            strings = strings,
                            palette = palette,
                            typography = typography,
                            onSelect = { viewModel.selectJob(job.id) },
                            onToggleEnabled = { viewModel.toggleJobEnabled(job.id) },
                            onRunNow = { viewModel.runNow(job.id) },
                            onDelete = { viewModel.deleteJob(job.id) }
                        )
                    }
                }
            }
        }

        // Right panel - Run history
        Column(
            modifier = Modifier
                .weight(0.55f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(AppDimensions.spacingMd)
        ) {
            val selectedJob = state.jobs.find { it.id == state.selectedJobId }
            if (selectedJob != null) {
                RunHistoryPanel(
                    job = selectedJob,
                    strings = strings,
                    palette = palette,
                    typography = typography
                )
            } else {
                NoJobSelectedState(strings = strings, palette = palette, typography = typography)
            }
        }
    }
}

// ─── Header ────────────────────────────────────────────────────────

@Composable
private fun SchedulerHeader(
    strings: com.postsyncpro.presentation.i18n.AppStrings,
    palette: AppPalette,
    typography: AppTextStyles,
    onNewSchedule: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = strings.schedulerTitle,
                style = typography.heading1,
                color = palette.textPrimary
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = strings.schedulerSubtitle,
                style = typography.bodySmall,
                color = palette.textSecondary
            )
        }

        val interaction = remember { MutableInteractionSource() }
        val isHovered by interaction.collectIsHoveredAsState()

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(AppDimensions.buttonRadius))
                .background(if (isHovered) BrandColors.OrangeDark else BrandColors.Orange)
                .hoverable(interaction)
                .clickable(onClick = onNewSchedule)
                .padding(horizontal = AppDimensions.spacingMd, vertical = AppDimensions.spacingSm),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppDimensions.spacingXs)
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = null,
                    tint = palette.textOnAccent,
                    modifier = Modifier.size(AppDimensions.iconSizeSm)
                )
                Text(
                    text = strings.schedulerNewJob,
                    style = typography.button,
                    color = palette.textOnAccent
                )
            }
        }
    }
}

// ─── Create Job Form ───────────────────────────────────────────────

@Composable
private fun CreateJobForm(
    job: ScheduledJob,
    strings: com.postsyncpro.presentation.i18n.AppStrings,
    palette: AppPalette,
    typography: AppTextStyles,
    onUpdate: (ScheduledJob) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppDimensions.cardRadius))
            .background(palette.surface)
            .border(AppDimensions.borderWidth, BrandColors.Orange.copy(alpha = 0.3f), RoundedCornerShape(AppDimensions.cardRadius))
            .padding(AppDimensions.spacingLg),
        verticalArrangement = Arrangement.spacedBy(AppDimensions.spacingMd)
    ) {
        Text(
            text = strings.schedulerCreate,
            style = typography.heading3,
            color = palette.textPrimary
        )

        // Job name
        Column(verticalArrangement = Arrangement.spacedBy(AppDimensions.spacingXs)) {
            Text(
                text = strings.schedulerJobName,
                style = typography.caption,
                color = palette.textSecondary
            )
            OutlinedTextField(
                value = job.name,
                onValueChange = { onUpdate(job.copy(name = it)) },
                placeholder = {
                    Text(
                        text = strings.schedulerJobNamePlaceholder,
                        style = typography.body,
                        color = palette.textTertiary
                    )
                },
                textStyle = typography.body.copy(color = palette.textPrimary),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    backgroundColor = palette.inputBackground,
                    focusedBorderColor = BrandColors.Orange,
                    unfocusedBorderColor = palette.inputBorder,
                    cursorColor = BrandColors.Orange
                )
            )
        }

        // Test type dropdown
        Column(verticalArrangement = Arrangement.spacedBy(AppDimensions.spacingXs)) {
            Text(
                text = strings.schedulerTestType,
                style = typography.caption,
                color = palette.textSecondary
            )
            var testTypeExpanded by remember { mutableStateOf(false) }
            Box {
                val testTypeInteraction = remember { MutableInteractionSource() }
                val testTypeHovered by testTypeInteraction.collectIsHoveredAsState()
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(AppDimensions.buttonRadius))
                        .background(if (testTypeHovered) palette.surfaceHover else palette.inputBackground)
                        .border(AppDimensions.borderWidth, palette.inputBorder, RoundedCornerShape(AppDimensions.buttonRadius))
                        .hoverable(testTypeInteraction)
                        .clickable { testTypeExpanded = true }
                        .padding(horizontal = AppDimensions.spacingMd, vertical = AppDimensions.spacingSm)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = testTypeLabel(job.testType),
                            style = typography.body,
                            color = palette.textPrimary
                        )
                        Icon(
                            Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            tint = palette.textSecondary,
                            modifier = Modifier.size(AppDimensions.iconSizeMd)
                        )
                    }
                }
                DropdownMenu(
                    expanded = testTypeExpanded,
                    onDismissRequest = { testTypeExpanded = false },
                    modifier = Modifier.background(palette.surface)
                ) {
                    TestType.entries.forEach { type ->
                        DropdownMenuItem(onClick = {
                            onUpdate(job.copy(testType = type))
                            testTypeExpanded = false
                        }) {
                            Text(
                                text = testTypeLabel(type),
                                style = typography.body,
                                color = palette.textPrimary
                            )
                        }
                    }
                }
            }
        }

        // Interval dropdown
        Column(verticalArrangement = Arrangement.spacedBy(AppDimensions.spacingXs)) {
            Text(
                text = strings.schedulerInterval,
                style = typography.caption,
                color = palette.textSecondary
            )
            var intervalExpanded by remember { mutableStateOf(false) }
            Box {
                val intervalInteraction = remember { MutableInteractionSource() }
                val intervalHovered by intervalInteraction.collectIsHoveredAsState()
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(AppDimensions.buttonRadius))
                        .background(if (intervalHovered) palette.surfaceHover else palette.inputBackground)
                        .border(AppDimensions.borderWidth, palette.inputBorder, RoundedCornerShape(AppDimensions.buttonRadius))
                        .hoverable(intervalInteraction)
                        .clickable { intervalExpanded = true }
                        .padding(horizontal = AppDimensions.spacingMd, vertical = AppDimensions.spacingSm)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = intervalLabel(job.interval, strings),
                            style = typography.body,
                            color = palette.textPrimary
                        )
                        Icon(
                            Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            tint = palette.textSecondary,
                            modifier = Modifier.size(AppDimensions.iconSizeMd)
                        )
                    }
                }
                DropdownMenu(
                    expanded = intervalExpanded,
                    onDismissRequest = { intervalExpanded = false },
                    modifier = Modifier.background(palette.surface)
                ) {
                    ScheduleInterval.entries.forEach { interval ->
                        DropdownMenuItem(onClick = {
                            onUpdate(job.copy(interval = interval))
                            intervalExpanded = false
                        }) {
                            Text(
                                text = intervalLabel(interval, strings),
                                style = typography.body,
                                color = palette.textPrimary
                            )
                        }
                    }
                }
            }
        }

        // Custom cron field
        AnimatedVisibility(
            visible = job.interval == ScheduleInterval.CUSTOM_CRON,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(AppDimensions.spacingXs)) {
                Text(
                    text = strings.schedulerCronExpression,
                    style = typography.caption,
                    color = palette.textSecondary
                )
                OutlinedTextField(
                    value = job.customCron,
                    onValueChange = { onUpdate(job.copy(customCron = it)) },
                    placeholder = {
                        Text(
                            text = strings.schedulerCronPlaceholder,
                            style = typography.body,
                            color = palette.textTertiary
                        )
                    },
                    textStyle = typography.body.copy(color = palette.textPrimary),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        backgroundColor = palette.inputBackground,
                        focusedBorderColor = BrandColors.Orange,
                        unfocusedBorderColor = palette.inputBorder,
                        cursorColor = BrandColors.Orange
                    )
                )
            }
        }

        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val cancelInteraction = remember { MutableInteractionSource() }
            val cancelHovered by cancelInteraction.collectIsHoveredAsState()
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(AppDimensions.buttonRadius))
                    .background(if (cancelHovered) palette.surfaceHover else Color.Transparent)
                    .hoverable(cancelInteraction)
                    .clickable(onClick = onCancel)
                    .padding(horizontal = AppDimensions.spacingMd, vertical = AppDimensions.spacingSm),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = strings.cancel,
                    style = typography.button,
                    color = palette.textSecondary
                )
            }

            Spacer(Modifier.width(AppDimensions.spacingSm))

            val saveInteraction = remember { MutableInteractionSource() }
            val saveHovered by saveInteraction.collectIsHoveredAsState()
            val canSave = job.name.isNotBlank()
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(AppDimensions.buttonRadius))
                    .background(
                        if (!canSave) palette.textTertiary.copy(alpha = 0.3f)
                        else if (saveHovered) BrandColors.OrangeDark
                        else BrandColors.Orange
                    )
                    .hoverable(saveInteraction)
                    .clickable(enabled = canSave, onClick = onSave)
                    .padding(horizontal = AppDimensions.spacingLg, vertical = AppDimensions.spacingSm),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = strings.schedulerCreate,
                    style = typography.button,
                    color = if (canSave) palette.textOnAccent else palette.textTertiary
                )
            }
        }
    }
}

// ─── Job Card ──────────────────────────────────────────────────────

@Composable
private fun JobCard(
    job: ScheduledJob,
    isSelected: Boolean,
    isRunning: Boolean,
    strings: com.postsyncpro.presentation.i18n.AppStrings,
    palette: AppPalette,
    typography: AppTextStyles,
    onSelect: () -> Unit,
    onToggleEnabled: () -> Unit,
    onRunNow: () -> Unit,
    onDelete: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val isHovered by interaction.collectIsHoveredAsState()

    val borderColor = when {
        isSelected -> BrandColors.Orange
        isHovered -> palette.border
        else -> palette.borderSubtle
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppDimensions.cardRadius))
            .background(if (isSelected) palette.surfaceElevated else palette.surface)
            .border(AppDimensions.borderWidth, borderColor, RoundedCornerShape(AppDimensions.cardRadius))
            .hoverable(interaction)
            .clickable(onClick = onSelect)
            .padding(AppDimensions.spacingMd),
        verticalArrangement = Arrangement.spacedBy(AppDimensions.spacingSm)
    ) {
        // Top row: name + status badge
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = job.name.ifBlank { "Untitled" },
                style = typography.heading3,
                color = palette.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            Spacer(Modifier.width(AppDimensions.spacingSm))

            // Status badge
            val statusColor = if (job.isEnabled) StatusColors.Success else palette.textTertiary
            val statusText = if (job.isEnabled) strings.schedulerActive else strings.schedulerPaused
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(AppDimensions.chipRadius))
                    .background(statusColor.copy(alpha = 0.15f))
                    .padding(horizontal = AppDimensions.spacingSm, vertical = 2.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppDimensions.spacingXs)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(statusColor)
                    )
                    Text(
                        text = statusText,
                        style = typography.caption,
                        color = statusColor
                    )
                }
            }
        }

        // Info row: test type + interval
        Row(
            horizontalArrangement = Arrangement.spacedBy(AppDimensions.spacingSm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Test type chip
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(AppDimensions.chipRadius))
                    .background(BrandColors.Orange.copy(alpha = 0.1f))
                    .padding(horizontal = AppDimensions.spacingSm, vertical = 2.dp)
            ) {
                Text(
                    text = testTypeLabel(job.testType),
                    style = typography.caption,
                    color = BrandColors.Orange
                )
            }

            // Interval chip
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(AppDimensions.chipRadius))
                    .background(StatusColors.Info.copy(alpha = 0.1f))
                    .padding(horizontal = AppDimensions.spacingSm, vertical = 2.dp)
            ) {
                Text(
                    text = intervalLabel(job.interval, strings),
                    style = typography.caption,
                    color = StatusColors.Info
                )
            }
        }

        // Schedule info
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = strings.schedulerNextRun,
                    style = typography.caption,
                    color = palette.textTertiary
                )
                Text(
                    text = job.nextRun ?: "--",
                    style = typography.bodySmall,
                    color = palette.textSecondary
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = strings.schedulerLastRun,
                    style = typography.caption,
                    color = palette.textTertiary
                )
                Text(
                    text = job.lastRun ?: "--",
                    style = typography.bodySmall,
                    color = palette.textSecondary
                )
            }
        }

        // Last result
        job.lastResult?.let { result ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(AppDimensions.chipRadius))
                    .background(palette.surfaceVariant)
                    .padding(horizontal = AppDimensions.spacingSm, vertical = AppDimensions.spacingXs)
            ) {
                Text(
                    text = "Last: $result",
                    style = typography.bodySmall,
                    color = palette.textSecondary
                )
            }
        }

        // Action buttons
        Divider(color = palette.borderSubtle, thickness = AppDimensions.borderWidth)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppDimensions.spacingSm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Enable toggle
            val toggleInteraction = remember { MutableInteractionSource() }
            val toggleHovered by toggleInteraction.collectIsHoveredAsState()
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(AppDimensions.chipRadius))
                    .background(if (toggleHovered) palette.surfaceHover else Color.Transparent)
                    .hoverable(toggleInteraction)
                    .clickable(onClick = onToggleEnabled)
                    .padding(horizontal = AppDimensions.spacingSm, vertical = AppDimensions.spacingXs)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppDimensions.spacingXs)
                ) {
                    Icon(
                        if (job.isEnabled) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                        contentDescription = null,
                        tint = if (job.isEnabled) StatusColors.Success else palette.textTertiary,
                        modifier = Modifier.size(AppDimensions.iconSizeSm)
                    )
                    Text(
                        text = strings.schedulerEnabled,
                        style = typography.caption,
                        color = palette.textSecondary
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            // Run Now button
            val runInteraction = remember { MutableInteractionSource() }
            val runHovered by runInteraction.collectIsHoveredAsState()
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(AppDimensions.chipRadius))
                    .background(
                        if (isRunning) palette.surfaceVariant
                        else if (runHovered) BrandColors.Orange.copy(alpha = 0.15f)
                        else Color.Transparent
                    )
                    .hoverable(runInteraction)
                    .clickable(enabled = !isRunning, onClick = onRunNow)
                    .padding(horizontal = AppDimensions.spacingSm, vertical = AppDimensions.spacingXs)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppDimensions.spacingXs)
                ) {
                    if (isRunning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(12.dp),
                            strokeWidth = 2.dp,
                            color = BrandColors.Orange
                        )
                    } else {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = BrandColors.Orange,
                            modifier = Modifier.size(AppDimensions.iconSizeSm)
                        )
                    }
                    Text(
                        text = strings.schedulerRunNow,
                        style = typography.caption,
                        color = BrandColors.Orange
                    )
                }
            }

            // Delete button
            val deleteInteraction = remember { MutableInteractionSource() }
            val deleteHovered by deleteInteraction.collectIsHoveredAsState()
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(AppDimensions.chipRadius))
                    .background(if (deleteHovered) StatusColors.Error.copy(alpha = 0.1f) else Color.Transparent)
                    .hoverable(deleteInteraction)
                    .clickable(onClick = onDelete)
                    .padding(horizontal = AppDimensions.spacingSm, vertical = AppDimensions.spacingXs)
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = strings.schedulerDelete,
                    tint = if (deleteHovered) StatusColors.Error else palette.textTertiary,
                    modifier = Modifier.size(AppDimensions.iconSizeSm)
                )
            }
        }
    }
}

// ─── Run History Panel ─────────────────────────────────────────────

@Composable
private fun RunHistoryPanel(
    job: ScheduledJob,
    strings: com.postsyncpro.presentation.i18n.AppStrings,
    palette: AppPalette,
    typography: AppTextStyles
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(AppDimensions.cardRadius))
            .background(palette.surface)
            .border(AppDimensions.borderWidth, palette.borderSubtle, RoundedCornerShape(AppDimensions.cardRadius))
            .padding(AppDimensions.spacingLg),
        verticalArrangement = Arrangement.spacedBy(AppDimensions.spacingMd)
    ) {
        // Panel header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = strings.schedulerHistory,
                    style = typography.heading2,
                    color = palette.textPrimary
                )
                Text(
                    text = job.name,
                    style = typography.bodySmall,
                    color = palette.textSecondary
                )
            }

            // Summary badge
            if (job.runHistory.isNotEmpty()) {
                val avgPass = job.runHistory.map { it.passRate }.average()
                val badgeColor = when {
                    avgPass >= 90 -> StatusColors.Success
                    avgPass >= 70 -> StatusColors.Warning
                    else -> StatusColors.Error
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(AppDimensions.chipRadius))
                        .background(badgeColor.copy(alpha = 0.15f))
                        .padding(horizontal = AppDimensions.spacingSm, vertical = AppDimensions.spacingXs)
                ) {
                    Text(
                        text = "Avg: ${avgPass.toInt()}%",
                        style = typography.caption,
                        color = badgeColor,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Divider(color = palette.borderSubtle, thickness = AppDimensions.borderWidth)

        if (job.runHistory.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.History,
                        contentDescription = null,
                        tint = palette.textTertiary,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(Modifier.height(AppDimensions.spacingSm))
                    Text(
                        text = strings.schedulerHistory,
                        style = typography.body,
                        color = palette.textTertiary,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            // History table header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(palette.surfaceVariant)
                    .padding(horizontal = AppDimensions.spacingSm, vertical = AppDimensions.spacingXs),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Timestamp",
                    style = typography.caption,
                    color = palette.textTertiary,
                    modifier = Modifier.weight(0.3f)
                )
                Text(
                    text = "Pass Rate",
                    style = typography.caption,
                    color = palette.textTertiary,
                    modifier = Modifier.weight(0.2f),
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Tests",
                    style = typography.caption,
                    color = palette.textTertiary,
                    modifier = Modifier.weight(0.2f),
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Duration",
                    style = typography.caption,
                    color = palette.textTertiary,
                    modifier = Modifier.weight(0.15f),
                    textAlign = TextAlign.End
                )
                Text(
                    text = "Status",
                    style = typography.caption,
                    color = palette.textTertiary,
                    modifier = Modifier.weight(0.15f),
                    textAlign = TextAlign.End
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(job.runHistory) { result ->
                    RunHistoryRow(
                        result = result,
                        palette = palette,
                        typography = typography
                    )
                }
            }
        }
    }
}

@Composable
private fun RunHistoryRow(
    result: RunResult,
    palette: AppPalette,
    typography: AppTextStyles
) {
    val interaction = remember { MutableInteractionSource() }
    val isHovered by interaction.collectIsHoveredAsState()

    val passColor = when {
        result.passRate >= 90 -> StatusColors.Success
        result.passRate >= 70 -> StatusColors.Warning
        else -> StatusColors.Error
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppDimensions.chipRadius))
            .background(if (isHovered) palette.surfaceHover else Color.Transparent)
            .hoverable(interaction)
            .padding(horizontal = AppDimensions.spacingSm, vertical = AppDimensions.spacingXs),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = result.timestamp,
            style = typography.bodySmall,
            color = palette.textSecondary,
            modifier = Modifier.weight(0.3f)
        )

        // Pass rate with color
        Box(
            modifier = Modifier.weight(0.2f),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(AppDimensions.chipRadius))
                    .background(passColor.copy(alpha = 0.1f))
                    .padding(horizontal = AppDimensions.spacingSm, vertical = 2.dp)
            ) {
                Text(
                    text = "${result.passRate.toInt()}%",
                    style = typography.caption,
                    color = passColor,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Text(
            text = "${result.passed}/${result.totalTests}",
            style = typography.bodySmall,
            color = palette.textSecondary,
            modifier = Modifier.weight(0.2f),
            textAlign = TextAlign.Center
        )

        Text(
            text = "${result.duration}ms",
            style = typography.bodySmall,
            color = palette.textTertiary,
            modifier = Modifier.weight(0.15f),
            textAlign = TextAlign.End
        )

        // Status indicator
        Box(
            modifier = Modifier.weight(0.15f),
            contentAlignment = Alignment.CenterEnd
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(passColor)
            )
        }
    }
}

// ─── Empty States ──────────────────────────────────────────────────

@Composable
private fun EmptyJobsState(
    strings: com.postsyncpro.presentation.i18n.AppStrings,
    palette: AppPalette,
    typography: AppTextStyles
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppDimensions.cardRadius))
            .background(palette.surface)
            .border(AppDimensions.borderWidth, palette.borderSubtle, RoundedCornerShape(AppDimensions.cardRadius))
            .padding(AppDimensions.spacingXl),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AppDimensions.spacingSm)
        ) {
            Icon(
                Icons.Default.Schedule,
                contentDescription = null,
                tint = palette.textTertiary,
                modifier = Modifier.size(48.dp)
            )
            Text(
                text = strings.schedulerNoJobs,
                style = typography.body,
                color = palette.textTertiary,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun NoJobSelectedState(
    strings: com.postsyncpro.presentation.i18n.AppStrings,
    palette: AppPalette,
    typography: AppTextStyles
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(AppDimensions.cardRadius))
            .background(palette.surface)
            .border(AppDimensions.borderWidth, palette.borderSubtle, RoundedCornerShape(AppDimensions.cardRadius))
            .padding(AppDimensions.spacingXl),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AppDimensions.spacingSm)
        ) {
            Icon(
                Icons.Default.History,
                contentDescription = null,
                tint = palette.textTertiary,
                modifier = Modifier.size(48.dp)
            )
            Text(
                text = strings.schedulerHistory,
                style = typography.heading3,
                color = palette.textTertiary
            )
            Text(
                text = "Select a job to view run history",
                style = typography.bodySmall,
                color = palette.textTertiary,
                textAlign = TextAlign.Center
            )
        }
    }
}

// ─── Helpers ───────────────────────────────────────────────────────

private fun testTypeLabel(type: TestType): String = when (type) {
    TestType.REGRESSION -> "Regression"
    TestType.FUZZ -> "Fuzz"
    TestType.CHAIN -> "Chain"
    TestType.COVERAGE -> "Coverage"
}

private fun intervalLabel(
    interval: ScheduleInterval,
    strings: com.postsyncpro.presentation.i18n.AppStrings
): String = when (interval) {
    ScheduleInterval.EVERY_HOUR -> strings.schedulerEveryHour
    ScheduleInterval.EVERY_6_HOURS -> strings.schedulerEvery6Hours
    ScheduleInterval.EVERY_12_HOURS -> strings.schedulerEvery12Hours
    ScheduleInterval.DAILY -> strings.schedulerDaily
    ScheduleInterval.WEEKLY -> strings.schedulerWeekly
    ScheduleInterval.CUSTOM_CRON -> strings.schedulerCustomCron
}
