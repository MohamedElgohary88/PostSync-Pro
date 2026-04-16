package com.postsyncpro.presentation.screen.regression

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.*
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import com.postsyncpro.presentation.theme.*
import com.postsyncpro.presentation.components.*
import com.postsyncpro.presentation.i18n.LocalAppStrings
import com.postsyncpro.presentation.i18n.AppStrings
import com.postsyncpro.domain.model.*

private fun statusColor(status: RegressionStatus): Color = when (status) {
    RegressionStatus.IMPROVED -> StatusColors.Success
    RegressionStatus.REGRESSED -> StatusColors.Error
    RegressionStatus.STABLE -> Color(0xFF8A8A8A)
    RegressionStatus.NO_BASELINE -> StatusColors.Info
}

private fun statusLabel(status: RegressionStatus): String = when (status) {
    RegressionStatus.IMPROVED -> "Improved"
    RegressionStatus.REGRESSED -> "Regressed"
    RegressionStatus.STABLE -> "Stable"
    RegressionStatus.NO_BASELINE -> "No Baseline"
}

@Composable
fun RegressionScreen(viewModel: RegressionViewModel) {
    val state by viewModel.state.collectAsState()
    val palette = LocalAppPalette.current
    val strings = LocalAppStrings.current

    LaunchedEffect(Unit) {
        viewModel.loadDashboard()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
            .padding(20.dp)
    ) {
        if (state.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = BrandColors.Orange)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = strings.loadingRegressionData,
                        style = AppTypography.bodyMedium,
                        color = palette.textSecondary
                    )
                }
            }
            return@Column
        }

        state.error?.let { error ->
            Text(
                text = error,
                style = AppTypography.bodyMedium,
                color = StatusColors.Error,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        val dashboard = state.dashboard
        if (dashboard == null || dashboard.endpointAnalyses.isEmpty()) {
            EmptyState(palette, strings)
            return@Column
        }

        // Dashboard Header
        DashboardHeader(dashboard = dashboard, palette = palette, strings = strings)

        Spacer(Modifier.height(16.dp))

        // Stats Row
        StatsRow(dashboard = dashboard, palette = palette, strings = strings)

        Spacer(Modifier.height(16.dp))

        // Endpoint List
        Text(
            text = strings.endpointAnalysis,
            style = AppTypography.titleMedium,
            color = palette.textPrimary
        )

        Spacer(Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(dashboard.endpointAnalyses) { analysis ->
                EndpointCard(
                    analysis = analysis,
                    isExpanded = state.expandedEndpoint == "${analysis.currentRun.method} ${analysis.currentRun.endpoint}",
                    palette = palette,
                    onToggle = {
                        viewModel.toggleEndpoint("${analysis.currentRun.method} ${analysis.currentRun.endpoint}")
                    }
                )
            }
        }
    }
}

@Composable
private fun EmptyState(palette: AppPalette, strings: AppStrings) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.TrendingUp,
                contentDescription = null,
                tint = palette.textTertiary,
                modifier = Modifier.size(64.dp)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = strings.noRegressionData,
                style = AppTypography.titleMedium,
                color = palette.textSecondary
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = strings.runFuzzToStart,
                style = AppTypography.bodyMedium,
                color = palette.textTertiary
            )
        }
    }
}

@Composable
private fun DashboardHeader(
    dashboard: RegressionDashboard,
    palette: AppPalette,
    strings: AppStrings
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = strings.regressionDashboard,
                style = AppTypography.titleMedium,
                color = palette.textPrimary
            )
            Spacer(Modifier.width(12.dp))

            // Overall trend indicator
            val trendIcon = when (dashboard.overallTrend) {
                RegressionStatus.IMPROVED -> Icons.Default.TrendingUp
                RegressionStatus.REGRESSED -> Icons.Default.TrendingDown
                else -> Icons.Default.TrendingFlat
            }
            Icon(
                imageVector = trendIcon,
                contentDescription = statusLabel(dashboard.overallTrend),
                tint = statusColor(dashboard.overallTrend),
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.width(6.dp))
            StatusBadge(
                text = statusLabel(dashboard.overallTrend),
                type = when (dashboard.overallTrend) {
                    RegressionStatus.IMPROVED -> BadgeType.SUCCESS
                    RegressionStatus.REGRESSED -> BadgeType.ERROR
                    RegressionStatus.STABLE -> BadgeType.NEUTRAL
                    RegressionStatus.NO_BASELINE -> BadgeType.INFO
                }
            )
        }

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "${dashboard.totalRunsTracked} ${strings.runsTracked}",
                style = AppTypography.labelSmall,
                color = palette.textSecondary
            )
            dashboard.lastRunDate?.let { date ->
                Text(
                    text = "${strings.lastRun}${date.format(DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm"))}",
                    style = AppTypography.labelSmall,
                    color = palette.textTertiary
                )
            }
        }
    }
}

