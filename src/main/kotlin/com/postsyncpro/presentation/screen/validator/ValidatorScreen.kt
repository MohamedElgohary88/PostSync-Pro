package com.postsyncpro.presentation.screen.validator

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.postsyncpro.domain.model.*
import com.postsyncpro.presentation.components.*
import com.postsyncpro.presentation.i18n.LocalAppStrings
import com.postsyncpro.presentation.theme.*

@Composable
fun ValidatorScreen(viewModel: ValidatorViewModel) {
    val state by viewModel.state.collectAsState()
    val palette = LocalAppPalette.current
    val strings = LocalAppStrings.current

    Box(modifier = Modifier.fillMaxSize().background(palette.background)) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(AppDimensions.spacingMd),
            horizontalArrangement = Arrangement.spacedBy(AppDimensions.spacingMd)
        ) {
            // Left Panel (55%)
            LeftPanel(
                state = state,
                viewModel = viewModel,
                modifier = Modifier.weight(0.55f)
            )

            // Right Panel (45%)
            RightPanel(
                state = state,
                modifier = Modifier.weight(0.45f)
            )
        }

        // Loading overlay
        if (state.isAnalyzing) {
            AnalyzingOverlay(state = state)
        }
    }
}

// ---------------------------------------------------------------------------
// Left Panel
// ---------------------------------------------------------------------------

@Composable
private fun LeftPanel(
    state: ValidatorState,
    viewModel: ValidatorViewModel,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(AppDimensions.spacingMd)
    ) {
        // SRS Input
        SrsInputCard(state = state, onSrsTextChanged = viewModel::updateSrsText)

        // API Specification
        ApiSpecCard(
            state = state,
            onFileLoaded = viewModel::loadSwaggerFile,
            onEndpointSelected = viewModel::selectEndpoint,
            onEndpointToggled = viewModel::toggleEndpoint,
            onSelectAll = viewModel::selectAllEndpoints,
            onDeselectAll = viewModel::deselectAllEndpoints,
            onToggleBatchMode = viewModel::toggleBatchMode
        )

        // Analyze button row
        AnalyzeActionRow(state = state, onAnalyze = viewModel::analyze)

        // Error banner
        state.error?.let { error -> ErrorBanner(error) }
    }
}

@Composable
private fun SrsInputCard(
    state: ValidatorState,
    onSrsTextChanged: (String) -> Unit
) {
    val strings = LocalAppStrings.current
    SectionCard(
        title = strings.srsRequirementsInput,
        icon = Icons.Default.Description
    ) {
        Box {
            OutlinedTextField(
                value = state.srsText,
                onValueChange = onSrsTextChanged,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 250.dp),
                placeholder = {
                    Text(
                        text = strings.pasteSrsHere,
                        style = AppTypography.codeMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    )
                },
                textStyle = AppTypography.codeMedium.copy(
                    fontFamily = FontFamily.Monospace
                ),
                shape = RoundedCornerShape(AppDimensions.cornerSmall),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = LocalAppPalette.current.border,
                    focusedContainerColor = LocalAppPalette.current.codeBg,
                    unfocusedContainerColor = LocalAppPalette.current.codeBg
                )
            )
            // Character count
            Text(
                text = "${state.srsText.length} chars",
                style = AppTypography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 12.dp, bottom = 8.dp)
            )
        }
    }
}

