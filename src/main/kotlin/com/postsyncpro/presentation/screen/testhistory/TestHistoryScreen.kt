package com.postsyncpro.presentation.screen.testhistory

import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
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
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.postsyncpro.presentation.components.*
import com.postsyncpro.presentation.i18n.LocalAppStrings
import com.postsyncpro.presentation.theme.*

private val AccentBlue = BrandColors.Blue

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TestHistoryScreen(viewModel: TestHistoryViewModel) {
    val state by viewModel.state.collectAsState()
    val strings = LocalAppStrings.current
    val palette = LocalAppPalette.current
    val typography = LocalAppTypography.current

    val filteredRuns = when (state.filter) {
        TestFilter.ALL -> state.runs
        TestFilter.FUZZ -> state.runs.filter { it.testType == "Fuzz" }
        TestFilter.REGRESSION -> state.runs.filter { it.testType == "Regression" }
        TestFilter.CHAIN -> state.runs.filter { it.testType == "Chain" }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
            .padding(AppDimensions.spacingLg)
    ) {
        // ── Title ────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.History,
                contentDescription = null,
                tint = AccentBlue,
                modifier = Modifier.size(AppDimensions.iconSizeLg)
            )
            Spacer(Modifier.width(AppDimensions.spacingSm))
            Column {
                Text(
                    text = strings.testHistoryTitle,
                    style = typography.heading1.copy(color = palette.textPrimary)
                )
                Text(
                    text = strings.testHistorySubtitle,
                    style = typography.bodySmall.copy(color = palette.textSecondary)
                )
            }
        }

        Spacer(Modifier.height(AppDimensions.spacingLg))

        // ── Stats Cards Row ──────────────────────────────────────
        StatsCardsRow(state = state, palette = palette, typography = typography)

        Spacer(Modifier.height(AppDimensions.spacingLg))

        // ── Filter Tabs ──────────────────────────────────────────
        FilterTabs(
            currentFilter = state.filter,
            onFilterSelected = { viewModel.setFilter(it) },
            palette = palette,
            typography = typography
        )

        Spacer(Modifier.height(AppDimensions.spacingLg))

        // ── Bar Chart ────────────────────────────────────────────
        if (filteredRuns.isNotEmpty()) {
            SectionCard(
                title = strings.testHistoryPassRate,
                icon = Icons.Default.BarChart,
                accentColor = AccentBlue,
                badge = "${filteredRuns.size} ${strings.testHistoryTotalRuns.lowercase()}"
            ) {
                PassRateBarChart(
                    runs = filteredRuns,
                    selectedRunId = state.selectedRunId,
                    onBarClick = { viewModel.selectRun(it) },
                    palette = palette,
                    typography = typography
                )
            }

            Spacer(Modifier.height(AppDimensions.spacingLg))

            // ── Runs List ────────────────────────────────────────
            SectionCard(
                title = strings.testHistoryTimeline,
                icon = Icons.Default.List,
                accentColor = AccentBlue,
                badge = "${filteredRuns.size}"
            ) {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(AppDimensions.spacingSm)
                ) {
                    items(filteredRuns, key = { it.id }) { run ->
                        RunListItem(
                            run = run,
                            isSelected = run.id == state.selectedRunId,
                            onClick = { viewModel.selectRun(run.id) },
                            palette = palette,
                            typography = typography
                        )
                    }
                }
            }
        } else {
            // ── Empty State ──────────────────────────────────────
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        tint = palette.textTertiary,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(Modifier.height(AppDimensions.spacingMd))
                    Text(
                        text = strings.testHistoryNoData,
                        style = typography.body.copy(color = palette.textTertiary)
                    )
                }
            }
        }
    }
}

// ─── Stats Cards ────────────────────────────────────────────────────────────