@Composable
private fun StatsRow(
    dashboard: RegressionDashboard,
    palette: AppPalette,
    strings: AppStrings
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Overall Pass Rate Trend
        val totalCurrent = dashboard.endpointAnalyses.sumOf { it.currentRun.totalTests }
        val passedCurrent = dashboard.endpointAnalyses.sumOf { it.currentRun.passedTests }
        val overallPassRate = if (totalCurrent > 0) passedCurrent.toFloat() / totalCurrent else 0f
        val avgDelta = if (dashboard.endpointAnalyses.isNotEmpty()) {
            dashboard.endpointAnalyses.map { it.passRateDelta }.average().toFloat()
        } else 0f

        StatCard(
            title = strings.overallPassRate,
            value = "${(overallPassRate * 100).toInt()}%",
            delta = avgDelta,
            palette = palette,
            modifier = Modifier.weight(1f)
        )

        StatCard(
            title = strings.endpointsTracked,
            value = "${dashboard.endpointAnalyses.size}",
            delta = null,
            palette = palette,
            modifier = Modifier.weight(1f)
        )

        val improvedCount = dashboard.endpointAnalyses.count { it.status == RegressionStatus.IMPROVED }
        val regressedCount = dashboard.endpointAnalyses.count { it.status == RegressionStatus.REGRESSED }

        StatCard(
            title = strings.improved,
            value = "$improvedCount",
            delta = null,
            palette = palette,
            valueColor = StatusColors.Success,
            modifier = Modifier.weight(1f)
        )

        StatCard(
            title = strings.regressed,
            value = "$regressedCount",
            delta = null,
            palette = palette,
            valueColor = if (regressedCount > 0) StatusColors.Error else palette.textPrimary,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    delta: Float?,
    palette: AppPalette,
    valueColor: Color = palette.textPrimary,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(palette.surface)
            .border(1.dp, palette.border, RoundedCornerShape(10.dp))
            .padding(14.dp)
    ) {
        Column {
            Text(
                text = title,
                style = AppTypography.labelSmall,
                color = palette.textTertiary
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = value,
                    style = AppTypography.titleMedium,
                    color = valueColor,
                    fontWeight = FontWeight.Bold
                )
                if (delta != null && delta != 0f) {
                    Spacer(Modifier.width(6.dp))
                    val deltaColor = if (delta > 0) StatusColors.Success else StatusColors.Error
                    val deltaPrefix = if (delta > 0) "+" else ""
                    Text(
                        text = "$deltaPrefix${(delta * 100).toInt()}%",
                        style = AppTypography.labelSmall,
                        color = deltaColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun EndpointCard(
    analysis: RegressionAnalysis,
    isExpanded: Boolean,
    palette: AppPalette,
    onToggle: () -> Unit
) {
    val strings = LocalAppStrings.current
    val current = analysis.currentRun
    val previous = analysis.previousRun

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(palette.surface)
            .border(1.dp, palette.border, RoundedCornerShape(10.dp))
            .clickable(onClick = onToggle)
            .padding(14.dp)
    ) {
        // Header row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Method badge
            val methodColor = when (current.method.uppercase()) {
                "GET" -> HttpMethodColors.GET
                "POST" -> HttpMethodColors.POST
                "PUT" -> HttpMethodColors.PUT
                "DELETE" -> HttpMethodColors.DELETE
                else -> BrandColors.Purple
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(methodColor)
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = current.method.uppercase(),
                    style = AppTypography.labelSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.width(10.dp))

            Text(
                text = current.endpoint,
                style = AppTypography.bodyMedium,
                color = palette.textPrimary,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.width(8.dp))

            // Status badge
            StatusBadge(
                text = statusLabel(analysis.status),
                type = when (analysis.status) {
                    RegressionStatus.IMPROVED -> BadgeType.SUCCESS
                    RegressionStatus.REGRESSED -> BadgeType.ERROR
                    RegressionStatus.STABLE -> BadgeType.NEUTRAL
                    RegressionStatus.NO_BASELINE -> BadgeType.INFO
                }
            )

            Spacer(Modifier.width(8.dp))

            Icon(
                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (isExpanded) strings.collapse else strings.expand,
                tint = palette.textTertiary,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(Modifier.height(10.dp))

        // Metrics row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Pass rate
            Column {
                Text(
                    text = strings.passRate,
                    style = AppTypography.labelSmall,
                    color = palette.textTertiary
                )
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "${(current.passRate * 100).toInt()}%",
                        style = AppTypography.titleMedium,
                        color = palette.textPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    if (previous != null && analysis.passRateDelta != 0f) {
                        Spacer(Modifier.width(4.dp))
                        val deltaColor = if (analysis.passRateDelta > 0) StatusColors.Success else StatusColors.Error
                        val deltaIcon = if (analysis.passRateDelta > 0) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward
                        Icon(
                            imageVector = deltaIcon,
                            contentDescription = null,
                            tint = deltaColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "${kotlin.math.abs((analysis.passRateDelta * 100).toInt())}%",
                            style = AppTypography.labelSmall,
                            color = deltaColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (previous != null) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "(was ${(previous.passRate * 100).toInt()}%)",
                            style = AppTypography.labelSmall,
                            color = palette.textTertiary
                        )
                    }
                }
            }

            // Response time
            Column {
                Text(
                    text = strings.avgResponseTime,
                    style = AppTypography.labelSmall,
                    color = palette.textTertiary
                )
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "${current.avgResponseTimeMs}ms",
                        style = AppTypography.titleMedium,
                        color = palette.textPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    if (previous != null && analysis.responseTimeDelta != 0L) {
                        Spacer(Modifier.width(4.dp))
                        // Lower response time is better, so negative delta = improved
                        val deltaColor = if (analysis.responseTimeDelta < 0) StatusColors.Success else StatusColors.Error
                        val deltaIcon = if (analysis.responseTimeDelta < 0) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward
                        Icon(
                            imageVector = deltaIcon,
                            contentDescription = null,
                            tint = deltaColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "${kotlin.math.abs(analysis.responseTimeDelta)}ms",
                            style = AppTypography.labelSmall,
                            color = deltaColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (previous != null) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "(was ${previous.avgResponseTimeMs}ms)",
                            style = AppTypography.labelSmall,
                            color = palette.textTertiary
                        )
                    }
                }
            }

            // Tests count
            Column {
                Text(
                    text = strings.tests,
                    style = AppTypography.labelSmall,
                    color = palette.textTertiary
                )
                Text(
                    text = "${current.passedTests}/${current.totalTests}",
                    style = AppTypography.titleMedium,
                    color = palette.textPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Expandable section: new failures and fixed tests
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column(modifier = Modifier.padding(top = 12.dp)) {
                Divider(color = palette.border)
                Spacer(Modifier.height(10.dp))

                // New Failures
                if (analysis.newFailures.isNotEmpty()) {
                    Text(
                        text = "${strings.newFailures} (${analysis.newFailures.size})",
                        style = AppTypography.labelSmall,
                        color = StatusColors.Error,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(6.dp))

                    analysis.newFailures.forEach { failure ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(StatusColors.Error.copy(alpha = 0.08f))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(StatusColors.Error)
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = failure.testDescription,
                                        style = AppTypography.codeSmall,
                                        color = palette.textPrimary,
                                        fontWeight = FontWeight.Medium
                                    )
                                    if (failure.mutationType.isNotEmpty()) {
                                        Spacer(Modifier.width(6.dp))
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(3.dp))
                                                .background(palette.codeBg)
                                                .padding(horizontal = 4.dp, vertical = 1.dp)
                                        ) {
                                            Text(
                                                text = failure.mutationType,
                                                style = AppTypography.labelSmall,
                                                color = palette.textTertiary
                                            )
                                        }
                                    }
                                }
                                if (failure.errorMessage.isNotEmpty()) {
                                    Text(
                                        text = failure.errorMessage,
                                        style = AppTypography.labelSmall,
                                        color = StatusColors.Error,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                // Fixed Tests
                if (analysis.fixedTests.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "${strings.fixedTests} (${analysis.fixedTests.size})",
                        style = AppTypography.labelSmall,
                        color = StatusColors.Success,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(6.dp))

                    analysis.fixedTests.forEach { fixed ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(StatusColors.Success.copy(alpha = 0.08f))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(StatusColors.Success)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = fixed.testDescription,
                                style = AppTypography.codeSmall,
                                color = palette.textPrimary
                            )
                        }
                    }
                }

                if (analysis.newFailures.isEmpty() && analysis.fixedTests.isEmpty()) {
                    Text(
                        text = strings.noChangesBetweenRuns,
                        style = AppTypography.bodyMedium,
                        color = palette.textTertiary
                    )
                }
            }
        }
    }
}
