package com.postsyncpro.presentation.screen.sync

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.postsyncpro.presentation.components.*
import com.postsyncpro.presentation.i18n.LocalAppStrings
import com.postsyncpro.presentation.theme.*
import com.postsyncpro.presentation.components.openNativeDirectoryDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// All colors now reference the theme system for proper dark/light mode support

@Composable
fun SyncScreen(viewModel: SyncViewModel) {
    val state by viewModel.state.collectAsState()
    val strings = LocalAppStrings.current
    val palette = LocalAppPalette.current
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    val canMerge = state.oldCollection != null &&
            (state.newSwagger != null || state.newCollection != null) &&
            !state.isMerging

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
            .verticalScroll(scrollState)
            .padding(AppDimensions.spacingLg),
        verticalArrangement = Arrangement.spacedBy(AppDimensions.spacingLg)
    ) {
        // --- Error Banner ---
        AnimatedVisibility(
            visible = state.error != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            state.error?.let { error ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(AppDimensions.cornerSmall),
                    color = StatusColors.Error.copy(alpha = 0.12f)
                ) {
                    Row(
                        modifier = Modifier.padding(AppDimensions.spacingMd),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AppDimensions.spacingSm)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = StatusColors.Error,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = error,
                            style = AppTypography.bodyMedium,
                            color = StatusColors.Error,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // --- Source Files Card ---
        SyncCard(title = strings.sourceFiles, icon = Icons.Default.FolderOpen) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppDimensions.spacingMd)
            ) {
                // Old Collection picker
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(AppDimensions.spacingXs)
                ) {
                    Text(
                        text = strings.oldPostmanCollection,
                        style = AppTypography.labelSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp
                        ),
                        color = palette.textSecondary
                    )
                    FilePickerButton(
                        label = strings.selectCollection,
                        fileExtensions = listOf("json"),
                        onFileSelected = { file ->
                            file?.let { viewModel.selectOldCollection(it) }
                        }
                    )

                    // Loaded file info chip
                    AnimatedVisibility(
                        visible = state.oldCollection != null,
                        enter = fadeIn() + expandVertically()
                    ) {
                        state.oldCollection?.let { collection ->
                            FileInfoChip(
                                name = collection.info.name,
                                detail = "${countEndpoints(collection.item)} endpoints",
                                icon = Icons.Default.CheckCircle,
                                color = StatusColors.Success
                            )
                        }
                    }
                }

                // New Source picker
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(AppDimensions.spacingXs)
                ) {
                    Text(
                        text = strings.newSourceSwaggerPostman,
                        style = AppTypography.labelSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp
                        ),
                        color = palette.textSecondary
                    )
                    FilePickerButton(
                        label = strings.selectSource,
                        fileExtensions = listOf("json", "yaml", "yml"),
                        onFileSelected = { file ->
                            file?.let { viewModel.selectNewSource(it) }
                        }
                    )

                    // Loaded file info chip
                    AnimatedVisibility(
                        visible = state.newCollection != null || state.newSwagger != null,
                        enter = fadeIn() + expandVertically()
                    ) {
                        val name = state.newCollection?.info?.name
                            ?: state.newSwagger?.let { "Swagger Spec" }
                            ?: ""
                        val detail = state.newSwagger?.let { "${it.paths.size} paths" }
                            ?: state.newCollection?.let { "${countEndpoints(it.item)} endpoints" }
                            ?: ""
                        if (name.isNotEmpty()) {
                            FileInfoChip(
                                name = name,
                                detail = detail,
                                icon = Icons.Default.CheckCircle,
                                color = StatusColors.Success
                            )
                        }
                    }
                }
            }

            // Parse status
            AnimatedVisibility(
                visible = state.parseStatus.isNotBlank(),
                enter = fadeIn() + expandVertically()
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = AppDimensions.spacingSm),
                    shape = RoundedCornerShape(AppDimensions.cornerSmall),
                    color = StatusColors.Success.copy(alpha = 0.08f)
                ) {
                    Row(
                        modifier = Modifier.padding(AppDimensions.spacingSm),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(AppDimensions.spacingXs)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = StatusColors.Success,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = state.parseStatus,
                            style = AppTypography.codeSmall,
                            color = StatusColors.Success.copy(alpha = 0.9f)
                        )
                    }
                }
            }

            // Loading indicator
            AnimatedVisibility(visible = state.isLoading) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = AppDimensions.spacingXs)
                        .height(2.dp)
                        .clip(RoundedCornerShape(1.dp)),
                    color = BrandColors.Orange,
                    trackColor = palette.border
                )
            }
        }

        // --- Merge Control Card ---
        SyncCard(title = strings.mergeControl, icon = Icons.Default.SyncAlt) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { viewModel.merge() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    enabled = canMerge,
                    shape = RoundedCornerShape(AppDimensions.cornerSmall),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BrandColors.Orange,
                        contentColor = Color.White,
                        disabledContainerColor = BrandColors.Orange.copy(alpha = 0.2f),
                        disabledContentColor = Color.White.copy(alpha = 0.4f)
                    )
                ) {
                    if (state.isMerging) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(AppDimensions.spacingSm))
                        Text(
                            "Merging...",
                            style = AppTypography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp
                            )
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.SyncAlt,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(AppDimensions.spacingSm))
                        Text(
                            strings.syncAndMerge,
                            style = AppTypography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp
                            )
                        )
                    }
                }

                // Disabled tooltip overlay
                if (!canMerge && !state.isMerging) {
                    Text(
                        text = strings.loadBothFiles,
                        style = AppTypography.labelSmall,
                        color = palette.textTertiary,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(top = 58.dp)
                    )
                }
            }
        }

        // --- Results Section (after merge) ---
        AnimatedVisibility(
            visible = state.mergeResult != null,
            enter = fadeIn(tween(300)) + slideInVertically(
                initialOffsetY = { it / 4 },
                animationSpec = tween(400, easing = EaseOutCubic)
            ),
            exit = fadeOut()
        ) {
            state.mergeResult?.let { result ->
                Column(
                    verticalArrangement = Arrangement.spacedBy(AppDimensions.spacingLg)
                ) {
                    // --- Stats Dashboard ---
                    SyncCard(title = strings.mergeResults, icon = Icons.Default.Assessment) {
                        // 3x2 grid of metric cards
                        Column(
                            verticalArrangement = Arrangement.spacedBy(AppDimensions.spacingSm)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(AppDimensions.spacingSm)
                            ) {
                                MetricCard(
                                    icon = Icons.Default.Api,
                                    label = strings.totalEndpoints,
                                    value = "${result.stats.totalEndpointsInOld}",
                                    accentColor = palette.textSecondary,
                                    modifier = Modifier.weight(1f)
                                )
                                MetricCard(
                                    icon = Icons.Default.Shield,
                                    label = strings.preserved,
                                    value = "${result.stats.preservedBodies}",
                                    accentColor = StatusColors.Success,
                                    modifier = Modifier.weight(1f)
                                )
                                MetricCard(
                                    icon = Icons.Default.Add,
                                    label = strings.newAdded,
                                    value = "${result.stats.newEndpointsAdded}",
                                    accentColor = BrandColors.Blue,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(AppDimensions.spacingSm)
                            ) {
                                MetricCard(
                                    icon = Icons.Default.Update,
                                    label = strings.updated,
                                    value = "${result.stats.updatedEndpoints}",
                                    accentColor = BrandColors.Orange,
                                    modifier = Modifier.weight(1f)
                                )
                                MetricCard(
                                    icon = Icons.Default.Delete,
                                    label = strings.removed,
                                    value = "${result.stats.removedEndpoints}",
                                    accentColor = StatusColors.Error,
                                    modifier = Modifier.weight(1f)
                                )
                                MetricCard(
                                    icon = Icons.Default.Code,
                                    label = strings.scriptsKept,
                                    value = "${result.stats.preservedScripts}",
                                    accentColor = BrandColors.Purple,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    // --- Merge Details Log ---
                    if (state.logs.isNotEmpty()) {
                        SyncCard(
                            title = strings.mergeDetails,
                            icon = Icons.Default.Terminal,
                            badge = "${state.logs.size} entries",
                            collapsible = true,
                            initiallyExpanded = false
                        ) {
                            LogConsole(
                                logs = state.logs,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(260.dp)
                            )
                        }
                    }

                    // --- Export Button ---
                    OutlinedButton(
                        onClick = {
                            coroutineScope.launch {
                                val directory = withContext(Dispatchers.IO) {
                                    openNativeDirectoryDialog(strings.selectExportDirectory)
                                }
                                directory?.let { viewModel.export(it) }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(AppDimensions.cornerSmall),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = BrandColors.Orange
                        ),
                        border = ButtonDefaults.outlinedButtonBorder.copy(
                            brush = Brush.linearGradient(
                                listOf(BrandColors.Orange.copy(alpha = 0.5f), BrandColors.Orange.copy(alpha = 0.5f))
                            )
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.SaveAlt,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(AppDimensions.spacingXs))
                        Text(
                            strings.exportMergedCollection,
                            style = AppTypography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                    }
                }
            }
        }

        // Bottom spacer
        Spacer(modifier = Modifier.height(AppDimensions.spacingLg))
    }
}

// --- Shared Components ---

@Composable
private fun SyncCard(
    title: String,
    icon: ImageVector,
    badge: String? = null,
    collapsible: Boolean = false,
    initiallyExpanded: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    val palette = LocalAppPalette.current
    var isExpanded by remember { mutableStateOf(initiallyExpanded) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AppDimensions.cornerMedium),
        color = palette.surface
    ) {
        Column {
            // Orange accent bar at top
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(
                        Brush.horizontalGradient(listOf(BrandColors.Orange, BrandColors.OrangeLight))
                    )
            )

            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (collapsible) Modifier.padding(
                            start = AppDimensions.spacingMd,
                            end = AppDimensions.spacingXs,
                            top = AppDimensions.spacingSm,
                            bottom = AppDimensions.spacingSm
                        ) else Modifier.padding(
                            horizontal = AppDimensions.spacingMd,
                            vertical = AppDimensions.spacingSm
                        )
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = BrandColors.Orange,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(AppDimensions.spacingSm))
                Text(
                    text = title,
                    style = AppTypography.titleMedium.copy(fontSize = 16.sp),
                    color = palette.textPrimary,
                    modifier = Modifier.weight(1f)
                )

                if (badge != null) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = palette.surfaceVariant
                    ) {
                        Text(
                            text = badge,
                            style = AppTypography.labelSmall.copy(fontSize = 10.sp),
                            color = palette.textTertiary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                if (collapsible) {
                    IconButton(
                        onClick = { isExpanded = !isExpanded },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (isExpanded) "Collapse" else "Expand",
                            tint = palette.textTertiary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            HorizontalDivider(color = palette.border, thickness = 1.dp)

            // Collapsible content
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    modifier = Modifier.padding(AppDimensions.spacingMd),
                    content = content
                )
            }
        }
    }
}

@Composable
private fun FileInfoChip(
    name: String,
    detail: String,
    icon: ImageVector,
    color: Color
) {
    val palette = LocalAppPalette.current
    Surface(
        shape = RoundedCornerShape(AppDimensions.cornerSmall),
        color = color.copy(alpha = 0.08f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppDimensions.spacingSm, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(14.dp)
            )
            Column {
                Text(
                    text = name,
                    style = AppTypography.codeSmall.copy(fontWeight = FontWeight.Medium),
                    color = palette.textPrimary
                )
                Text(
                    text = detail,
                    style = AppTypography.labelSmall.copy(fontSize = 10.sp),
                    color = color.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
private fun MetricCard(
    icon: ImageVector,
    label: String,
    value: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val palette = LocalAppPalette.current
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(AppDimensions.cornerSmall),
        color = palette.codeBg
    ) {
        Column(
            modifier = Modifier.padding(AppDimensions.spacingSm),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Icon with colored background
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(accentColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Large number
            Text(
                text = value,
                style = AppTypography.headlineLarge.copy(
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = accentColor
            )

            // Label
            Text(
                text = label,
                style = AppTypography.labelSmall.copy(fontSize = 10.sp),
                color = palette.textTertiary
            )
        }
    }
}

private fun countEndpoints(items: List<com.postsyncpro.domain.model.PostmanItem>): Int {
    return items.sumOf { item ->
        if (item.request != null) 1 else countEndpoints(item.item).toInt()
    }
}
