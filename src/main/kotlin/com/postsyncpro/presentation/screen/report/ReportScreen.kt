package com.postsyncpro.presentation.screen.report

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.postsyncpro.presentation.theme.*
import com.postsyncpro.presentation.components.*
import com.postsyncpro.presentation.i18n.LocalAppStrings
import androidx.compose.foundation.interaction.MutableInteractionSource
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

@Composable
fun ReportScreen(viewModel: ReportViewModel) {
    val state by viewModel.state.collectAsState()
    val strings = LocalAppStrings.current
    val palette = LocalAppPalette.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
    ) {
        // Main content area
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(AppDimensions.spacingMd)
        ) {
            // Left panel (40%) - Config & Summary
            Column(
                modifier = Modifier
                    .weight(0.4f)
                    .fillMaxHeight()
                    .padding(end = AppDimensions.spacingMd),
                verticalArrangement = Arrangement.spacedBy(AppDimensions.spacingMd)
            ) {
                // Config Card
                SectionCard(
                    title = strings.reportConfiguration,
                    badge = null,
                    icon = null
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(AppDimensions.spacingSm)
                    ) {
                        Text(
                            text = strings.projectName,
                            style = AppTypography.labelSmall,
                            color = palette.textSecondary
                        )
                        OutlinedTextField(
                            value = state.projectName,
                            onValueChange = { viewModel.updateProjectName(it) },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = AppTypography.bodyMedium.copy(color = palette.textPrimary),
                            singleLine = true,
                            colors = TextFieldDefaults.outlinedTextFieldColors(
                                textColor = palette.textPrimary,
                                backgroundColor = palette.codeBg,
                                focusedBorderColor = BrandColors.Orange,
                                unfocusedBorderColor = palette.border,
                                cursorColor = BrandColors.Orange
                            ),
                            shape = RoundedCornerShape(AppDimensions.cornerSmall)
                        )
                    }
                }

                // Health Score Indicator
                SectionCard(
                    title = strings.healthScore,
                    badge = null,
                    icon = null
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(AppDimensions.spacingSm)
                    ) {
                        Box(
                            modifier = Modifier.size(160.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            val scoreColor = when {
                                state.healthScore >= 0.8f -> StatusColors.Success
                                state.healthScore >= 0.5f -> StatusColors.Warning
                                else -> StatusColors.Error
                            }

                            Canvas(modifier = Modifier.size(150.dp)) {
                                val strokeWidth = 12.dp.toPx()
                                val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
                                val topLeft = Offset(strokeWidth / 2, strokeWidth / 2)

                                // Background arc
                                drawArc(
                                    color = palette.border,
                                    startAngle = 135f,
                                    sweepAngle = 270f,
                                    useCenter = false,
                                    topLeft = topLeft,
                                    size = arcSize,
                                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                                )

                                // Score arc
                                drawArc(
                                    color = scoreColor,
                                    startAngle = 135f,
                                    sweepAngle = 270f * state.healthScore,
                                    useCenter = false,
                                    topLeft = topLeft,
                                    size = arcSize,
                                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                                )
                            }

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "${(state.healthScore * 100).toInt()}%",
                                    style = AppTypography.titleMedium,
                                    color = palette.textPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = strings.overall,
                                    style = AppTypography.labelSmall,
                                    color = palette.textTertiary
                                )
                            }
                        }
                    }
                }

                // SRS Compliance Preview
                state.srsMetrics?.let { srs ->
                    SectionCard(
                        title = strings.srsCompliance,
                        badge = null,
                        icon = null
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(AppDimensions.spacingXs)
                        ) {
                            MetricRow(strings.matched, srs.matchedFields.toString(), StatusColors.Success, palette)
                            MetricRow(strings.missing, srs.missingInApi.toString(), StatusColors.Error, palette)
                            MetricRow(strings.conflicts, srs.conflicts.toString(), StatusColors.Warning, palette)
                        }
                    }
                }

                // Fuzz Summary Preview
                state.fuzzMetrics?.let { fuzz ->
                    SectionCard(
                        title = strings.fuzzSummary,
                        badge = null,
                        icon = null
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(AppDimensions.spacingXs)
                        ) {
                            MetricRow(strings.passed, fuzz.passed.toString(), StatusColors.Success, palette)
                            MetricRow(strings.failed, fuzz.failed.toString(), StatusColors.Error, palette)
                        }
                    }
                }
            }

            // Right panel (60%) - Endpoint Details
            Column(
                modifier = Modifier
                    .weight(0.6f)
                    .fillMaxHeight()
            ) {
                SectionCard(
                    title = strings.endpointDetails,
                    badge = "${state.endpointDetails.size} ${strings.endpoints}",
                    icon = null
                ) {
                    Column {
                        // Table header
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(palette.codeBg)
                                .padding(horizontal = AppDimensions.spacingSm, vertical = AppDimensions.spacingXs),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = strings.method,
                                style = AppTypography.labelSmall,
                                color = palette.textTertiary,
                                modifier = Modifier.weight(0.12f),
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = strings.path,
                                style = AppTypography.labelSmall,
                                color = palette.textTertiary,
                                modifier = Modifier.weight(0.43f),
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = strings.srsScore,
                                style = AppTypography.labelSmall,
                                color = palette.textTertiary,
                                modifier = Modifier.weight(0.2f),
                                textAlign = TextAlign.Center,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = strings.fuzzPassRate,
                                style = AppTypography.labelSmall,
                                color = palette.textTertiary,
                                modifier = Modifier.weight(0.25f),
                                textAlign = TextAlign.Center,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Divider(color = palette.divider)

                        // Table rows
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(state.endpointDetails) { endpoint ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .hoverable(remember { MutableInteractionSource() })
                                        .padding(
                                            horizontal = AppDimensions.spacingSm,
                                            vertical = AppDimensions.spacingXs
                                        ),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    MethodBadge(
                                        method = endpoint.method,
                                        modifier = Modifier.weight(0.12f)
                                    )
                                    Text(
                                        text = endpoint.path,
                                        style = AppTypography.codeSmall,
                                        color = palette.textPrimary,
                                        modifier = Modifier.weight(0.43f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    ScoreBadge(
                                        score = endpoint.srsScore,
                                        modifier = Modifier.weight(0.2f)
                                    )
                                    ScoreBadge(
                                        score = endpoint.fuzzPassRate,
                                        modifier = Modifier.weight(0.25f)
                                    )
                                }
                                Divider(color = palette.divider.copy(alpha = 0.5f))
                            }
                        }

                        if (state.endpointDetails.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(AppDimensions.spacingLg),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = strings.noEndpointData,
                                    style = AppTypography.bodyMedium,
                                    color = palette.textTertiary,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }

        // Error message
        AnimatedVisibility(
            visible = state.error != null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            state.error?.let { error ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AppDimensions.spacingMd)
                        .background(
                            StatusColors.Error.copy(alpha = 0.1f),
                            RoundedCornerShape(AppDimensions.cornerSmall)
                        )
                        .padding(AppDimensions.spacingSm),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppDimensions.spacingSm)
                ) {
                    Text(
                        text = error,
                        style = AppTypography.bodyMedium,
                        color = StatusColors.Error
                    )
                }
            }
        }

        // Success message
        AnimatedVisibility(
            visible = state.isGenerated && state.outputPath.isNotEmpty(),
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppDimensions.spacingMd)
                    .background(
                        StatusColors.Success.copy(alpha = 0.1f),
                        RoundedCornerShape(AppDimensions.cornerSmall)
                    )
                    .padding(AppDimensions.spacingSm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${strings.reportSavedTo}${state.outputPath}",
                    style = AppTypography.bodyMedium,
                    color = StatusColors.Success
                )
            }
        }

        Spacer(modifier = Modifier.height(AppDimensions.spacingSm))

        // Bottom: Download QA Certificate button
        Button(
            onClick = {
                val dialog = FileDialog(null as Frame?, "Save Report", FileDialog.SAVE)
                dialog.file = "QA_Report.pdf"
                dialog.isVisible = true
                val dir = dialog.directory
                val file = dialog.file
                if (dir != null && file != null) {
                    val outputFile = File(dir, file)
                    viewModel.generateReport(outputFile)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppDimensions.spacingMd)
                .padding(bottom = AppDimensions.spacingMd)
                .height(48.dp),
            enabled = !state.isGenerating,
            colors = ButtonDefaults.buttonColors(
                backgroundColor = BrandColors.Orange,
                contentColor = Color.White,
                disabledBackgroundColor = BrandColors.Orange.copy(alpha = 0.5f),
                disabledContentColor = Color.White.copy(alpha = 0.7f)
            ),
            shape = RoundedCornerShape(AppDimensions.cornerMedium)
        ) {
            if (state.isGenerating) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(AppDimensions.spacingSm))
                Text(
                    text = strings.generatingReport,
                    style = AppTypography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
            } else {
                Text(
                    text = strings.downloadQaCertificate,
                    style = AppTypography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun MetricRow(
    label: String,
    value: String,
    color: Color,
    palette: AppPalette
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
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
                text = label,
                style = AppTypography.bodyMedium,
                color = palette.textSecondary
            )
        }
        Text(
            text = value,
            style = AppTypography.bodyMedium,
            color = palette.textPrimary,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun MethodBadge(method: String, modifier: Modifier = Modifier) {
    val color = when (method.uppercase()) {
        "GET" -> HttpMethodColors.GET
        "POST" -> HttpMethodColors.POST
        "PUT" -> HttpMethodColors.PUT
        "DELETE" -> HttpMethodColors.DELETE
        "PATCH" -> HttpMethodColors.PATCH
        else -> HttpMethodColors.OPTIONS
    }

    Box(modifier = modifier) {
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
}

@Composable
private fun ScoreBadge(score: Float, modifier: Modifier = Modifier) {
    val percentage = (score * 100).toInt()
    val color = when {
        percentage >= 80 -> StatusColors.Success
        percentage >= 50 -> StatusColors.Warning
        else -> StatusColors.Error
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "$percentage%",
            style = AppTypography.labelSmall,
            color = color,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .background(color.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
                .padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}
