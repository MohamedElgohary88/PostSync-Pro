package com.postsyncpro.presentation.screen.breakingchange

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.postsyncpro.presentation.theme.*
import com.postsyncpro.presentation.components.*
import com.postsyncpro.presentation.i18n.LocalAppStrings
import com.postsyncpro.domain.model.*

@Composable
fun BreakingChangeScreen(viewModel: BreakingChangeViewModel) {
    val state by viewModel.state.collectAsState()
    val palette = LocalAppPalette.current
    val strings = LocalAppStrings.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
            .padding(AppDimensions.spacingMd),
        verticalArrangement = Arrangement.spacedBy(AppDimensions.spacingMd)
    ) {
        // Top: File pickers side-by-side
        SectionCard(
            title = strings.versionComparison,
            badge = null,
            icon = null
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppDimensions.spacingSm)
            ) {
                // Old version picker
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(AppDimensions.spacingXs)
                ) {
                    Text(
                        text = strings.oldVersion,
                        style = AppTypography.labelSmall,
                        color = palette.textSecondary,
                        fontWeight = FontWeight.SemiBold
                    )
                    FilePickerButton(
                        label = strings.selectOldSpec,
                        fileExtensions = listOf("json", "yaml", "yml"),
                        onFileSelected = { file -> viewModel.loadOldSpec(file) }
                    )
                    if (state.oldFileName.isNotEmpty()) {
                        Text(
                            text = state.oldFileName,
                            style = AppTypography.codeSmall,
                            color = palette.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(palette.codeBg, RoundedCornerShape(4.dp))
                                .padding(AppDimensions.spacingXs)
                        )
                    }
                }

                // Arrow
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(top = 16.dp)
                ) {
                    Text(
                        text = "\u2192",
                        style = AppTypography.titleMedium,
                        color = BrandColors.Orange,
                        fontWeight = FontWeight.Bold
                    )
                }

                // New version picker
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(AppDimensions.spacingXs)
                ) {
                    Text(
                        text = strings.newVersion,
                        style = AppTypography.labelSmall,
                        color = palette.textSecondary,
                        fontWeight = FontWeight.SemiBold
                    )
                    FilePickerButton(
                        label = strings.selectNewSpec,
                        fileExtensions = listOf("json", "yaml", "yml"),
                        onFileSelected = { file -> viewModel.loadNewSpec(file) }
                    )
                    if (state.newFileName.isNotEmpty()) {
                        Text(
                            text = state.newFileName,
                            style = AppTypography.codeSmall,
                            color = palette.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(palette.codeBg, RoundedCornerShape(4.dp))
                                .padding(AppDimensions.spacingXs)
                        )
                    }
                }

                // Compare button
                Button(
                    onClick = { viewModel.compareSpecs() },
                    enabled = state.oldSpec != null && state.newSpec != null && !state.isAnalyzing,
                    modifier = Modifier
                        .padding(top = 16.dp)
                        .height(40.dp),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = BrandColors.Orange,
                        contentColor = Color.White,
                        disabledBackgroundColor = BrandColors.Orange.copy(alpha = 0.4f),
                        disabledContentColor = Color.White.copy(alpha = 0.6f)
                    ),
                    shape = RoundedCornerShape(AppDimensions.cornerMedium)
                ) {
                    if (state.isAnalyzing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(AppDimensions.spacingXs))
                    }
                    Text(
                        text = strings.compare,
                        style = AppTypography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Error display
        state.error?.let { error ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        StatusColors.Error.copy(alpha = 0.1f),
                        RoundedCornerShape(AppDimensions.cornerSmall)
                    )
                    .padding(AppDimensions.spacingSm)
            ) {
                Text(
                    text = error,
                    style = AppTypography.bodyMedium,
                    color = StatusColors.Error
                )
            }
        }

        // Diff results
        state.diffResult?.let { diff ->
            // Severity banner
            val severityColor = when (diff.severity) {
                DiffSeverity.NONE -> palette.textTertiary
                DiffSeverity.LOW -> StatusColors.Success
                DiffSeverity.MEDIUM -> StatusColors.Warning
                DiffSeverity.HIGH -> Color(0xFFFF9800)
                DiffSeverity.CRITICAL -> StatusColors.Error
            }
            val severityBgColor = severityColor.copy(alpha = 0.1f)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(severityBgColor, RoundedCornerShape(AppDimensions.cornerMedium))
                    .border(1.dp, severityColor.copy(alpha = 0.3f), RoundedCornerShape(AppDimensions.cornerMedium))
                    .padding(AppDimensions.spacingSm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppDimensions.spacingSm)
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(severityColor)
                    )
                    Text(
                        text = "Severity: ${diff.severity.name}",
                        style = AppTypography.titleMedium,
                        color = severityColor,
                        fontWeight = FontWeight.Bold
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(AppDimensions.spacingSm)
                ) {
                    StatusBadge(
                        text = "${diff.breakingChangesCount} breaking",
                        type = BadgeType.ERROR
                    )
                    StatusBadge(
                        text = "${diff.nonBreakingChangesCount} non-breaking",
                        type = BadgeType.SUCCESS
                    )
                }
            }

            // Filter row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppDimensions.spacingXs)
            ) {
                ChangeFilter.values().forEach { filter ->
                    val isSelected = state.filter == filter
                    Button(
                        onClick = { viewModel.setFilter(filter) },
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = if (isSelected) BrandColors.Orange else palette.surface,
                            contentColor = if (isSelected) Color.White else palette.textSecondary
                        ),
                        shape = RoundedCornerShape(AppDimensions.cornerSmall),
                        elevation = ButtonDefaults.elevation(
                            defaultElevation = 0.dp,
                            pressedElevation = 0.dp
                        ),
                        border = if (!isSelected) BorderStroke(1.dp, palette.border) else null,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = when (filter) {
                                ChangeFilter.ALL -> strings.all
                                ChangeFilter.BREAKING_ONLY -> strings.breakingOnly
                                ChangeFilter.NON_BREAKING -> strings.nonBreakingLabel
                            },
                            style = AppTypography.labelSmall,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }
            }

            // Results sections in a scrollable column
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(AppDimensions.spacingMd)
            ) {
                // Removed Endpoints
                if (diff.removedEndpoints.isNotEmpty() && state.filter != ChangeFilter.NON_BREAKING) {
                    item {
                        SectionCard(
                            title = strings.removedEndpoints,
                            badge = "${diff.removedEndpoints.size}",
                            icon = null
                        ) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                diff.removedEndpoints.forEach { endpoint ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(
                                                StatusColors.Error.copy(alpha = 0.06f),
                                                RoundedCornerShape(AppDimensions.cornerSmall)
                                            )
                                            .border(
                                                1.dp,
                                                StatusColors.Error.copy(alpha = 0.2f),
                                                RoundedCornerShape(AppDimensions.cornerSmall)
                                            )
                                            .padding(AppDimensions.spacingSm),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(AppDimensions.spacingSm)
                                    ) {
                                        MethodBadge(method = endpoint.method)
                                        Text(
                                            text = endpoint.path,
                                            style = AppTypography.codeMedium,
                                            color = StatusColors.Error
                                        )
                                        Spacer(modifier = Modifier.weight(1f))
                                        StatusBadge(text = strings.removedLabel.uppercase(), type = BadgeType.ERROR)
                                    }
                                }
                            }
                        }
                    }
                }

                // Added Endpoints
                if (diff.addedEndpoints.isNotEmpty() && state.filter != ChangeFilter.BREAKING_ONLY) {
                    item {
                        SectionCard(
                            title = strings.addedEndpoints,
                            badge = "${diff.addedEndpoints.size}",
                            icon = null
                        ) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                diff.addedEndpoints.forEach { endpoint ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(
                                                StatusColors.Success.copy(alpha = 0.06f),
                                                RoundedCornerShape(AppDimensions.cornerSmall)
                                            )
                                            .border(
                                                1.dp,
                                                StatusColors.Success.copy(alpha = 0.2f),
                                                RoundedCornerShape(AppDimensions.cornerSmall)
                                            )
                                            .padding(AppDimensions.spacingSm),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(AppDimensions.spacingSm)
                                    ) {
                                        MethodBadge(method = endpoint.method)
                                        Text(
                                            text = endpoint.path,
                                            style = AppTypography.codeMedium,
                                            color = StatusColors.Success
                                        )
                                        Spacer(modifier = Modifier.weight(1f))
                                        StatusBadge(text = strings.added.uppercase(), type = BadgeType.SUCCESS)
                                    }
                                }
                            }
                        }
                    }
                }

                // Modified Endpoints
                val filteredModified = diff.modifiedEndpoints.filter { endpoint ->
                    when (state.filter) {
                        ChangeFilter.ALL -> true
                        ChangeFilter.BREAKING_ONLY -> endpoint.changes.any { it.isBreaking }
                        ChangeFilter.NON_BREAKING -> endpoint.changes.any { !it.isBreaking }
                    }
                }

                if (filteredModified.isNotEmpty()) {
                    item {
                        SectionCard(
                            title = strings.modifiedEndpoints,
                            badge = "${filteredModified.size}",
                            icon = null
                        ) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(AppDimensions.spacingSm)
                            ) {
                                filteredModified.forEach { endpoint ->
                                    ModifiedEndpointCard(
                                        endpoint = endpoint,
                                        isExpanded = state.expandedEndpoints.contains("${endpoint.method} ${endpoint.path}"),
                                        onToggleExpand = { viewModel.toggleEndpoint("${endpoint.method} ${endpoint.path}") },
                                        filter = state.filter,
                                        palette = palette
                                    )
                                }
                            }
                        }
                    }
                }

                // Empty state
                if (diff.removedEndpoints.isEmpty() &&
                    diff.addedEndpoints.isEmpty() &&
                    diff.modifiedEndpoints.isEmpty()
                ) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(AppDimensions.spacingLg),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = strings.noChangesDetected,
                                    style = AppTypography.titleMedium,
                                    color = palette.textSecondary
                                )
                                Text(
                                    text = strings.specsIdentical,
                                    style = AppTypography.bodyMedium,
                                    color = palette.textTertiary
                                )
                            }
                        }
                    }
                }
            }
        }

        // Initial state when no diff yet
        if (state.diffResult == null && !state.isAnalyzing) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(AppDimensions.spacingSm)
                ) {
                    Text(
                        text = strings.selectTwoSpecs,
                        style = AppTypography.titleMedium,
                        color = palette.textSecondary
                    )
                    Text(
                        text = strings.uploadAndCompare,
                        style = AppTypography.bodyMedium,
                        color = palette.textTertiary,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // Loading state
        if (state.isAnalyzing) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(AppDimensions.spacingSm)
                ) {
                    CircularProgressIndicator(
                        color = BrandColors.Orange,
                        modifier = Modifier.size(40.dp)
                    )
                    Text(
                        text = strings.analyzingChanges,
                        style = AppTypography.bodyMedium,
                        color = palette.textSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun ModifiedEndpointCard(
    endpoint: EndpointModification,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    filter: ChangeFilter,
    palette: AppPalette
) {
    val breakingCount = endpoint.changes.count { it.isBreaking }
    val nonBreakingCount = endpoint.changes.count { !it.isBreaking }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(palette.surface, RoundedCornerShape(AppDimensions.cornerSmall))
            .border(1.dp, palette.border, RoundedCornerShape(AppDimensions.cornerSmall))
    ) {
        // Header (clickable to expand)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggleExpand() }
                .padding(AppDimensions.spacingSm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppDimensions.spacingSm)
        ) {
            Text(
                text = if (isExpanded) "\u25BC" else "\u25B6",
                style = AppTypography.labelSmall,
                color = palette.textTertiary
            )
            MethodBadge(method = endpoint.method)
            Text(
                text = endpoint.path,
                style = AppTypography.codeMedium,
                color = palette.textPrimary,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (breakingCount > 0) {
                StatusBadge(
                    text = "$breakingCount breaking",
                    type = BadgeType.ERROR
                )
            }
            if (nonBreakingCount > 0) {
                StatusBadge(
                    text = "$nonBreakingCount non-breaking",
                    type = BadgeType.SUCCESS
                )
            }
        }

        // Expanded content: changes table
        AnimatedVisibility(visible = isExpanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(palette.codeBg)
                    .padding(AppDimensions.spacingSm),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // Table header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = AppDimensions.spacingXs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val tableStrings = LocalAppStrings.current
                    Text(
                        text = tableStrings.field,
                        style = AppTypography.labelSmall,
                        color = palette.textTertiary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(0.2f)
                    )
                    Text(
                        text = tableStrings.change,
                        style = AppTypography.labelSmall,
                        color = palette.textTertiary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(0.18f)
                    )
                    Text(
                        text = tableStrings.details,
                        style = AppTypography.labelSmall,
                        color = palette.textTertiary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(0.42f)
                    )
                    Text(
                        text = tableStrings.impact,
                        style = AppTypography.labelSmall,
                        color = palette.textTertiary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(0.2f),
                        textAlign = TextAlign.End
                    )
                }

                Divider(color = palette.divider)

                val filteredChanges = endpoint.changes.filter { change ->
                    when (filter) {
                        ChangeFilter.ALL -> true
                        ChangeFilter.BREAKING_ONLY -> change.isBreaking
                        ChangeFilter.NON_BREAKING -> !change.isBreaking
                    }
                }

                filteredChanges.forEach { change ->
                    FieldChangeRow(change = change, palette = palette)
                    Divider(color = palette.divider.copy(alpha = 0.3f))
                }
            }
        }
    }
}

