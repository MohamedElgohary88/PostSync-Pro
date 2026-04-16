package com.postsyncpro.presentation.screen.fuzz

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.postsyncpro.domain.model.EndpointInfo
import com.postsyncpro.domain.model.FuzzTestResult
import com.postsyncpro.domain.model.MutationType
import com.postsyncpro.presentation.components.*
import com.postsyncpro.presentation.i18n.LocalAppStrings
import com.postsyncpro.presentation.theme.*

// All colors reference the theme system

@Composable
fun FuzzScreen(viewModel: FuzzViewModel) {
    val state by viewModel.state.collectAsState()
    val strings = LocalAppStrings.current
    val palette = LocalAppPalette.current

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
            .padding(AppDimensions.spacingMd),
        horizontalArrangement = Arrangement.spacedBy(AppDimensions.spacingMd)
    ) {
        // Left Panel - Endpoint Browser (22%)
        EndpointBrowserPanel(
            state = state,
            viewModel = viewModel,
            modifier = Modifier.weight(0.22f)
        )

        // Center Panel - Configuration (48%)
        ConfigurationPanel(
            state = state,
            viewModel = viewModel,
            modifier = Modifier.weight(0.48f)
        )

        // Right Panel - Results Feed (30%)
        ResultsFeedPanel(
            state = state,
            modifier = Modifier.weight(0.30f)
        )
    }
}

// ---------------------------------------------------------------------------
// Left Panel - Endpoint Browser with multi-select
// ---------------------------------------------------------------------------