@Composable
private fun ApiSpecCard(
    state: ValidatorState,
    onFileLoaded: (java.io.File) -> Unit,
    onEndpointSelected: (EndpointInfo) -> Unit,
    onEndpointToggled: (EndpointInfo) -> Unit,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    onToggleBatchMode: () -> Unit
) {
    val strings = LocalAppStrings.current
    SectionCard(
        title = strings.apiSpecification,
        badge = if (state.swaggerFileName.isNotBlank()) state.swaggerFileName else null,
        icon = Icons.Default.Api
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(AppDimensions.spacingMd)) {
            FilePickerButton(
                label = strings.selectSwaggerFile,
                fileExtensions = listOf("json", "yaml", "yml"),
                onFileSelected = { file -> file?.let { onFileLoaded(it) } }
            )

            if (state.availableEndpoints.isNotEmpty()) {
                HorizontalDivider(color = LocalAppPalette.current.border, thickness = 1.dp)

                // Header row with batch toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AppDimensions.spacingXs)
                    ) {
                        Text(
                            text = strings.selectEndpoint,
                            style = AppTypography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                        )
                        if (state.isBatchMode && state.selectedEndpoints.isNotEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = BrandColors.Orange.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = "${state.selectedEndpoints.size} selected",
                                    style = AppTypography.labelSmall.copy(fontSize = 10.sp),
                                    color = BrandColors.Orange,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Select All / Deselect All (only in batch mode)
                        if (state.isBatchMode) {
                            val allSelected = state.selectedEndpoints.size == state.availableEndpoints.size
                            Text(
                                text = if (allSelected) strings.deselectAll else strings.selectAll,
                                style = AppTypography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                color = BrandColors.Orange,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .clickable { if (allSelected) onDeselectAll() else onSelectAll() }
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }

                        // Batch mode toggle
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = strings.multi,
                                style = AppTypography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                            Switch(
                                checked = state.isBatchMode,
                                onCheckedChange = { onToggleBatchMode() },
                                modifier = Modifier.height(20.dp),
                                colors = SwitchDefaults.colors(
                                    checkedTrackColor = BrandColors.Orange,
                                    checkedThumbColor = Color.White
                                )
                            )
                        }
                    }
                }

                // Endpoint list
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 240.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    state.availableEndpoints.forEach { endpoint ->
                        if (state.isBatchMode) {
                            BatchEndpointListItem(
                                endpoint = endpoint,
                                isChecked = endpoint in state.selectedEndpoints,
                                onClick = { onEndpointToggled(endpoint) }
                            )
                        } else {
                            EndpointListItem(
                                endpoint = endpoint,
                                isSelected = state.selectedEndpoint == endpoint,
                                onClick = { onEndpointSelected(endpoint) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EndpointListItem(
    endpoint: EndpointInfo,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val methodColor = httpMethodColor(endpoint.method)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppDimensions.cornerSmall))
            .then(
                if (isSelected) {
                    Modifier
                        .drawBehind {
                            drawRect(
                                color = methodColor.copy(alpha = 0.08f),
                                size = size
                            )
                            // Orange left border
                            drawRect(
                                color = methodColor,
                                topLeft = Offset.Zero,
                                size = Size(3.dp.toPx(), size.height)
                            )
                        }
                } else Modifier
            )
            .clickable(onClick = onClick)
            .padding(horizontal = AppDimensions.spacingSm, vertical = AppDimensions.spacingXs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppDimensions.spacingXs)
    ) {
        HttpMethodChip(method = endpoint.method)

        Text(
            text = endpoint.path,
            style = AppTypography.codeSmall,
            color = if (isSelected) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun BatchEndpointListItem(
    endpoint: EndpointInfo,
    isChecked: Boolean,
    onClick: () -> Unit
) {
    val methodColor = httpMethodColor(endpoint.method)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppDimensions.cornerSmall))
            .then(
                if (isChecked) Modifier.background(methodColor.copy(alpha = 0.06f))
                else Modifier
            )
            .clickable(onClick = onClick)
            .padding(horizontal = AppDimensions.spacingXs, vertical = AppDimensions.spacingXxs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppDimensions.spacingXs)
    ) {
        Checkbox(
            checked = isChecked,
            onCheckedChange = { onClick() },
            modifier = Modifier.size(20.dp),
            colors = CheckboxDefaults.colors(
                checkedColor = BrandColors.Orange,
                uncheckedColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
            )
        )
        HttpMethodChip(method = endpoint.method)
        Text(
            text = endpoint.path,
            style = AppTypography.codeSmall,
            color = if (isChecked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun AnalyzeActionRow(state: ValidatorState, onAnalyze: () -> Unit) {
    val strings = LocalAppStrings.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppDimensions.spacingSm)
    ) {
        Button(
            onClick = onAnalyze,
            modifier = Modifier.height(48.dp),
            enabled = state.srsText.isNotBlank()
                    && (state.selectedEndpoint != null || state.selectedEndpoints.isNotEmpty())
                    && !state.isAnalyzing,
            shape = RoundedCornerShape(AppDimensions.cornerSmall),
            colors = ButtonDefaults.buttonColors(
                containerColor = BrandColors.Orange, // Postman orange
                disabledContainerColor = LocalAppPalette.current.surfaceVariant
            ),
            contentPadding = PaddingValues(horizontal = 24.dp)
        ) {
            Icon(
                imageVector = Icons.Default.FactCheck,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(AppDimensions.spacingXs))
            Text(
                text = if (state.isBatchMode && state.selectedEndpoints.size > 1) {
                    "Analyze ${state.selectedEndpoints.size} Endpoints"
                } else {
                    strings.analyze
                },
                style = AppTypography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White
            )
        }

        if (state.aiProviderName.isNotBlank()) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = state.aiProviderName,
                        style = AppTypography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun ErrorBanner(error: String) {
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

// ---------------------------------------------------------------------------
// Right Panel
// ---------------------------------------------------------------------------

@Composable
private fun RightPanel(
    state: ValidatorState,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxHeight(),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        // Single endpoint results
        AnimatedVisibility(
            visible = state.validationReport != null && !state.isAnalyzing && state.batchResults.isEmpty(),
            enter = fadeIn() + slideInHorizontally(initialOffsetX = { it / 3 })
        ) {
            state.validationReport?.let { report ->
                ResultsDashboard(report = report, extractedRules = state.extractedRules)
            }
        }

        // Batch results
        AnimatedVisibility(
            visible = state.batchResults.isNotEmpty() && !state.isAnalyzing,
            enter = fadeIn() + slideInHorizontally(initialOffsetX = { it / 3 })
        ) {
            BatchResultsDashboard(state = state)
        }

        // Empty state
        if (state.validationReport == null && state.batchResults.isEmpty() && !state.isAnalyzing) {
            EmptyResultsState()
        }
    }
}

@Composable
private fun ResultsDashboard(
    report: ValidationReport,
    extractedRules: SrsExtractionResult?
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(AppDimensions.spacingMd)
    ) {
        // Score hero card
        ScoreHeroCard(report = report)

        // Tabbed results
        ResultsTabs(report = report)

        // Extracted rules
        extractedRules?.let { extraction ->
            ExtractedRulesCard(extraction = extraction)
        }
    }
}

@Composable
private fun ScoreHeroCard(report: ValidationReport) {
    val strings = LocalAppStrings.current
    val palette = LocalAppPalette.current
    val score = report.overallScore
    val scorePercent = (score * 100).toInt()
    val scoreColor = when {
        score >= 0.8f -> StatusColors.Success
        score >= 0.6f -> StatusColors.Warning
        else -> StatusColors.Error
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AppDimensions.cornerMedium),
        color = palette.surface,
        border = BorderStroke(1.dp, palette.border)
    ) {
        Column(
            modifier = Modifier.padding(AppDimensions.spacingLg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AppDimensions.spacingSm)
        ) {
            // Circular score indicator
            Box(
                modifier = Modifier.size(120.dp),
                contentAlignment = Alignment.Center
            ) {
                // Background arc
                val trackColor = palette.border
                Canvas(
                    modifier = Modifier.size(120.dp)
                ) {
                    val strokeWidth = 10.dp.toPx()
                    val arcSize = size.width - strokeWidth
                    val topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)
                    // Track
                    drawArc(
                        color = trackColor,
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = Size(arcSize, arcSize),
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                    // Progress
                    drawArc(
                        color = scoreColor,
                        startAngle = -90f,
                        sweepAngle = 360f * score,
                        useCenter = false,
                        topLeft = topLeft,
                        size = Size(arcSize, arcSize),
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "$scorePercent%",
                        style = AppTypography.headlineLarge.copy(
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = scoreColor
                    )
                    Text(
                        text = strings.score,
                        style = AppTypography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }

            // Summary stats row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                MiniStat(
                    label = strings.matches,
                    value = "${report.matches.size}",
                    color = StatusColors.Success
                )
                MiniStat(
                    label = strings.issues,
                    value = "${report.missingInBackend.size + report.missingInSrs.size}",
                    color = BrandColors.Orange
                )
                MiniStat(
                    label = strings.conflicts,
                    value = "${report.typeConflicts.size}",
                    color = StatusColors.Error
                )
            }
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = AppTypography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            ),
            color = color
        )
        Text(
            text = label,
            style = AppTypography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )
    }
}

@Composable
private fun ResultsTabs(report: ValidationReport) {
    var selectedTab by remember { mutableIntStateOf(0) }

    val strings = LocalAppStrings.current
    val tabs = listOf(
        "${strings.matches} (${report.matches.size})",
        "${strings.issues} (${report.missingInBackend.size + report.missingInSrs.size})",
        "${strings.conflicts} (${report.typeConflicts.size})"
    )

    val palette = LocalAppPalette.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AppDimensions.cornerMedium),
        color = palette.surface,
        border = BorderStroke(1.dp, palette.border)
    ) {
        Column {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = palette.sidebar,
                contentColor = BrandColors.Orange,
                indicator = { tabPositions ->
                    if (selectedTab < tabPositions.size) {
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .wrapContentSize(Alignment.BottomStart),
                            height = 2.dp,
                            color = BrandColors.Orange
                        )
                    }
                },
                divider = { HorizontalDivider(color = palette.border, thickness = 1.dp) }
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                text = title,
                                style = AppTypography.labelSmall.copy(
                                    fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Medium
                                ),
                                color = if (selectedTab == index) {
                                    BrandColors.Orange
                                } else {
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                }
                            )
                        }
                    )
                }
            }

            // Tab content
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp, max = 350.dp)
            ) {
                when (selectedTab) {
                    0 -> MatchesTabContent(matches = report.matches)
                    1 -> IssuesTabContent(
                        missingInBackend = report.missingInBackend,
                        missingInSrs = report.missingInSrs
                    )
                    2 -> ConflictsTabContent(conflicts = report.typeConflicts)
                }
            }
        }
    }
}