@Composable
private fun FieldChangeRow(change: FieldChange, palette: AppPalette) {
    val strings = LocalAppStrings.current
    val changeColor = when {
        change.changeType == ChangeType.FIELD_REMOVED ||
        change.changeType == ChangeType.ENUM_VALUE_REMOVED -> StatusColors.Error
        change.changeType == ChangeType.FIELD_ADDED -> StatusColors.Success
        else -> StatusColors.Warning
    }

    val changeLabel = when (change.changeType) {
        ChangeType.FIELD_ADDED -> strings.added
        ChangeType.FIELD_REMOVED -> strings.removedLabel
        ChangeType.TYPE_CHANGED -> strings.typeChanged
        ChangeType.REQUIRED_ADDED -> strings.nowRequired
        ChangeType.REQUIRED_REMOVED -> strings.nowOptional
        ChangeType.ENUM_VALUE_REMOVED -> strings.enumRemoved
        ChangeType.FORMAT_CHANGED -> strings.formatChanged
        ChangeType.CONSTRAINT_CHANGED -> strings.constraintChanged
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Field name
        Text(
            text = change.field,
            style = AppTypography.codeSmall,
            color = palette.textPrimary,
            modifier = Modifier.weight(0.2f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        // Change type badge
        Box(modifier = Modifier.weight(0.18f)) {
            Text(
                text = changeLabel,
                style = AppTypography.labelSmall,
                color = changeColor,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .background(changeColor.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }

        // Details: old -> new values
        Row(
            modifier = Modifier.weight(0.42f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (change.oldValue != null && change.newValue != null) {
                Text(
                    text = change.oldValue,
                    style = AppTypography.codeSmall,
                    color = StatusColors.Error,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .background(
                            StatusColors.Error.copy(alpha = 0.08f),
                            RoundedCornerShape(2.dp)
                        )
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                )
                Text(
                    text = "\u2192",
                    style = AppTypography.labelSmall,
                    color = palette.textTertiary
                )
                Text(
                    text = change.newValue,
                    style = AppTypography.codeSmall,
                    color = StatusColors.Success,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .background(
                            StatusColors.Success.copy(alpha = 0.08f),
                            RoundedCornerShape(2.dp)
                        )
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                )
            } else {
                Text(
                    text = change.description,
                    style = AppTypography.codeSmall,
                    color = palette.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Impact badge
        Box(
            modifier = Modifier.weight(0.2f),
            contentAlignment = Alignment.CenterEnd
        ) {
            StatusBadge(
                text = if (change.isBreaking) strings.breakingLabel else strings.safe,
                type = if (change.isBreaking) BadgeType.ERROR else BadgeType.SUCCESS
            )
        }
    }
}

@Composable
private fun MethodBadge(method: String) {
    val color = when (method.uppercase()) {
        "GET" -> HttpMethodColors.GET
        "POST" -> HttpMethodColors.POST
        "PUT" -> HttpMethodColors.PUT
        "DELETE" -> HttpMethodColors.DELETE
        "PATCH" -> HttpMethodColors.PATCH
        "HEAD" -> HttpMethodColors.HEAD
        "OPTIONS" -> HttpMethodColors.OPTIONS
        else -> HttpMethodColors.OPTIONS
    }

    Text(
        text = method.uppercase(),
        style = AppTypography.labelSmall,
        color = color,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}
