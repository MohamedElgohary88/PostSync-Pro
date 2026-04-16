package com.postsyncpro.presentation.screen.smarttests

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.postsyncpro.presentation.i18n.LocalAppStrings
import com.postsyncpro.presentation.theme.*

val PinkAccent = Color(0xFFEC4899)
val PinkLight = Color(0xFFFCE7F3)

@Composable
fun SmartTestsScreen(viewModel: SmartTestsViewModel) {
    val strings = LocalAppStrings.current
    val palette = LocalAppPalette.current
    val typography = LocalAppTypography.current
    val state by viewModel.state.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(strings.smartTestsTitle, style = typography.heading1.copy(color = palette.textPrimary))
                Spacer(Modifier.height(4.dp))
                Text(strings.smartTestsSubtitle, style = typography.bodySmall.copy(color = palette.textTertiary))
            }
            Spacer(Modifier.width(16.dp))

            if (!state.specLoaded) {
                val loadInteraction = remember { MutableInteractionSource() }
                val loadHovered by loadInteraction.collectIsHoveredAsState()
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(AppDimensions.buttonRadius))
                        .background(if (loadHovered) PinkAccent else PinkAccent.copy(alpha = 0.9f))
                        .hoverable(loadInteraction)
                        .clickable { viewModel.loadSpec("sample-api-spec.json") }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.FileOpen, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(strings.smartTestsLoadSpec, style = typography.body.copy(color = Color.White, fontWeight = FontWeight.SemiBold))
                }
            } else if (state.scenarios.isEmpty() && !state.isGenerating) {
                val genInteraction = remember { MutableInteractionSource() }
                val genHovered by genInteraction.collectIsHoveredAsState()
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(AppDimensions.buttonRadius))
                        .background(if (genHovered) PinkAccent else PinkAccent.copy(alpha = 0.9f))
                        .hoverable(genInteraction)
                        .clickable { viewModel.generateTests() }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Psychology, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(strings.smartTestsGenerate, style = typography.body.copy(color = Color.White, fontWeight = FontWeight.SemiBold))
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // Generating state
        if (state.isGenerating) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(AppDimensions.cardRadius))
                    .background(PinkAccent.copy(alpha = 0.08f))
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = PinkAccent, modifier = Modifier.size(40.dp))
                    Spacer(Modifier.height(12.dp))
                    Text(strings.smartTestsGenerating, style = typography.body.copy(color = PinkAccent, fontWeight = FontWeight.SemiBold))
                }
            }
            return@Column
        }

        // Empty state
        if (state.scenarios.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(AppDimensions.cardRadius))
                    .border(AppDimensions.borderWidth, palette.borderSubtle, RoundedCornerShape(AppDimensions.cardRadius))
                    .background(palette.surface)
                    .padding(48.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Psychology, contentDescription = null, tint = palette.textTertiary, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(12.dp))
                    Text(strings.smartTestsNoScenarios, style = typography.body.copy(color = palette.textTertiary))
                }
            }
            return@Column
        }

        // Stats cards
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(strings.smartTestsScenarioCount, "${state.generatedCount}", PinkAccent, palette, typography, Modifier.weight(1f))
            StatCard(strings.smartTestsEdgeCases, "${state.edgeCaseCount}", BrandColors.Orange, palette, typography, Modifier.weight(1f))
            StatCard(strings.smartTestsSecurityTests, "${state.securityCount}", StatusColors.Error, palette, typography, Modifier.weight(1f))
            StatCard(strings.smartTestsPerformanceTests, "${state.performanceCount}", BrandColors.Blue, palette, typography, Modifier.weight(1f))
            StatCard(strings.smartTestsBusinessLogic, "${state.businessLogicCount}", BrandColors.Purple, palette, typography, Modifier.weight(1f))
        }

        Spacer(Modifier.height(20.dp))

        // Category filter + Run All
        Row(verticalAlignment = Alignment.CenterVertically) {
            val categories = listOf<Pair<TestCategory?, String>>(
                null to strings.testHistoryAll,
                TestCategory.EDGE_CASE to strings.smartTestsEdgeCases,
                TestCategory.SECURITY to strings.smartTestsSecurityTests,
                TestCategory.PERFORMANCE to strings.smartTestsPerformanceTests,
                TestCategory.BUSINESS_LOGIC to strings.smartTestsBusinessLogic,
            )
            categories.forEach { (cat, label) ->
                val isSelected = state.selectedCategory == cat
                val chipInteraction = remember { MutableInteractionSource() }
                val chipHovered by chipInteraction.collectIsHoveredAsState()
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(AppDimensions.chipRadius))
                        .background(
                            when {
                                isSelected -> PinkAccent
                                chipHovered -> PinkAccent.copy(alpha = 0.1f)
                                else -> palette.surface
                            }
                        )
                        .border(
                            AppDimensions.borderWidth,
                            if (isSelected) PinkAccent else palette.borderSubtle,
                            RoundedCornerShape(AppDimensions.chipRadius)
                        )
                        .hoverable(chipInteraction)
                        .clickable { viewModel.setCategory(cat) }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        label,
                        style = typography.bodySmall.copy(
                            color = if (isSelected) Color.White else palette.textSecondary,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                        )
                    )
                }
                Spacer(Modifier.width(8.dp))
            }

            Spacer(Modifier.weight(1f))

            // Run All button
            if (!state.isRunning) {
                val runInteraction = remember { MutableInteractionSource() }
                val runHovered by runInteraction.collectIsHoveredAsState()
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(AppDimensions.buttonRadius))
                        .background(if (runHovered) StatusColors.Success else StatusColors.Success.copy(alpha = 0.9f))
                        .hoverable(runInteraction)
                        .clickable { viewModel.runAllScenarios() }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(strings.smartTestsRunAll, style = typography.bodySmall.copy(color = Color.White, fontWeight = FontWeight.SemiBold))
                }
            }
        }

        // Progress bar
        if (state.isRunning) {
            Spacer(Modifier.height(12.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(color = PinkAccent, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("${state.runProgress}%", style = typography.body.copy(color = PinkAccent, fontWeight = FontWeight.Bold))
                }
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { state.runProgress / 100f },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color = PinkAccent,
                    trackColor = palette.borderSubtle,
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // Scenario list
        val filtered = state.scenarios.let { list ->
            if (state.selectedCategory != null) list.filter { it.category == state.selectedCategory } else list
        }

        filtered.forEach { scenario ->
            ScenarioCard(scenario, palette, typography)
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun StatCard(
    label: String,
    value: String,
    accent: Color,
    palette: AppPalette,
    typography: AppTextStyles,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(AppDimensions.cardRadius))
            .border(AppDimensions.borderWidth, palette.borderSubtle, RoundedCornerShape(AppDimensions.cardRadius))
            .background(palette.surface)
            .padding(16.dp)
    ) {
        Column {
            Text(value, style = typography.heading1.copy(color = accent, fontWeight = FontWeight.Bold))
            Spacer(Modifier.height(4.dp))
            Text(label, style = typography.caption.copy(color = palette.textTertiary), maxLines = 1)
        }
    }
}

@Composable
private fun ScenarioCard(
    scenario: SmartTestScenario,
    palette: AppPalette,
    typography: AppTextStyles
) {
    val strings = LocalAppStrings.current
    val interaction = remember { MutableInteractionSource() }
    val isHovered by interaction.collectIsHoveredAsState()

    val categoryColor = when (scenario.category) {
        TestCategory.EDGE_CASE -> BrandColors.Orange
        TestCategory.SECURITY -> StatusColors.Error
        TestCategory.PERFORMANCE -> BrandColors.Blue
        TestCategory.BUSINESS_LOGIC -> BrandColors.Purple
    }

    val categoryLabel = when (scenario.category) {
        TestCategory.EDGE_CASE -> strings.smartTestsEdgeCases
        TestCategory.SECURITY -> strings.smartTestsSecurityTests
        TestCategory.PERFORMANCE -> strings.smartTestsPerformanceTests
        TestCategory.BUSINESS_LOGIC -> strings.smartTestsBusinessLogic
    }

    val priorityColor = when (scenario.priority) {
        TestPriority.HIGH -> StatusColors.Error
        TestPriority.MEDIUM -> StatusColors.Warning
        TestPriority.LOW -> StatusColors.Success
    }

    val priorityLabel = when (scenario.priority) {
        TestPriority.HIGH -> strings.smartTestsHigh
        TestPriority.MEDIUM -> strings.smartTestsMedium
        TestPriority.LOW -> strings.smartTestsLow
    }

    val borderColor by animateColorAsState(
        if (isHovered) categoryColor else palette.borderSubtle
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppDimensions.cardRadius))
            .border(AppDimensions.borderWidth, borderColor, RoundedCornerShape(AppDimensions.cardRadius))
            .background(if (isHovered) categoryColor.copy(alpha = 0.03f) else palette.surface)
            .hoverable(interaction)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            // Result indicator
            if (scenario.passed != null) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(
                            if (scenario.passed == true) StatusColors.Success.copy(alpha = 0.12f)
                            else StatusColors.Error.copy(alpha = 0.12f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (scenario.passed == true) Icons.Default.Check else Icons.Default.Close,
                        contentDescription = null,
                        tint = if (scenario.passed == true) StatusColors.Success else StatusColors.Error,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(scenario.name, style = typography.body.copy(color = palette.textPrimary, fontWeight = FontWeight.SemiBold))
                    Spacer(Modifier.width(8.dp))
                    // Category badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(AppDimensions.chipRadius))
                            .background(categoryColor.copy(alpha = 0.12f))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(categoryLabel, style = typography.caption.copy(color = categoryColor, fontWeight = FontWeight.Bold))
                    }
                    Spacer(Modifier.width(6.dp))
                    // Priority badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(AppDimensions.chipRadius))
                            .background(priorityColor.copy(alpha = 0.12f))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(priorityLabel, style = typography.caption.copy(color = priorityColor, fontWeight = FontWeight.Bold))
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(scenario.description, style = typography.bodySmall.copy(color = palette.textSecondary))
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Endpoint badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(palette.background)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            scenario.endpoint,
                            style = typography.caption.copy(
                                color = palette.textTertiary,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                            )
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "${strings.smartTestsExpectedResult}: ${scenario.expectedResult}",
                        style = typography.caption.copy(color = palette.textTertiary),
                        maxLines = 1
                    )
                }
            }
        }
    }
}