@Composable
private fun EndpointBrowserPanel(
    state: FuzzState,
    viewModel: FuzzViewModel,
    modifier: Modifier = Modifier
) {
    val palette = LocalAppPalette.current
    val strings = LocalAppStrings.current
    Surface(
        modifier = modifier.fillMaxHeight(),
        shape = RoundedCornerShape(AppDimensions.cornerMedium),
        color = palette.sidebar,
        border = BorderStroke(1.dp, palette.border)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // File picker + file name
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(AppDimensions.spacingSm),
                verticalArrangement = Arrangement.spacedBy(AppDimensions.spacingXs)
            ) {
                FilePickerButton(
                    label = if (state.swaggerFileName.isNotEmpty()) state.swaggerFileName else strings.loadSwagger,
                    fileExtensions = listOf("json", "yaml", "yml"),
                    onFileSelected = { file -> file?.let { viewModel.loadSwaggerFile(it) } }
                )
            }

            HorizontalDivider(color = palette.border, thickness = 1.dp)

            // Section header with batch toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppDimensions.spacingSm, vertical = AppDimensions.spacingXs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = strings.endpoints.uppercase(),
                        style = AppTypography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                    if (state.endpoints.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = BrandColors.Orange.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = if (state.isBatchMode) "${state.selectedEndpoints.size} selected" else "${state.endpoints.size}",
                                style = AppTypography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp
                                ),
                                color = BrandColors.Orange,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                // Multi toggle
                if (state.endpoints.isNotEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = strings.multi,
                            style = AppTypography.labelSmall.copy(fontSize = 10.sp),
                            color = if (state.isBatchMode) BrandColors.Orange else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                        Switch(
                            checked = state.isBatchMode,
                            onCheckedChange = { viewModel.toggleBatchMode() },
                            modifier = Modifier.height(20.dp),
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = BrandColors.Orange,
                                uncheckedThumbColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                uncheckedTrackColor = palette.border
                            )
                        )
                    }
                }
            }

            // Select All / Deselect All when batch mode
            if (state.isBatchMode && state.endpoints.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AppDimensions.spacingSm),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    TextButton(
                        onClick = viewModel::selectAllEndpoints,
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                        modifier = Modifier.height(24.dp)
                    ) {
                        Text(
                            strings.selectAll,
                            style = AppTypography.labelSmall.copy(fontSize = 10.sp),
                            color = BrandColors.Orange
                        )
                    }
                    TextButton(
                        onClick = viewModel::deselectAllEndpoints,
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                        modifier = Modifier.height(24.dp)
                    ) {
                        Text(
                            strings.deselectAll,
                            style = AppTypography.labelSmall.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                    }
                }
            }

            if (state.endpoints.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(AppDimensions.spacingXs)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Api,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f),
                            modifier = Modifier.size(36.dp)
                        )
                        Text(
                            text = strings.loadSwaggerToSeeEndpoints,
                            style = AppTypography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                            textAlign = TextAlign.Center,
                            lineHeight = 16.sp
                        )
                    }
                }
            } else {
                val grouped = remember(state.endpoints) {
                    state.endpoints.groupBy { ep ->
                        val segments = ep.path.trimStart('/').split('/')
                        if (segments.size > 1) "/${segments.first()}" else ep.path
                    }
                }

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(AppDimensions.spacingXxs),
                    verticalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    grouped.forEach { (prefix, eps) ->
                        item {
                            Text(
                                text = prefix,
                                style = AppTypography.labelSmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 10.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                                modifier = Modifier.padding(
                                    start = AppDimensions.spacingXs,
                                    top = AppDimensions.spacingXs,
                                    bottom = 2.dp
                                )
                            )
                        }
                        items(eps) { endpoint ->
                            FuzzEndpointItem(
                                endpoint = endpoint,
                                isSelected = if (state.isBatchMode)
                                    state.selectedEndpoints.contains(endpoint)
                                else
                                    state.selectedEndpoint == endpoint,
                                isBatchMode = state.isBatchMode,
                                onClick = { viewModel.selectEndpoint(endpoint) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FuzzEndpointItem(
    endpoint: EndpointInfo,
    isSelected: Boolean,
    isBatchMode: Boolean,
    onClick: () -> Unit
) {
    val methodColor = httpMethodColor(endpoint.method)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .then(
                if (isSelected) {
                    Modifier.background(BrandColors.Orange.copy(alpha = 0.1f))
                } else Modifier
            )
            .clickable(onClick = onClick)
            .padding(horizontal = AppDimensions.spacingXs, vertical = AppDimensions.spacingXxs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppDimensions.spacingXxs)
    ) {
        // Checkbox in batch mode
        if (isBatchMode) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onClick() },
                modifier = Modifier.size(16.dp),
                colors = CheckboxDefaults.colors(
                    checkedColor = BrandColors.Orange,
                    uncheckedColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                    checkmarkColor = Color.White
                )
            )
        }

        // Method badge
        Surface(
            shape = RoundedCornerShape(3.dp),
            color = methodColor.copy(alpha = 0.15f)
        ) {
            Text(
                text = endpoint.method.uppercase().take(3),
                style = AppTypography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 9.sp,
                    letterSpacing = 0.3.sp
                ),
                color = methodColor,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
            )
        }

        Text(
            text = endpoint.path,
            style = AppTypography.codeSmall.copy(fontSize = 11.sp),
            color = if (isSelected) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ---------------------------------------------------------------------------
// Center Panel - Configuration
// ---------------------------------------------------------------------------

@Composable
private fun ConfigurationPanel(
    state: FuzzState,
    viewModel: FuzzViewModel,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    Column(
        modifier = modifier
            .fillMaxHeight()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(AppDimensions.spacingMd)
    ) {
        // Configuration card
        val configBadge = if (state.isBatchMode && state.selectedEndpoints.size > 1) {
            "${state.selectedEndpoints.size} endpoints"
        } else {
            state.selectedEndpoint?.let { "${it.method} ${it.path}" }
        }

        SectionCard(
            title = strings.configuration,
            badge = configBadge,
            icon = Icons.Default.Settings
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(AppDimensions.spacingMd)) {
                // Base URL
                InputField(
                    label = strings.baseUrl,
                    value = state.baseUrl,
                    onValueChange = viewModel::updateBaseUrl,
                    placeholder = strings.baseUrlPlaceholder,
                    icon = Icons.Default.Language
                )

                // Auth Header
                InputField(
                    label = strings.authorizationHeader,
                    value = state.authHeader,
                    onValueChange = viewModel::updateAuthHeader,
                    placeholder = strings.authHeaderPlaceholder,
                    icon = Icons.Default.Lock
                )

                // Mutation Types
                MutationTypesSection(
                    selectedMutations = state.selectedMutations,
                    onToggleMutation = viewModel::toggleMutation
                )
            }
        }

        // Action row
        ActionRow(state = state, viewModel = viewModel)

        // Batch progress
        if (state.isRunning && state.isBatchMode && state.batchTotal > 1) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(AppDimensions.cornerSmall),
                color = LocalAppPalette.current.surface,
                border = BorderStroke(1.dp, LocalAppPalette.current.border)
            ) {
                Column(
                    modifier = Modifier.padding(AppDimensions.spacingMd),
                    verticalArrangement = Arrangement.spacedBy(AppDimensions.spacingXs)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${strings.testing}${state.currentTestingEndpoint}",
                            style = AppTypography.codeSmall,
                            color = BrandColors.Orange,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${state.batchProgress}/${state.batchTotal}",
                            style = AppTypography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                    LinearProgressIndicator(
                        progress = { state.batchProgress.toFloat() / state.batchTotal },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = BrandColors.Orange,
                        trackColor = LocalAppPalette.current.border
                    )
                }
            }
        }

        // Single-mode stats
        AnimatedVisibility(
            visible = state.results.isNotEmpty() && !state.isRunning && state.batchResults.isEmpty(),
            enter = fadeIn() + expandVertically()
        ) {
            StatsRow(results = state.results)
        }

        // Batch results dashboard
        AnimatedVisibility(
            visible = state.batchResults.isNotEmpty() && !state.isRunning,
            enter = fadeIn() + expandVertically()
        ) {
            BatchResultsDashboard(state = state)
        }

        // Error banner
        state.error?.let { error ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(AppDimensions.cornerSmall),
                color = StatusColors.Error.copy(alpha = 0.25f),
                border = BorderStroke(1.dp, StatusColors.Error.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier.padding(AppDimensions.spacingMd),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppDimensions.spacingXs)
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
}

// ---------------------------------------------------------------------------
// Batch Results Dashboard
// ---------------------------------------------------------------------------

@Composable
private fun BatchResultsDashboard(state: FuzzState) {
    val palette = LocalAppPalette.current
    val strings = LocalAppStrings.current

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AppDimensions.cornerMedium),
        color = palette.surface,
        border = BorderStroke(1.dp, palette.border)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(palette.sidebar)
                    .padding(AppDimensions.spacingMd),
                verticalArrangement = Arrangement.spacedBy(AppDimensions.spacingXs)
            ) {
                Text(
                    text = strings.batchFuzzResults,
                    style = AppTypography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${state.batchResults.size} ${strings.endpointsTested}",
                    style = AppTypography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }

            // Overall stats
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(AppDimensions.spacingMd),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Pass rate circle
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        progress = { state.overallPassRate },
                        modifier = Modifier.size(64.dp),
                        strokeWidth = 5.dp,
                        color = if (state.overallPassRate >= 0.8f) StatusColors.Success
                        else if (state.overallPassRate >= 0.5f) StatusColors.Warning
                        else StatusColors.Error,
                        trackColor = palette.border
                    )
                    Text(
                        text = "${(state.overallPassRate * 100).toInt()}%",
                        style = AppTypography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 16.sp),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    BatchStatRow(strings.totalTests, "${state.totalBatchTests}", BrandColors.Orange)
                    BatchStatRow(strings.passed, "${state.totalBatchPassed}", StatusColors.Success)
                    BatchStatRow(strings.failed, "${state.totalBatchFailed}", StatusColors.Error)
                    BatchStatRow(strings.avgTime, "${state.overallAvgTime}ms", StatusColors.Info)
                }
            }

            HorizontalDivider(color = palette.border, thickness = 1.dp)

            // Per-endpoint results
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(AppDimensions.spacingSm),
                verticalArrangement = Arrangement.spacedBy(AppDimensions.spacingXs)
            ) {
                state.batchResults.forEach { endpointResult ->
                    BatchEndpointResultCard(endpointResult = endpointResult)
                }
            }
        }
    }
}