@Composable
private fun StatsCardsRow(
    state: TestHistoryState,
    palette: AppPalette,
    typography: AppTextStyles
) {
    val strings = LocalAppStrings.current

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AppDimensions.spacingMd)
    ) {
        StatCard(
            title = strings.testHistoryTotalRuns,
            value = "${state.totalRuns}",
            icon = Icons.Default.PlayArrow,
            color = AccentBlue,
            palette = palette,
            typography = typography,
            modifier = Modifier.weight(1f)
        )
        StatCard(
            title = strings.testHistoryAvgPassRate,
            value = "%.1f%%".format(state.avgPassRate),
            icon = Icons.Default.TrendingUp,
            color = passRateColor(state.avgPassRate),
            palette = palette,
            typography = typography,
            modifier = Modifier.weight(1f)
        )
        StatCard(
            title = strings.testHistoryBestRun,
            value = "%.1f%%".format(state.bestRunRate),
            icon = Icons.Default.EmojiEvents,
            color = StatusColors.Success,
            palette = palette,
            typography = typography,
            modifier = Modifier.weight(1f)
        )
        StatCard(
            title = strings.testHistoryWorstRun,
            value = "%.1f%%".format(state.worstRunRate),
            icon = Icons.Default.Warning,
            color = StatusColors.Error,
            palette = palette,
            typography = typography,
            modifier = Modifier.weight(1f)
        )
        TrendCard(
            trend = state.trend,
            palette = palette,
            typography = typography,
            modifier = Modifier.weight(1f)
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun StatCard(
    title: String,
    value: String,
    icon: ImageVector,
    color: Color,
    palette: AppPalette,
    typography: AppTextStyles,
    modifier: Modifier = Modifier
) {
    var isHovered by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(AppDimensions.cardRadius))
            .background(if (isHovered) palette.surfaceHover else palette.surface)
            .border(
                width = AppDimensions.borderWidth,
                color = if (isHovered) color.copy(alpha = 0.4f) else palette.border,
                shape = RoundedCornerShape(AppDimensions.cardRadius)
            )
            .onPointerEvent(PointerEventType.Enter) { isHovered = true }
            .onPointerEvent(PointerEventType.Exit) { isHovered = false }
            .padding(AppDimensions.spacingMd),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(AppDimensions.iconSizeMd)
            )
        }
        Spacer(Modifier.height(AppDimensions.spacingSm))
        Text(
            text = value,
            style = typography.heading2.copy(color = palette.textPrimary)
        )
        Spacer(Modifier.height(AppDimensions.spacingXs))
        Text(
            text = title,
            style = typography.caption.copy(color = palette.textSecondary),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun TrendCard(
    trend: TrendDirection,
    palette: AppPalette,
    typography: AppTextStyles,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    val (icon, color, label) = when (trend) {
        TrendDirection.UP -> Triple(Icons.Default.TrendingUp, StatusColors.Success, strings.testHistoryUp)
        TrendDirection.DOWN -> Triple(Icons.Default.TrendingDown, StatusColors.Error, strings.testHistoryDown)
        TrendDirection.STABLE -> Triple(Icons.Default.TrendingFlat, StatusColors.Warning, strings.testHistoryStable)
    }

    var isHovered by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(AppDimensions.cardRadius))
            .background(if (isHovered) palette.surfaceHover else palette.surface)
            .border(
                width = AppDimensions.borderWidth,
                color = if (isHovered) color.copy(alpha = 0.4f) else palette.border,
                shape = RoundedCornerShape(AppDimensions.cardRadius)
            )
            .onPointerEvent(PointerEventType.Enter) { isHovered = true }
            .onPointerEvent(PointerEventType.Exit) { isHovered = false }
            .padding(AppDimensions.spacingMd),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(AppDimensions.iconSizeMd)
            )
        }
        Spacer(Modifier.height(AppDimensions.spacingSm))
        Text(
            text = label,
            style = typography.heading2.copy(color = color)
        )
        Spacer(Modifier.height(AppDimensions.spacingXs))
        Text(
            text = strings.testHistoryTrending,
            style = typography.caption.copy(color = palette.textSecondary),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ─── Filter Tabs ────────────────────────────────────────────────────────────

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun FilterTabs(
    currentFilter: TestFilter,
    onFilterSelected: (TestFilter) -> Unit,
    palette: AppPalette,
    typography: AppTextStyles
) {
    val strings = LocalAppStrings.current
    val tabs = listOf(
        TestFilter.ALL to strings.testHistoryAll,
        TestFilter.FUZZ to strings.testHistoryFuzz,
        TestFilter.REGRESSION to strings.testHistoryRegression,
        TestFilter.CHAIN to strings.testHistoryChain
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AppDimensions.spacingSm)
    ) {
        tabs.forEach { (filter, label) ->
            val isSelected = currentFilter == filter
            var isHovered by remember { mutableStateOf(false) }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(AppDimensions.buttonRadius))
                    .background(
                        when {
                            isSelected -> AccentBlue
                            isHovered -> palette.surfaceHover
                            else -> palette.surface
                        }
                    )
                    .border(
                        width = AppDimensions.borderWidth,
                        color = if (isSelected) AccentBlue else palette.border,
                        shape = RoundedCornerShape(AppDimensions.buttonRadius)
                    )
                    .hoverable(interactionSource = remember { MutableInteractionSource() })
                    .onPointerEvent(PointerEventType.Enter) { isHovered = true }
                    .onPointerEvent(PointerEventType.Exit) { isHovered = false }
                    .clickable { onFilterSelected(filter) }
                    .padding(horizontal = AppDimensions.spacingMd, vertical = AppDimensions.spacingSm)
            ) {
                Text(
                    text = label,
                    style = typography.button.copy(
                        color = if (isSelected) Color.White else palette.textSecondary
                    )
                )
            }
        }
    }
}