@Composable
private fun MatchesTabContent(matches: List<FieldMatch>) {
    if (matches.isEmpty()) {
        EmptyTabState(text = LocalAppStrings.current.noMatchesFound)
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxWidth().padding(AppDimensions.spacingSm),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(matches) { match ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(LocalAppPalette.current.codeBg)
                    .padding(horizontal = AppDimensions.spacingSm, vertical = AppDimensions.spacingXs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppDimensions.spacingXs)
            ) {
                // SRS field
                Text(
                    text = match.fieldName,
                    style = AppTypography.codeSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = AppColors.jsonKey,
                    modifier = Modifier.weight(1.2f)
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = StatusColors.Success,
                    modifier = Modifier.size(14.dp)
                )
                // Type chips
                TypeChip(text = match.srsType, color = AppColors.jsonString)
                Icon(
                    imageVector = Icons.Default.SwapHoriz,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                    modifier = Modifier.size(12.dp)
                )
                TypeChip(text = match.swaggerType, color = AppColors.jsonNumber)
            }
        }
    }
}

@Composable
private fun IssuesTabContent(
    missingInBackend: List<FieldMismatch>,
    missingInSrs: List<FieldMismatch>
) {
    val allIssues = missingInBackend + missingInSrs
    if (allIssues.isEmpty()) {
        EmptyTabState(text = LocalAppStrings.current.noIssuesFound)
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxWidth().padding(AppDimensions.spacingSm),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(allIssues) { mismatch ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(LocalAppPalette.current.codeBg)
                    .padding(horizontal = AppDimensions.spacingSm, vertical = AppDimensions.spacingXs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppDimensions.spacingXs)
            ) {
                Text(
                    text = mismatch.fieldName,
                    style = AppTypography.codeSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                // Source badge
                val (badgeText, badgeColor) = when (mismatch.source) {
                    MismatchSource.SRS_ONLY -> LocalAppStrings.current.srsOnly to BrandColors.Orange
                    MismatchSource.SWAGGER_ONLY -> LocalAppStrings.current.apiOnly to BrandColors.Blue
                }
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = badgeColor.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.3f))
                ) {
                    Text(
                        text = badgeText,
                        style = AppTypography.labelSmall.copy(fontSize = 10.sp),
                        color = badgeColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                if (mismatch.description.isNotBlank()) {
                    Text(
                        text = mismatch.description,
                        style = AppTypography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun ConflictsTabContent(conflicts: List<TypeConflict>) {
    if (conflicts.isEmpty()) {
        EmptyTabState(text = LocalAppStrings.current.noConflictsFound)
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxWidth().padding(AppDimensions.spacingSm),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(conflicts) { conflict ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(LocalAppPalette.current.codeBg)
                    .padding(horizontal = AppDimensions.spacingSm, vertical = AppDimensions.spacingXs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppDimensions.spacingXs)
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = StatusColors.Warning,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = conflict.fieldName,
                    style = AppTypography.codeSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(0.8f)
                )
                TypeChip(text = conflict.srsType, color = AppColors.jsonString)
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = StatusColors.Error,
                    modifier = Modifier.size(14.dp)
                )
                TypeChip(text = conflict.swaggerType, color = AppColors.jsonNumber)
            }
        }
    }
}

@Composable
private fun ExtractedRulesCard(extraction: SrsExtractionResult) {
    SectionCard(
        title = LocalAppStrings.current.extractedRules,
        badge = "${extraction.rules.size} rules found",
        icon = Icons.Default.Rule
    ) {
        Column(
            modifier = Modifier.heightIn(max = 250.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // Header row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(LocalAppPalette.current.sidebar)
                    .padding(horizontal = AppDimensions.spacingSm, vertical = AppDimensions.spacingXs)
            ) {
                Text(
                    text = LocalAppStrings.current.field,
                    style = AppTypography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.weight(1.5f)
                )
                Text(
                    text = LocalAppStrings.current.type,
                    style = AppTypography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = LocalAppStrings.current.req,
                    style = AppTypography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.weight(0.5f)
                )
                Text(
                    text = LocalAppStrings.current.constraints,
                    style = AppTypography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.weight(2f)
                )
            }
            HorizontalDivider(color = LocalAppPalette.current.border, thickness = 1.dp)

            extraction.rules.forEach { rule ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AppDimensions.spacingSm, vertical = AppDimensions.spacingXxs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = rule.fieldName,
                        style = AppTypography.codeSmall,
                        color = AppColors.jsonKey,
                        modifier = Modifier.weight(1.5f)
                    )
                    Text(
                        text = rule.dataType,
                        style = AppTypography.codeSmall,
                        color = AppColors.jsonString,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = if (rule.isRequired) Icons.Default.Check else Icons.Default.Close,
                        contentDescription = null,
                        tint = if (rule.isRequired) StatusColors.Success else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f),
                        modifier = Modifier.weight(0.5f).size(14.dp)
                    )
                    Text(
                        text = rule.constraints.joinToString(", ").ifEmpty { "-" },
                        style = AppTypography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(2f)
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Batch Results
// ---------------------------------------------------------------------------

@Composable
private fun BatchResultsDashboard(state: ValidatorState) {
    val palette = LocalAppPalette.current
    var expandedEndpoint by remember { mutableStateOf<EndpointInfo?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(AppDimensions.spacingMd)
    ) {
        // Overall batch score
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(AppDimensions.cornerMedium),
            color = palette.surface,
            border = BorderStroke(1.dp, palette.border)
        ) {
            Column(
                modifier = Modifier.padding(AppDimensions.spacingLg),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(AppDimensions.spacingSm)
            ) {
                Text(
                    text = LocalAppStrings.current.batchAnalysisResults,
                    style = AppTypography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${state.batchResults.size} endpoints analyzed",
                    style = AppTypography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Overall score ring
                val overallScore = state.overallBatchScore
                val scorePercent = (overallScore * 100).toInt()
                val scoreColor = when {
                    overallScore >= 0.8f -> StatusColors.Success
                    overallScore >= 0.6f -> StatusColors.Warning
                    else -> StatusColors.Error
                }

                Box(modifier = Modifier.size(100.dp), contentAlignment = Alignment.Center) {
                    val trackColor = palette.border
                    Canvas(modifier = Modifier.size(100.dp)) {
                        val strokeWidth = 8.dp.toPx()
                        val arcSize = size.width - strokeWidth
                        val topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)
                        drawArc(color = trackColor, startAngle = -90f, sweepAngle = 360f, useCenter = false, topLeft = topLeft, size = Size(arcSize, arcSize), style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
                        drawArc(color = scoreColor, startAngle = -90f, sweepAngle = 360f * overallScore, useCenter = false, topLeft = topLeft, size = Size(arcSize, arcSize), style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "$scorePercent%", style = AppTypography.headlineLarge.copy(fontSize = 24.sp, fontWeight = FontWeight.Bold), color = scoreColor)
                        Text(text = LocalAppStrings.current.overall, style = AppTypography.labelSmall.copy(fontSize = 9.sp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    }
                }

                // Summary row
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    MiniStat(label = LocalAppStrings.current.matches, value = "${state.totalMatches}", color = StatusColors.Success)
                    MiniStat(label = LocalAppStrings.current.issues, value = "${state.totalIssues}", color = BrandColors.Orange)
                    MiniStat(label = LocalAppStrings.current.conflicts, value = "${state.totalConflicts}", color = StatusColors.Error)
                }
            }
        }

        // Per-endpoint results list
        state.batchResults.forEach { result ->
            val isExpanded = expandedEndpoint == result.endpoint
            val score = result.validationReport.overallScore
            val scorePercent = (score * 100).toInt()
            val scoreColor = when {
                score >= 0.8f -> StatusColors.Success
                score >= 0.6f -> StatusColors.Warning
                else -> StatusColors.Error
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(AppDimensions.cornerSmall),
                color = palette.surface,
                border = BorderStroke(1.dp, palette.border)
            ) {
                Column {
                    // Endpoint header (always visible)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { expandedEndpoint = if (isExpanded) null else result.endpoint }
                            .padding(AppDimensions.spacingSm),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AppDimensions.spacingXs)
                    ) {
                        HttpMethodChip(method = result.endpoint.method)
                        Text(
                            text = result.endpoint.path,
                            style = AppTypography.codeSmall.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        // Score badge
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = scoreColor.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "$scorePercent%",
                                style = AppTypography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp),
                                color = scoreColor,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }

                        // Match/Issue counts
                        Text(
                            text = "${result.validationReport.matches.size}/${result.extractedRules.rules.size}",
                            style = AppTypography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )

                        Icon(
                            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Expanded details
                    AnimatedVisibility(visible = isExpanded) {
                        Column(modifier = Modifier.padding(start = AppDimensions.spacingSm, end = AppDimensions.spacingSm, bottom = AppDimensions.spacingSm)) {
                            HorizontalDivider(color = palette.border, thickness = 1.dp)
                            Spacer(modifier = Modifier.height(AppDimensions.spacingXs))

                            // Quick stats
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(AppDimensions.spacingSm)
                            ) {
                                CompactStat(LocalAppStrings.current.matches, "${result.validationReport.matches.size}", StatusColors.Success, Modifier.weight(1f))
                                CompactStat(LocalAppStrings.current.srsOnly, "${result.validationReport.missingInBackend.size}", BrandColors.Orange, Modifier.weight(1f))
                                CompactStat(LocalAppStrings.current.apiOnly, "${result.validationReport.missingInSrs.size}", BrandColors.Blue, Modifier.weight(1f))
                                CompactStat(LocalAppStrings.current.conflicts, "${result.validationReport.typeConflicts.size}", StatusColors.Error, Modifier.weight(1f))
                            }

                            Spacer(modifier = Modifier.height(AppDimensions.spacingXs))

                            // Matched fields
                            if (result.validationReport.matches.isNotEmpty()) {
                                Text(
                                    text = "Matched: ${result.validationReport.matches.joinToString(", ") { it.fieldName }}",
                                    style = AppTypography.labelSmall,
                                    color = StatusColors.Success.copy(alpha = 0.8f),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (result.validationReport.missingInBackend.isNotEmpty()) {
                                Text(
                                    text = "Missing in API: ${result.validationReport.missingInBackend.joinToString(", ") { it.fieldName }}",
                                    style = AppTypography.labelSmall,
                                    color = BrandColors.Orange.copy(alpha = 0.8f),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            // Extracted rules count
                            Text(
                                text = "${result.extractedRules.rules.size} rules extracted by AI",
                                style = AppTypography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactStat(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.08f)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = value, style = AppTypography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 14.sp), color = color)
            Text(text = label, style = AppTypography.labelSmall.copy(fontSize = 9.sp), color = color.copy(alpha = 0.7f))
        }
    }
}

// ---------------------------------------------------------------------------
// Shared / Utility composables
// ---------------------------------------------------------------------------

@Composable
private fun EmptyResultsState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AppDimensions.spacingSm)
        ) {
            Icon(
                imageVector = Icons.Default.VerifiedUser,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.12f),
                modifier = Modifier.size(72.dp)
            )
            Text(
                text = "Validation Results",
                style = AppTypography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.25f)
            )
            Text(
                text = "Enter SRS text, select a Swagger file\nand endpoint, then click Analyze",
                style = AppTypography.labelSmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.18f),
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
private fun EmptyTabState(text: String) {
    Box(
        modifier = Modifier.fillMaxWidth().height(100.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = AppTypography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
        )
    }
}

@Composable
private fun AnalyzingOverlay(state: ValidatorState) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f)),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(AppDimensions.cornerMedium),
            color = LocalAppPalette.current.surface,
            border = BorderStroke(1.dp, LocalAppPalette.current.border),
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(AppDimensions.spacingXl).widthIn(min = 280.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(AppDimensions.spacingMd)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(48.dp),
                    color = BrandColors.Orange,
                    strokeWidth = 4.dp
                )
                if (state.batchTotal > 1) {
                    Text(
                        text = "Analyzing endpoint ${state.batchProgress + 1} of ${state.batchTotal}",
                        style = AppTypography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                    if (state.currentAnalyzingEndpoint.isNotBlank()) {
                        Text(
                            text = state.currentAnalyzingEndpoint,
                            style = AppTypography.codeSmall,
                            color = BrandColors.Orange
                        )
                    }
                    LinearProgressIndicator(
                        progress = { state.batchProgress.toFloat() / state.batchTotal },
                        modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                        color = BrandColors.Orange,
                        trackColor = LocalAppPalette.current.border
                    )
                } else {
                    Text(
                        text = if (state.isExtracting) LocalAppStrings.current.extractingRules else LocalAppStrings.current.validatingAgainstSwagger,
                        style = AppTypography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}

@Composable
private fun HttpMethodChip(method: String) {
    val color = httpMethodColor(method)
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = color.copy(alpha = 0.15f)
    ) {
        Text(
            text = method.uppercase(),
            style = AppTypography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                letterSpacing = 0.5.sp
            ),
            color = color,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun TypeChip(text: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = color.copy(alpha = 0.1f)
    ) {
        Text(
            text = text,
            style = AppTypography.codeSmall.copy(fontSize = 10.sp),
            color = color,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
        )
    }
}

private fun httpMethodColor(method: String): Color {
    return when (method.uppercase()) {
        "GET" -> HttpMethodColors.GET
        "POST" -> HttpMethodColors.POST
        "PUT" -> HttpMethodColors.PUT
        "DELETE" -> HttpMethodColors.DELETE
        "PATCH" -> HttpMethodColors.PATCH
        else -> HttpMethodColors.OPTIONS
    }
}
