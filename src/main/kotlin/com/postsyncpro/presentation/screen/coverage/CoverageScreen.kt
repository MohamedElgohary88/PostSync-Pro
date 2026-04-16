package com.postsyncpro.presentation.screen.coverage

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.postsyncpro.presentation.theme.*
import com.postsyncpro.presentation.components.*
import com.postsyncpro.presentation.i18n.LocalAppStrings
import com.postsyncpro.presentation.i18n.AppStrings
import com.postsyncpro.domain.model.*

private fun statusColor(status: CoverageStatus): Color = when (status) {
    CoverageStatus.FULLY_COVERED -> StatusColors.Success
    CoverageStatus.PARTIALLY_COVERED -> StatusColors.Warning
    CoverageStatus.NOT_COVERED -> StatusColors.Error
    CoverageStatus.NOT_APPLICABLE -> Color(0xFF8A8A8A).copy(alpha = 0.3f)
}

private fun statusLabel(status: CoverageStatus, strings: AppStrings): String = when (status) {
    CoverageStatus.FULLY_COVERED -> strings.fullyCovered
    CoverageStatus.PARTIALLY_COVERED -> strings.partiallyCovered
    CoverageStatus.NOT_COVERED -> strings.notCovered
    CoverageStatus.NOT_APPLICABLE -> strings.na
}

@Composable
fun CoverageScreen(viewModel: CoverageViewModel) {
    val state by viewModel.state.collectAsState()
    val palette = LocalAppPalette.current
    val strings = LocalAppStrings.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
            .padding(20.dp)
    ) {
        // Title
        Text(
            text = strings.coverageAnalysis,
            style = AppTypography.titleMedium,
            color = palette.textPrimary
        )

        Spacer(Modifier.height(16.dp))

        // Top Section: SRS + Swagger + Analyze
        TopInputSection(
            state = state,
            palette = palette,
            onSrsTextChange = { viewModel.updateSrsText(it) },
            onFileSelected = { viewModel.loadSwaggerFile(it) },
            onAnalyze = { viewModel.analyzeCoverage() }
        )

        Spacer(Modifier.height(16.dp))

        // Error
        state.error?.let { error ->
            Text(
                text = error,
                style = AppTypography.bodyMedium,
                color = StatusColors.Error,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        // Progress
        if (state.isAnalyzing) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = BrandColors.Orange,
                    strokeWidth = 2.dp
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = state.progress.ifEmpty { "Analyzing coverage..." },
                    style = AppTypography.bodyMedium,
                    color = palette.textSecondary
                )
            }
        }

        // Main Area: Heatmap
        state.matrix?.let { matrix ->
            HeatmapGrid(
                matrix = matrix,
                palette = palette,
                selectedCell = state.selectedCell,
                onCellClick = { viewModel.selectCell(it) },
                modifier = Modifier.weight(1f)
            )

            Spacer(Modifier.height(16.dp))

            // Bottom: Overall Coverage Bar
            OverallCoverageBar(
                coverage = matrix.overallCoverage,
                palette = palette
            )
        }

        if (state.matrix == null && !state.isAnalyzing) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.GridOn,
                        contentDescription = null,
                        tint = palette.textTertiary,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = strings.pasteSrsAndLoadSwagger,
                        style = AppTypography.bodyMedium,
                        color = palette.textTertiary
                    )
                }
            }
        }

        // Cell Detail Popup
        state.selectedCell?.let { cell ->
            CellDetailPopup(
                cell = cell,
                requirements = state.matrix?.requirements ?: emptyList(),
                palette = palette,
                onDismiss = { viewModel.selectCell(null) }
            )
        }
    }
}