// ─── Bar Chart ──────────────────────────────────────────────────────────────

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun PassRateBarChart(
    runs: List<TestRun>,
    selectedRunId: String?,
    onBarClick: (String?) -> Unit,
    palette: AppPalette,
    typography: AppTextStyles
) {
    val chartHeight = 180.dp
    val barWidth = 28.dp
    val barSpacing = 6.dp
    val scrollState = rememberScrollState()

    Column {
        // Y-axis labels and bars
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(chartHeight)
        ) {
            // Y-axis labels
            Column(
                modifier = Modifier
                    .width(40.dp)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                listOf("100%", "75%", "50%", "25%", "0%").forEach { label ->
                    Text(
                        text = label,
                        style = typography.caption.copy(color = palette.textTertiary),
                        modifier = Modifier.padding(end = 4.dp)
                    )
                }
            }

            // Bars area
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 44.dp)
                    .horizontalScroll(scrollState),
                horizontalArrangement = Arrangement.spacedBy(barSpacing),
                verticalAlignment = Alignment.Bottom
            ) {
                runs.forEachIndexed { _, run ->
                    val isSelected = run.id == selectedRunId
                    val barColor = passRateColor(run.passRate)
                    var isHovered by remember { mutableStateOf(false) }
                    val barFraction = (run.passRate / 100.0).coerceIn(0.0, 1.0)

                    Column(
                        modifier = Modifier.width(barWidth),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Pass rate label on top of bar
                        if (isHovered || isSelected) {
                            Text(
                                text = "%.0f%%".format(run.passRate),
                                style = typography.caption.copy(
                                    color = barColor,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }

                        Box(
                            modifier = Modifier
                                .width(barWidth)
                                .fillMaxHeight(barFraction.toFloat())
                                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                .background(
                                    if (isSelected) barColor
                                    else if (isHovered) barColor.copy(alpha = 0.85f)
                                    else barColor.copy(alpha = 0.65f)
                                )
                                .then(
                                    if (isSelected) Modifier.border(
                                        width = 2.dp,
                                        color = AccentBlue,
                                        shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)
                                    ) else Modifier
                                )
                                .hoverable(interactionSource = remember { MutableInteractionSource() })
                                .onPointerEvent(PointerEventType.Enter) { isHovered = true }
                    .onPointerEvent(PointerEventType.Exit) { isHovered = false }
                                .clickable {
                                    onBarClick(if (isSelected) null else run.id)
                                }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(AppDimensions.spacingXs))

        // X-axis labels (timestamps)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 44.dp)
                .horizontalScroll(scrollState),
            horizontalArrangement = Arrangement.spacedBy(barSpacing)
        ) {
            runs.forEach { run ->
                val shortTimestamp = run.timestamp.substringAfter(" ")
                Text(
                    text = shortTimestamp,
                    style = typography.caption.copy(color = palette.textTertiary),
                    modifier = Modifier.width(barWidth),
                    maxLines = 1,
                    overflow = TextOverflow.Clip
                )
            }
        }
    }
}

// ─── Run List Item ──────────────────────────────────────────────────────────

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun RunListItem(
    run: TestRun,
    isSelected: Boolean,
    onClick: () -> Unit,
    palette: AppPalette,
    typography: AppTextStyles
) {
    val strings = LocalAppStrings.current
    var isHovered by remember { mutableStateOf(false) }
    val rateColor = passRateColor(run.passRate)
    val badgeColor = testTypeBadgeColor(run.testType)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppDimensions.cardRadius))
            .background(
                when {
                    isSelected -> AccentBlue.copy(alpha = 0.1f)
                    isHovered -> palette.surfaceHover
                    else -> palette.surfaceVariant
                }
            )
            .border(
                width = AppDimensions.borderWidth,
                color = if (isSelected) AccentBlue.copy(alpha = 0.5f) else palette.borderSubtle,
                shape = RoundedCornerShape(AppDimensions.cardRadius)
            )
            .onPointerEvent(PointerEventType.Enter) { isHovered = true }
            .onPointerEvent(PointerEventType.Exit) { isHovered = false }
            .clickable { onClick() }
            .padding(horizontal = AppDimensions.spacingMd, vertical = AppDimensions.spacingSm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppDimensions.spacingMd)
    ) {
        // Timestamp
        Column(modifier = Modifier.width(120.dp)) {
            Text(
                text = run.timestamp.substringBefore(" "),
                style = typography.bodySmall.copy(color = palette.textPrimary),
                maxLines = 1
            )
            Text(
                text = run.timestamp.substringAfter(" "),
                style = typography.caption.copy(color = palette.textTertiary),
                maxLines = 1
            )
        }

        // Type badge
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(AppDimensions.chipRadius))
                .background(badgeColor.copy(alpha = 0.15f))
                .padding(horizontal = AppDimensions.spacingSm, vertical = AppDimensions.spacingXs)
        ) {
            Text(
                text = run.testType,
                style = typography.caption.copy(
                    color = badgeColor,
                    fontWeight = FontWeight.Bold
                )
            )
        }

        Spacer(Modifier.weight(1f))

        // Pass rate bar
        Column(
            modifier = Modifier.width(120.dp),
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text = "%.1f%%".format(run.passRate),
                style = typography.body.copy(
                    color = rateColor,
                    fontWeight = FontWeight.Bold
                )
            )
            Spacer(Modifier.height(2.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(palette.codeBg)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth((run.passRate / 100.0).toFloat().coerceIn(0f, 1f))
                        .clip(RoundedCornerShape(2.dp))
                        .background(rateColor)
                )
            }
        }

        // Tests count
        Column(
            modifier = Modifier.width(80.dp),
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text = "${run.passed}/${run.totalTests}",
                style = typography.bodySmall.copy(color = palette.textPrimary),
                maxLines = 1
            )
            Text(
                text = "${strings.passed} / ${strings.total}",
                style = typography.caption.copy(color = palette.textTertiary),
                maxLines = 1
            )
        }

        // Duration
        Column(
            modifier = Modifier.width(60.dp),
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text = "${run.duration}",
                style = typography.bodySmall.copy(color = palette.textSecondary),
                maxLines = 1
            )
            Text(
                text = strings.ms,
                style = typography.caption.copy(color = palette.textTertiary),
                maxLines = 1
            )
        }
    }
}

// ─── Utilities ──────────────────────────────────────────────────────────────

private fun passRateColor(rate: Double): Color = when {
    rate >= 90.0 -> StatusColors.Success
    rate >= 70.0 -> StatusColors.Warning
    else -> StatusColors.Error
}

private fun testTypeBadgeColor(type: String): Color = when (type) {
    "Fuzz" -> BrandColors.Orange
    "Regression" -> BrandColors.Purple
    "Chain" -> BrandColors.Blue
    else -> HttpMethodColors.OPTIONS
}