@Composable
private fun BatchStatRow(label: String, value: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppDimensions.spacingXs)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = "$label:",
            style = AppTypography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            modifier = Modifier.width(80.dp)
        )
        Text(
            text = value,
            style = AppTypography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = color
        )
    }
}

@Composable
private fun BatchEndpointResultCard(endpointResult: EndpointFuzzResult) {
    val palette = LocalAppPalette.current
    val strings = LocalAppStrings.current
    var expanded by remember { mutableStateOf(false) }
    val methodColor = httpMethodColor(endpointResult.endpoint.method)

    val passRateColor = if (endpointResult.passRate >= 0.8f) StatusColors.Success
    else if (endpointResult.passRate >= 0.5f) StatusColors.Warning
    else StatusColors.Error

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AppDimensions.cornerSmall),
        color = palette.codeBg,
        border = BorderStroke(1.dp, palette.border)
    ) {
        Column {
            // Header row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(AppDimensions.spacingSm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppDimensions.spacingXs)
            ) {
                // Method badge
                Surface(
                    shape = RoundedCornerShape(3.dp),
                    color = methodColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = endpointResult.endpoint.method.take(3),
                        style = AppTypography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp
                        ),
                        color = methodColor,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }

                // Path
                Text(
                    text = endpointResult.endpoint.path,
                    style = AppTypography.codeSmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                // Pass rate badge
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = passRateColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "${(endpointResult.passRate * 100).toInt()}%",
                        style = AppTypography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        ),
                        color = passRateColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }

                // Stats mini
                Text(
                    text = "${endpointResult.passedTests}/${endpointResult.totalTests}",
                    style = AppTypography.labelSmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )

                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    modifier = Modifier.size(18.dp)
                )
            }

            // Expanded: show individual test results
            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.padding(
                        start = AppDimensions.spacingSm,
                        end = AppDimensions.spacingSm,
                        bottom = AppDimensions.spacingSm
                    ),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    HorizontalDivider(color = palette.border, thickness = 1.dp)
                    Spacer(modifier = Modifier.height(4.dp))

                    // Quick stats bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(AppDimensions.spacingSm)
                    ) {
                        MiniStat(strings.passed, "${endpointResult.passedTests}", StatusColors.Success)
                        MiniStat(strings.failed, "${endpointResult.failedTests}", StatusColors.Error)
                        MiniStat(strings.avg, "${endpointResult.averageResponseTimeMs}ms", StatusColors.Info)
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Individual results
                    endpointResult.results.forEach { result ->
                        MiniResultRow(result = result)
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = color.copy(alpha = 0.08f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = AppTypography.labelSmall.copy(fontSize = 9.sp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
            Text(
                text = value,
                style = AppTypography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
                color = color
            )
        }
    }
}

@Composable
private fun MiniResultRow(result: FuzzTestResult) {
    val palette = LocalAppPalette.current
    val statusColor = if (result.passed) StatusColors.Success else StatusColors.Error
    var showDetail by remember { mutableStateOf(false) }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .clickable { showDetail = !showDetail }
                .padding(horizontal = 4.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Status dot
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(statusColor)
            )

            // Mutation badge
            Surface(
                shape = RoundedCornerShape(3.dp),
                color = mutationColor(result.testCase.mutationType).copy(alpha = 0.12f)
            ) {
                Text(
                    text = result.testCase.mutationType.displayName,
                    style = AppTypography.labelSmall.copy(fontSize = 8.sp),
                    color = mutationColor(result.testCase.mutationType),
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                )
            }

            // Description
            Text(
                text = result.testCase.description,
                style = AppTypography.codeSmall.copy(fontSize = 9.sp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            // Status code
            Text(
                text = "${result.httpStatus}",
                style = AppTypography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 9.sp
                ),
                color = when {
                    result.httpStatus in 200..299 -> StatusColors.Success
                    result.httpStatus in 400..499 -> StatusColors.Warning
                    else -> StatusColors.Error
                }
            )

            // Time
            Text(
                text = "${result.responseTimeMs}ms",
                style = AppTypography.labelSmall.copy(fontSize = 9.sp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
            )
        }

        // Expandable detail
        AnimatedVisibility(visible = showDetail) {
            Column(
                modifier = Modifier.padding(start = 16.dp, top = 2.dp, bottom = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (result.testCase.payload.isNotBlank()) {
                    DetailLabel(text = "Payload")
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = palette.sidebar
                    ) {
                        Text(
                            text = result.testCase.payload.take(300),
                            style = AppTypography.codeSmall.copy(fontSize = 9.sp),
                            color = StatusColors.Success,
                            modifier = Modifier.padding(4.dp)
                        )
                    }
                }
                if (result.responseBody.isNotBlank()) {
                    DetailLabel(text = "Response")
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = palette.sidebar
                    ) {
                        Text(
                            text = result.responseBody.take(300),
                            style = AppTypography.codeSmall.copy(fontSize = 9.sp),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                            modifier = Modifier.padding(4.dp)
                        )
                    }
                }
                if (result.errorMessage.isNotBlank()) {
                    Text(
                        text = result.errorMessage,
                        style = AppTypography.codeSmall.copy(fontSize = 9.sp),
                        color = StatusColors.Error
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Input field, Mutation types, Action row, Stats row
// ---------------------------------------------------------------------------

@Composable
private fun InputField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Column(verticalArrangement = Arrangement.spacedBy(AppDimensions.spacingXxs)) {
        Text(
            text = label,
            style = AppTypography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(
                    text = placeholder,
                    style = AppTypography.codeMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                    modifier = Modifier.size(18.dp)
                )
            },
            singleLine = true,
            textStyle = AppTypography.codeMedium,
            shape = RoundedCornerShape(AppDimensions.cornerSmall),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = BrandColors.Orange,
                unfocusedBorderColor = LocalAppPalette.current.border,
                focusedContainerColor = LocalAppPalette.current.codeBg,
                unfocusedContainerColor = LocalAppPalette.current.codeBg
            )
        )
    }
}

@Composable
private fun MutationTypesSection(
    selectedMutations: Set<MutationType>,
    onToggleMutation: (MutationType) -> Unit
) {
    val strings = LocalAppStrings.current
    Column(verticalArrangement = Arrangement.spacedBy(AppDimensions.spacingXs)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Mutation Types",
                style = AppTypography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(AppDimensions.spacingXxs)) {
                TextButton(
                    onClick = {
                        MutationType.entries.forEach { mt ->
                            if (!selectedMutations.contains(mt)) onToggleMutation(mt)
                        }
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Text(
                        text = strings.selectAll,
                        style = AppTypography.labelSmall.copy(fontSize = 10.sp),
                        color = BrandColors.Orange
                    )
                }
                TextButton(
                    onClick = {
                        selectedMutations.forEach { mt -> onToggleMutation(mt) }
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Text(
                        text = strings.deselectAll,
                        style = AppTypography.labelSmall.copy(fontSize = 10.sp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                }
            }
        }

        val mutations = MutationType.entries.toList()
        val rows = mutations.chunked(2)

        rows.forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppDimensions.spacingXs)
            ) {
                rowItems.forEach { mutation ->
                    MutationCard(
                        mutation = mutation,
                        isSelected = selectedMutations.contains(mutation),
                        onToggle = { onToggleMutation(mutation) },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (rowItems.size < 2) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun MutationCard(
    mutation: MutationType,
    isSelected: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalAppPalette.current
    val borderColor = if (isSelected) BrandColors.Orange.copy(alpha = 0.5f) else palette.border
    val bgColor = if (isSelected) BrandColors.Orange.copy(alpha = 0.06f) else Color.Transparent

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(AppDimensions.cornerSmall),
        color = bgColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onToggle)
                .padding(horizontal = AppDimensions.spacingXs, vertical = AppDimensions.spacingXs),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(AppDimensions.spacingXxs)
        ) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggle() },
                modifier = Modifier.size(18.dp),
                colors = CheckboxDefaults.colors(
                    checkedColor = BrandColors.Orange,
                    uncheckedColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                    checkmarkColor = Color.White
                )
            )
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = mutationIcon(mutation),
                        style = AppTypography.labelSmall.copy(fontSize = 12.sp)
                    )
                    Text(
                        text = mutation.displayName,
                        style = AppTypography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = if (isSelected) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = mutation.description,
                    style = AppTypography.labelSmall.copy(fontSize = 9.sp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ActionRow(state: FuzzState, viewModel: FuzzViewModel) {
    val hasSelection = if (state.isBatchMode)
        state.selectedEndpoints.isNotEmpty()
    else
        state.selectedEndpoint != null

    val buttonLabel = if (state.isBatchMode && state.selectedEndpoints.size > 1) {
        "Run Tests on ${state.selectedEndpoints.size} Endpoints"
    } else {
        "Run Tests"
    }

    Column(verticalArrangement = Arrangement.spacedBy(AppDimensions.spacingXs)) {
        // Progress bar (single mode)
        if (state.isRunning && !state.isBatchMode) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                LinearProgressIndicator(
                    progress = { state.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = BrandColors.Orange,
                    trackColor = LocalAppPalette.current.border
                )
                Text(
                    text = "${state.results.size} / ${state.totalTests} tests completed",
                    style = AppTypography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        }

        // Button
        if (state.isRunning) {
            Button(
                onClick = viewModel::cancelTests,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(AppDimensions.cornerSmall),
                colors = ButtonDefaults.buttonColors(
                    containerColor = StatusColors.Error
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Stop,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(AppDimensions.spacingXs))
                Text(
                    text = "Cancel Tests",
                    style = AppTypography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White
                )
            }
        } else {
            Button(
                onClick = viewModel::generateAndRun,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                enabled = hasSelection
                        && state.baseUrl.isNotBlank()
                        && state.selectedMutations.isNotEmpty(),
                shape = RoundedCornerShape(AppDimensions.cornerSmall),
                colors = ButtonDefaults.buttonColors(
                    containerColor = BrandColors.Orange,
                    disabledContainerColor = LocalAppPalette.current.surfaceVariant
                )
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(AppDimensions.spacingXs))
                Text(
                    text = buttonLabel,
                    style = AppTypography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun StatsRow(results: List<FuzzTestResult>) {
    val passed = results.count { it.passed }
    val failed = results.size - passed
    val avgTime = if (results.isNotEmpty()) {
        results.map { it.responseTimeMs }.average().toLong()
    } else 0L

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AppDimensions.cornerSmall),
        color = LocalAppPalette.current.surface,
        border = BorderStroke(1.dp, LocalAppPalette.current.border)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(AppDimensions.spacingMd),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatItem(label = "Total", value = "${results.size}", color = BrandColors.Orange)
            StatDivider()
            StatItem(label = "Passed", value = "$passed", color = StatusColors.Success)
            StatDivider()
            StatItem(label = "Failed", value = "$failed", color = StatusColors.Error)
            StatDivider()
            StatItem(label = "Avg Time", value = "${avgTime}ms", color = StatusColors.Info)
        }
    }
}

@Composable
private fun StatItem(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = AppTypography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp
            ),
            color = color
        )
        Text(
            text = label,
            style = AppTypography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
        )
    }
}

@Composable
private fun StatDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(36.dp)
            .background(LocalAppPalette.current.border)
    )
}

// ---------------------------------------------------------------------------
// Right Panel - Results Feed
// ---------------------------------------------------------------------------

@Composable
private fun ResultsFeedPanel(
    state: FuzzState,
    modifier: Modifier = Modifier
) {
    // Show batch results in the feed if in batch mode
    val allResults = if (state.batchResults.isNotEmpty()) {
        state.batchResults.flatMap { it.results }
    } else {
        state.results
    }

    val passed = allResults.count { it.passed }
    val failed = allResults.size - passed

    val palette = LocalAppPalette.current
    Surface(
        modifier = modifier.fillMaxHeight(),
        shape = RoundedCornerShape(AppDimensions.cornerMedium),
        color = palette.surface,
        border = BorderStroke(1.dp, palette.border)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(palette.sidebar)
                    .padding(AppDimensions.spacingSm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppDimensions.spacingXs)
                ) {
                    Icon(
                        imageVector = Icons.Default.ListAlt,
                        contentDescription = null,
                        tint = BrandColors.Orange,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Test Results",
                        style = AppTypography.titleMedium.copy(fontSize = 15.sp),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                if (allResults.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        StatusBadge(
                            text = "$passed passed",
                            type = BadgeType.SUCCESS
                        )
                        StatusBadge(
                            text = "$failed failed",
                            type = BadgeType.ERROR
                        )
                    }
                }
            }

            HorizontalDivider(color = palette.border, thickness = 1.dp)

            if (allResults.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(AppDimensions.spacingSm)
                    ) {
                        Icon(
                            imageVector = Icons.Default.BugReport,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                            modifier = Modifier.size(56.dp)
                        )
                        Text(
                            text = "No test results yet",
                            style = AppTypography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        )
                        Text(
                            text = "Configure mutations and run\ntests to see results here",
                            style = AppTypography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                            textAlign = TextAlign.Center,
                            lineHeight = 16.sp
                        )
                    }
                }
            } else {
                val listState = rememberLazyListState()

                LaunchedEffect(allResults.size) {
                    if (allResults.isNotEmpty()) {
                        listState.animateScrollToItem(allResults.size - 1)
                    }
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(AppDimensions.spacingXs),
                    verticalArrangement = Arrangement.spacedBy(AppDimensions.spacingXs)
                ) {
                    items(allResults) { result ->
                        ResultCard(result = result)
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultCard(result: FuzzTestResult) {
    val palette = LocalAppPalette.current
    var expanded by remember { mutableStateOf(false) }

    val borderColor = if (result.passed) StatusColors.Success else StatusColors.Error

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AppDimensions.cornerSmall),
        color = palette.codeBg,
        border = BorderStroke(1.dp, palette.border)
    ) {
        Column(
            modifier = Modifier
                .drawBehind {
                    drawRect(
                        color = borderColor,
                        topLeft = Offset.Zero,
                        size = Size(3.dp.toPx(), size.height)
                    )
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(
                        start = AppDimensions.spacingSm + 3.dp,
                        end = AppDimensions.spacingSm,
                        top = AppDimensions.spacingXs,
                        bottom = AppDimensions.spacingXs
                    ),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Status row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(AppDimensions.spacingXxs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StatusBadge(
                            text = if (result.passed) "PASS" else "FAIL",
                            type = if (result.passed) BadgeType.SUCCESS else BadgeType.ERROR
                        )
                        MutationTypeBadge(type = result.testCase.mutationType)
                    }
                    Text(
                        text = "${result.responseTimeMs}ms",
                        style = AppTypography.labelSmall.copy(fontSize = 10.sp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                }

                // Endpoint info (useful in batch mode)
                Text(
                    text = "${result.testCase.method} ${result.testCase.endpointPath}",
                    style = AppTypography.codeSmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Test name
                Text(
                    text = result.testCase.description,
                    style = AppTypography.codeSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                    maxLines = if (expanded) Int.MAX_VALUE else 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f),
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = if (expanded) "Less" else "Details",
                        style = AppTypography.labelSmall.copy(fontSize = 9.sp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                    )
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = AppDimensions.spacingSm + 3.dp,
                            end = AppDimensions.spacingSm,
                            bottom = AppDimensions.spacingSm
                        ),
                    verticalArrangement = Arrangement.spacedBy(AppDimensions.spacingXs)
                ) {
                    HorizontalDivider(color = palette.border.copy(alpha = 0.5f))

                    DetailLabel(text = "Request Payload")
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = palette.sidebar
                    ) {
                        Text(
                            text = result.testCase.payload,
                            style = AppTypography.codeSmall.copy(fontSize = 10.sp),
                            color = StatusColors.Success,
                            modifier = Modifier.padding(AppDimensions.spacingXs)
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AppDimensions.spacingXs)
                    ) {
                        DetailLabel(text = "Response")
                        StatusBadge(
                            text = "${result.httpStatus}",
                            type = httpStatusBadgeType(result.httpStatus)
                        )
                    }

                    if (result.responseBody.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = palette.sidebar
                        ) {
                            Text(
                                text = result.responseBody.take(500),
                                style = AppTypography.codeSmall.copy(fontSize = 10.sp),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                                modifier = Modifier.padding(AppDimensions.spacingXs)
                            )
                        }
                    }

                    if (result.errorMessage.isNotBlank()) {
                        Text(
                            text = result.errorMessage,
                            style = AppTypography.codeSmall.copy(fontSize = 10.sp),
                            color = StatusColors.Error
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = AppTypography.labelSmall.copy(
            fontWeight = FontWeight.Bold,
            fontSize = 9.sp,
            letterSpacing = 1.sp
        ),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
    )
}

@Composable
private fun MutationTypeBadge(type: MutationType) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = mutationColor(type).copy(alpha = 0.12f)
    ) {
        Text(
            text = "${mutationIcon(type)} ${type.displayName}",
            style = AppTypography.labelSmall.copy(fontSize = 9.sp),
            color = mutationColor(type),
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
        )
    }
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

private fun mutationIcon(type: MutationType): String = when (type) {
    MutationType.HAPPY_PATH -> "\u2713"
    MutationType.NULL_VALUES -> "\u2205"
    MutationType.EMPTY_STRINGS -> "\u201C\u201D"
    MutationType.TYPE_MISMATCH -> "\u21C4"
    MutationType.BOUNDARY_VALUES -> "\u2195"
    MutationType.SQL_INJECTION -> "\uD83D\uDC89"
    MutationType.XSS_PAYLOADS -> "\u26A0"
    MutationType.OVERFLOW -> "\uD83D\uDCC8"
}

private fun mutationColor(type: MutationType): Color = when (type) {
    MutationType.HAPPY_PATH -> StatusColors.Success
    MutationType.NULL_VALUES -> StatusColors.Warning
    MutationType.EMPTY_STRINGS -> StatusColors.Warning
    MutationType.TYPE_MISMATCH -> StatusColors.Error
    MutationType.BOUNDARY_VALUES -> StatusColors.Info
    MutationType.SQL_INJECTION -> StatusColors.Error
    MutationType.XSS_PAYLOADS -> StatusColors.Error
    MutationType.OVERFLOW -> StatusColors.Warning
}

private fun httpMethodColor(method: String): Color = when (method.uppercase()) {
    "GET" -> HttpMethodColors.GET
    "POST" -> HttpMethodColors.POST
    "PUT" -> HttpMethodColors.PUT
    "DELETE" -> HttpMethodColors.DELETE
    "PATCH" -> HttpMethodColors.PATCH
    else -> HttpMethodColors.OPTIONS
}

private fun httpStatusBadgeType(status: Int): BadgeType = when {
    status in 200..299 -> BadgeType.SUCCESS
    status in 400..499 -> BadgeType.WARNING
    status in 500..599 -> BadgeType.ERROR
    else -> BadgeType.NEUTRAL
}