@Composable
private fun TopInputSection(
    state: CoverageState,
    palette: AppPalette,
    onSrsTextChange: (String) -> Unit,
    onFileSelected: (java.io.File) -> Unit,
    onAnalyze: () -> Unit
) {
    val strings = LocalAppStrings.current

    Row(
        modifier = Modifier.fillMaxWidth().height(180.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // SRS Text Area
        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
            Text(
                text = strings.srsRequirements,
                style = AppTypography.titleMedium,
                color = palette.textPrimary
            )
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = state.srsText,
                onValueChange = onSrsTextChange,
                modifier = Modifier.fillMaxWidth().weight(1f),
                textStyle = AppTypography.bodyMedium.copy(color = palette.textPrimary),
                placeholder = {
                    Text(
                        strings.pasteSrsRequirements,
                        style = AppTypography.bodyMedium,
                        color = palette.textTertiary
                    )
                },
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    focusedBorderColor = BrandColors.Orange,
                    unfocusedBorderColor = palette.border,
                    backgroundColor = palette.codeBg,
                    cursorColor = BrandColors.Orange
                )
            )
        }

        // Swagger + Analyze
        Column(
            modifier = Modifier.width(220.dp).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = strings.swaggerSpec,
                style = AppTypography.titleMedium,
                color = palette.textPrimary
            )
            Spacer(Modifier.height(2.dp))

            FilePickerButton(
                label = if (state.swaggerFileName.isNotEmpty()) state.swaggerFileName else strings.selectSwaggerFile,
                fileExtensions = listOf("json", "yaml", "yml"),
                onFileSelected = onFileSelected
            )

            state.swaggerSpec?.let { spec ->
                Text(
                    text = "${spec.info.title} v${spec.info.version}",
                    style = AppTypography.labelSmall,
                    color = palette.textSecondary
                )
                Text(
                    text = "${spec.paths.size} ${strings.endpoints}",
                    style = AppTypography.labelSmall,
                    color = palette.textTertiary
                )
            }

            Spacer(Modifier.weight(1f))

            Button(
                onClick = onAnalyze,
                enabled = state.srsText.isNotBlank() && state.swaggerSpec != null && !state.isAnalyzing,
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = BrandColors.Orange,
                    contentColor = Color.White,
                    disabledBackgroundColor = palette.border
                ),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Analytics,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(strings.analyzeCoverage, style = AppTypography.labelSmall, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun HeatmapGrid(
    matrix: CoverageMatrix,
    palette: AppPalette,
    selectedCell: CoverageCell?,
    onCellClick: (CoverageCell) -> Unit,
    modifier: Modifier = Modifier
) {
    val horizontalScrollState = rememberScrollState()
    val verticalScrollState = rememberScrollState()
    val cellSize = 44.dp
    val reqLabelWidth = 200.dp
    val headerHeight = 120.dp

    val strings = LocalAppStrings.current

    SectionCard(title = strings.coverageHeatmap, badge = "${matrix.requirements.size} x ${matrix.endpoints.size}", icon = null) {
        // Legend
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CoverageStatus.values().forEach { status ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(statusColor(status))
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = statusLabel(status, strings),
                        style = AppTypography.labelSmall,
                        color = palette.textSecondary
                    )
                }
            }
        }

        Box(modifier = modifier.fillMaxWidth()) {
            Row {
                // Y-axis: Requirement labels
                Column(modifier = Modifier.width(reqLabelWidth)) {
                    // Empty corner
                    Spacer(Modifier.height(headerHeight))

                    Column(modifier = Modifier.verticalScroll(verticalScrollState)) {
                        matrix.requirements.forEach { req ->
                            Box(
                                modifier = Modifier
                                    .height(cellSize)
                                    .fillMaxWidth()
                                    .padding(end = 4.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Column {
                                    Text(
                                        text = req.id,
                                        style = AppTypography.labelSmall,
                                        color = palette.textPrimary,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = req.section,
                                        style = AppTypography.labelSmall,
                                        color = palette.textTertiary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                // Grid area
                Column(modifier = Modifier.weight(1f).horizontalScroll(horizontalScrollState)) {
                    // X-axis: Endpoint headers (rotated)
                    Row(modifier = Modifier.height(headerHeight)) {
                        matrix.endpoints.forEach { endpoint ->
                            Box(
                                modifier = Modifier
                                    .width(cellSize)
                                    .fillMaxHeight()
                                    .padding(bottom = 4.dp),
                                contentAlignment = Alignment.BottomCenter
                            ) {
                                Text(
                                    text = endpoint,
                                    style = AppTypography.labelSmall,
                                    color = palette.textSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .graphicsLayer {
                                            rotationZ = -60f
                                            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 1f)
                                        }
                                        .widthIn(max = 100.dp)
                                )
                            }
                        }
                    }

                    // Cells
                    Column(modifier = Modifier.verticalScroll(verticalScrollState)) {
                        matrix.requirements.forEach { req ->
                            Row {
                                matrix.endpoints.forEach { endpoint ->
                                    val cell = matrix.cells.find {
                                        it.requirementId == req.id && it.endpoint == endpoint
                                    }
                                    val isSelected = selectedCell?.requirementId == req.id &&
                                            selectedCell.endpoint == endpoint

                                    Box(
                                        modifier = Modifier
                                            .size(cellSize)
                                            .padding(1.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(
                                                cell?.let { statusColor(it.status) }
                                                    ?: Color(0xFF8A8A8A).copy(alpha = 0.1f)
                                            )
                                            .then(
                                                if (isSelected) Modifier.border(
                                                    2.dp,
                                                    BrandColors.Orange,
                                                    RoundedCornerShape(4.dp)
                                                ) else Modifier
                                            )
                                            .clickable {
                                                cell?.let { onCellClick(it) }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        cell?.let {
                                            Text(
                                                text = "${(it.score * 100).toInt()}",
                                                style = AppTypography.labelSmall,
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 9.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OverallCoverageBar(
    coverage: Float,
    palette: AppPalette
) {
    val coveragePercent = (coverage * 100).toInt()
    val barColor = when {
        coverage >= 0.8f -> StatusColors.Success
        coverage >= 0.5f -> StatusColors.Warning
        else -> StatusColors.Error
    }

    val strings = LocalAppStrings.current

    SectionCard(title = strings.overallCoverage, badge = null, icon = null) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Progress bar
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(24.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(palette.codeBg)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fraction = coverage.coerceIn(0f, 1f))
                        .clip(RoundedCornerShape(12.dp))
                        .background(barColor)
                )
            }

            Text(
                text = "$coveragePercent%",
                style = AppTypography.titleMedium,
                color = barColor,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun CellDetailPopup(
    cell: CoverageCell,
    requirements: List<SrsRequirement>,
    palette: AppPalette,
    onDismiss: () -> Unit
) {
    val strings = LocalAppStrings.current
    val requirement = requirements.find { it.id == cell.requirementId }

    AlertDialog(
        onDismissRequest = onDismiss,
        backgroundColor = palette.surface,
        shape = RoundedCornerShape(12.dp),
        title = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(statusColor(cell.status))
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = statusLabel(cell.status, strings),
                        style = AppTypography.titleMedium,
                        color = palette.textPrimary
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${cell.requirementId} -> ${cell.endpoint}",
                    style = AppTypography.labelSmall,
                    color = palette.textSecondary
                )
            }
        },
        text = {
            Column {
                Text(
                    text = "${strings.score}: ${(cell.score * 100).toInt()}%",
                    style = AppTypography.bodyMedium,
                    color = palette.textPrimary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))

                requirement?.let { req ->
                    Text(
                        text = "${strings.requirement}${req.description}",
                        style = AppTypography.bodyMedium,
                        color = palette.textSecondary
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "${strings.fields}${req.fields.joinToString(", ")}",
                        style = AppTypography.codeSmall,
                        color = palette.textTertiary
                    )
                    Spacer(Modifier.height(8.dp))
                }

                Text(
                    text = strings.details,
                    style = AppTypography.labelSmall,
                    color = palette.textPrimary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = cell.details,
                    style = AppTypography.bodyMedium,
                    color = palette.textSecondary
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.close, color = BrandColors.Orange)
            }
        }
    )
}
