package com.postsyncpro.presentation.screen.autoflow

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.postsyncpro.domain.model.*
import com.postsyncpro.presentation.components.*
import com.postsyncpro.presentation.i18n.LocalAppStrings
import com.postsyncpro.presentation.theme.*
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

@Composable
fun AutoFlowScreen(viewModel: AutoFlowViewModel) {
    val state by viewModel.state.collectAsState()
    val palette = LocalAppPalette.current
    val typography = LocalAppTypography.current
    val strings = LocalAppStrings.current

    Row(modifier = Modifier.fillMaxSize()) {
        // Left Panel: Config + Plan
        Column(
            modifier = Modifier
                .width(380.dp)
                .fillMaxHeight()
                .background(palette.surface)
                .border(width = 1.dp, color = palette.border, shape = RoundedCornerShape(AppDimensions.cardRadius))
                .padding(16.dp)
        ) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.RocketLaunch, contentDescription = null, tint = BrandColors.Orange, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(strings.autoFlowRunner, style = typography.heading2.copy(color = palette.textPrimary))
                    Text(strings.fullE2eAutomation, style = typography.caption.copy(color = palette.textTertiary))
                }
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = palette.divider)
            Spacer(Modifier.height(16.dp))

            // Load File Button (Swagger OR Postman)
            val loadInteraction = remember { MutableInteractionSource() }
            val loadHovered by loadInteraction.collectIsHoveredAsState()
            val isLoaded = state.config.sourceType != null

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(AppDimensions.buttonRadius))
                    .background(if (loadHovered) palette.surfaceHover else palette.surfaceVariant)
                    .border(1.dp, if (isLoaded) BrandColors.Orange.copy(alpha = 0.3f) else palette.border, RoundedCornerShape(AppDimensions.buttonRadius))
                    .hoverable(loadInteraction)
                    .clickable {
                        val chooser = JFileChooser()
                        chooser.fileFilter = FileNameExtensionFilter("Swagger / Postman JSON", "json")
                        if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                            viewModel.loadFile(chooser.selectedFile)
                        }
                    }
                    .padding(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.FileOpen, contentDescription = null, tint = BrandColors.Orange, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            if (isLoaded) state.config.sourceName.ifBlank { strings.fileLoaded }
                            else strings.loadSwaggerOrPostman,
                            style = typography.body.copy(color = palette.textPrimary, fontWeight = FontWeight.Medium)
                        )
                        if (isLoaded) {
                            val versionStr = if (state.config.sourceVersion.isNotBlank()) "v${state.config.sourceVersion} - " else ""
                            Text(
                                "$versionStr${state.totalSteps} endpoints",
                                style = typography.caption.copy(color = palette.textTertiary)
                            )
                        } else {
                            Text(
                                strings.acceptsFormats,
                                style = typography.caption.copy(color = palette.textTertiary)
                            )
                        }
                    }
                    if (isLoaded) {
                        Spacer(Modifier.width(8.dp))
                        val badgeColor = when (state.config.sourceType) {
                            FlowSourceType.SWAGGER -> StatusColors.Info
                            FlowSourceType.POSTMAN -> BrandColors.OrangeLight
                            else -> palette.textTertiary
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(AppDimensions.chipRadius))
                                .background(badgeColor.copy(alpha = 0.15f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                when (state.config.sourceType) {
                                    FlowSourceType.SWAGGER -> strings.swagger
                                    FlowSourceType.POSTMAN -> strings.postman
                                    else -> ""
                                },
                                style = typography.caption.copy(color = badgeColor, fontWeight = FontWeight.Bold, fontSize = 9.sp)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Base URL
            OutlinedTextField(
                value = state.config.baseUrl,
                onValueChange = { viewModel.updateBaseUrl(it) },
                label = { Text(strings.baseUrl, style = typography.caption.copy(color = palette.textTertiary)) },
                placeholder = { Text("http://localhost:8080", style = typography.bodySmall.copy(color = palette.textTertiary)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = typography.code.copy(color = palette.textPrimary, fontSize = 12.sp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = BrandColors.Orange,
                    unfocusedBorderColor = palette.inputBorder,
                    cursorColor = BrandColors.Orange,
                    focusedContainerColor = palette.inputBackground,
                    unfocusedContainerColor = palette.inputBackground
                )
            )

            Spacer(Modifier.height(16.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!state.isRunning && !state.isPaused) {
                    // Run button
                    Button(
                        onClick = { viewModel.runFullFlow() },
                        enabled = state.plan != null && state.config.baseUrl.isNotBlank(),
                        modifier = Modifier.weight(1f).height(42.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BrandColors.Orange,
                            contentColor = Color.White,
                            disabledContainerColor = palette.surfaceVariant
                        ),
                        shape = RoundedCornerShape(AppDimensions.buttonRadius)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(strings.runFullFlow, style = typography.button)
                    }
                } else if (state.isRunning) {
                    // Pause button
                    Button(
                        onClick = { viewModel.pauseFlow() },
                        modifier = Modifier.weight(1f).height(42.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = StatusColors.Warning,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(AppDimensions.buttonRadius)
                    ) {
                        Icon(Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(strings.pause, style = typography.button)
                    }
                } else if (state.isPaused) {
                    // Resume button
                    Button(
                        onClick = { viewModel.resumeFlow() },
                        modifier = Modifier.weight(1f).height(42.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = StatusColors.Success,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(AppDimensions.buttonRadius)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(strings.resume, style = typography.button)
                    }
                }

                if (state.stepResults.isNotEmpty()) {
                    // Reset button
                    OutlinedButton(
                        onClick = { viewModel.resetFlow() },
                        modifier = Modifier.height(42.dp),
                        shape = RoundedCornerShape(AppDimensions.buttonRadius),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = palette.textSecondary),
                        border = BorderStroke(1.dp, palette.border)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                }
            }

            // Progress indicator
            if (state.isRunning) {
                Spacer(Modifier.height(12.dp))
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            state.currentStep?.label ?: "Running...",
                            style = typography.caption.copy(color = BrandColors.Orange)
                        )
                        Text(
                            "${state.stepResults.size}/${state.totalSteps}",
                            style = typography.caption.copy(color = palette.textTertiary)
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { if (state.totalSteps > 0) state.stepResults.size.toFloat() / state.totalSteps else 0f },
                        modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                        color = BrandColors.Orange,
                        trackColor = palette.surfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = palette.divider)
            Spacer(Modifier.height(12.dp))

            // Flow Plan
            Text(strings.flowPlan, style = typography.heading3.copy(color = palette.textPrimary))
            Spacer(Modifier.height(8.dp))

            if (state.isPlanning) {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = BrandColors.Orange, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                }
            } else if (state.plan == null) {
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f).padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.UploadFile, contentDescription = null, tint = palette.textTertiary, modifier = Modifier.size(40.dp))
                        Spacer(Modifier.height(8.dp))
                        Text(strings.loadFileForPlan, style = typography.bodySmall.copy(color = palette.textTertiary))
                    }
                }
            } else {
                state.plan?.let { plan ->
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        plan.phases.forEach { phase ->
                            item {
                                PhaseHeader(phase, state, palette, typography)
                            }
                            items(phase.steps) { step ->
                                FlowStepItem(step, state, palette, typography)
                            }
                            item { Spacer(Modifier.height(8.dp)) }
                        }
                    }
                }
            }

            // Export Button
            if (state.isComplete) {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = palette.divider)
                Spacer(Modifier.height(8.dp))

                Button(
                    onClick = {
                        val chooser = JFileChooser()
                        chooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                        chooser.dialogTitle = strings.selectExportFolder
                        if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
                            viewModel.exportResults(chooser.selectedFile)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(42.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BrandColors.Purple,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(AppDimensions.buttonRadius)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(strings.exportForFrontend, style = typography.button)
                }

                if (state.exportedFilePath != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "${strings.saved}${state.exportedFilePath}",
                        style = typography.caption.copy(color = StatusColors.Success),
                        maxLines = 2
                    )
                }
            }
        }

        Spacer(Modifier.width(12.dp))

        // Right Panel: Results
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            // Summary Cards (shown after flow starts)
            if (state.stepResults.isNotEmpty()) {
                SummaryBar(state, palette, typography)
                Spacer(Modifier.height(12.dp))
            }

            // Results List
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(palette.surface, RoundedCornerShape(AppDimensions.cardRadius))
                    .border(1.dp, palette.border, RoundedCornerShape(AppDimensions.cardRadius))
            ) {
                if (state.stepResults.isEmpty() && !state.isRunning) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Science, contentDescription = null, tint = palette.textTertiary, modifier = Modifier.size(48.dp))
                            Spacer(Modifier.height(12.dp))
                            Text(strings.runFlowToSeeResults, style = typography.body.copy(color = palette.textTertiary))
                            Text(strings.eachEndpointWillAppear, style = typography.caption.copy(color = palette.textTertiary))
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Variables section
                        if (state.variables.isNotEmpty()) {
                            item {
                                VariablesCard(state.variables, palette, typography)
                                Spacer(Modifier.height(4.dp))
                            }
                        }

                        itemsIndexed(state.stepResults) { index, result ->
                            ResultCard(index, result, palette, typography)
                        }
                    }
                }
            }
        }
    }

    // Error snackbar
    if (state.error != null) {
        LaunchedEffect(state.error) {
            kotlinx.coroutines.delay(5000)
        }
    }
}

@Composable
private fun PhaseHeader(
    phase: FlowPhase,
    state: AutoFlowState,
    palette: AppPalette,
    typography: AppTextStyles
) {
    val allSteps = state.plan?.phases?.flatMap { it.steps } ?: emptyList()
    val phaseStepIds = phase.steps.map { it.id }.toSet()
    val phaseResults = state.stepResults.filter { it.step.id in phaseStepIds }
    val phasePassed = phaseResults.count { it.passed }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(BrandColors.Orange)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            phase.name,
            style = typography.label.copy(color = BrandColors.Orange, fontWeight = FontWeight.Bold)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "${phase.steps.size} ${LocalAppStrings.current.steps}",
            style = typography.caption.copy(color = palette.textTertiary)
        )
        if (phaseResults.isNotEmpty()) {
            Spacer(Modifier.weight(1f))
            Text(
                "$phasePassed/${phaseResults.size} ${LocalAppStrings.current.passed.lowercase()}",
                style = typography.caption.copy(
                    color = if (phasePassed == phaseResults.size) StatusColors.Success else StatusColors.Warning
                )
            )
        }
    }
}

@Composable
private fun FlowStepItem(
    step: FlowStep,
    state: AutoFlowState,
    palette: AppPalette,
    typography: AppTextStyles
) {
    val result = state.stepResults.find { it.step.id == step.id }
    val allSteps = state.plan?.phases?.flatMap { it.steps } ?: emptyList()
    val stepIndex = allSteps.indexOf(step)
    val isCurrent = state.isRunning && state.currentStepIndex == stepIndex

    val statusColor = when {
        isCurrent -> BrandColors.Orange
        result != null && result.passed -> StatusColors.Success
        result != null && !result.passed -> StatusColors.Error
        else -> palette.textTertiary
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Status indicator
        Box(
            modifier = Modifier
                .size(16.dp)
                .clip(CircleShape)
                .background(statusColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            when {
                isCurrent -> CircularProgressIndicator(
                    modifier = Modifier.size(10.dp),
                    color = BrandColors.Orange,
                    strokeWidth = 1.5.dp
                )
                result?.passed == true -> Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = StatusColors.Success,
                    modifier = Modifier.size(10.dp)
                )
                result?.passed == false -> Icon(
                    Icons.Default.Close,
                    contentDescription = null,
                    tint = StatusColors.Error,
                    modifier = Modifier.size(10.dp)
                )
                else -> Box(
                    modifier = Modifier.size(4.dp).clip(CircleShape).background(palette.textTertiary)
                )
            }
        }

        Spacer(Modifier.width(8.dp))

        HttpMethodBadge(step.method, compact = true)

        Spacer(Modifier.width(8.dp))

        Text(
            step.path,
            style = typography.codeSmall.copy(
                color = if (isCurrent) BrandColors.Orange else palette.textSecondary
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        if (result != null) {
            Spacer(Modifier.width(6.dp))
            HttpStatusBadge(result.httpStatus)
        }
    }
}

@Composable
private fun SummaryBar(
    state: AutoFlowState,
    palette: AppPalette,
    typography: AppTextStyles
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(palette.surface, RoundedCornerShape(AppDimensions.cardRadius))
            .border(1.dp, palette.border, RoundedCornerShape(AppDimensions.cardRadius))
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        val strings = LocalAppStrings.current
        SummaryItem(strings.total, "${state.stepResults.size}/${state.totalSteps}", BrandColors.Orange, palette, typography)
        SummaryItem(strings.passed, "${state.passedSteps}", StatusColors.Success, palette, typography)
        SummaryItem(strings.failed, "${state.failedSteps}", StatusColors.Error, palette, typography)
        SummaryItem(strings.passRate, "${(state.passRate * 100).toInt()}%", if (state.passRate >= 0.8f) StatusColors.Success else StatusColors.Warning, palette, typography)
        SummaryItem("Avg Time", "${state.avgResponseTime}ms", StatusColors.Info, palette, typography)
        SummaryItem(strings.variables, "${state.variables.size}", BrandColors.Purple, palette, typography)
    }
}

@Composable
private fun SummaryItem(
    label: String,
    value: String,
    color: Color,
    palette: AppPalette,
    typography: AppTextStyles
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = typography.heading3.copy(color = color, fontWeight = FontWeight.Bold))
        Text(label, style = typography.caption.copy(color = palette.textTertiary))
    }
}

@Composable
private fun VariablesCard(
    variables: Map<String, String>,
    palette: AppPalette,
    typography: AppTextStyles
) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(BrandColors.Purple.copy(alpha = 0.08f), RoundedCornerShape(AppDimensions.cardRadius))
            .border(1.dp, BrandColors.Purple.copy(alpha = 0.2f), RoundedCornerShape(AppDimensions.cardRadius))
            .clickable { expanded = !expanded }
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.DataObject, contentDescription = null, tint = BrandColors.Purple, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                "${LocalAppStrings.current.extractedVariables} (${variables.size})",
                style = typography.body.copy(color = BrandColors.Purple, fontWeight = FontWeight.Medium)
            )
            Spacer(Modifier.weight(1f))
            Icon(
                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = BrandColors.Purple,
                modifier = Modifier.size(16.dp)
            )
        }

        if (expanded) {
            Spacer(Modifier.height(8.dp))
            variables.forEach { (key, value) ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        key,
                        style = typography.codeSmall.copy(color = BrandColors.PurpleLight),
                        modifier = Modifier.width(120.dp)
                    )
                    Text(" = ", style = typography.codeSmall.copy(color = palette.textTertiary))
                    Text(
                        if (value.length > 50) value.take(50) + "..." else value,
                        style = typography.codeSmall.copy(color = palette.textSecondary),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun ResultCard(
    index: Int,
    result: FlowStepResult,
    palette: AppPalette,
    typography: AppTextStyles
) {
    var expanded by remember { mutableStateOf(false) }

    val borderColor = if (result.passed) StatusColors.Success.copy(alpha = 0.3f) else StatusColors.Error.copy(alpha = 0.3f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(palette.surfaceVariant, RoundedCornerShape(AppDimensions.cardRadius))
            .border(1.dp, borderColor, RoundedCornerShape(AppDimensions.cardRadius))
            .clickable { expanded = !expanded }
            .padding(12.dp)
    ) {
        // Header row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Step number
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(if (result.passed) StatusColors.Success.copy(alpha = 0.15f) else StatusColors.Error.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "${index + 1}",
                    style = typography.caption.copy(
                        color = if (result.passed) StatusColors.Success else StatusColors.Error,
                        fontWeight = FontWeight.Bold
                    )
                )
            }

            Spacer(Modifier.width(8.dp))

            HttpMethodBadge(result.step.method)

            Spacer(Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    result.step.path,
                    style = typography.code.copy(color = palette.textPrimary, fontSize = 12.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${result.step.category.displayName} - ${result.responseTimeMs}ms",
                    style = typography.caption.copy(color = palette.textTertiary)
                )
            }

            Spacer(Modifier.width(8.dp))
            HttpStatusBadge(result.httpStatus)

            Spacer(Modifier.width(4.dp))
            Icon(
                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = palette.textTertiary,
                modifier = Modifier.size(16.dp)
            )
        }

        // Expanded content
        if (expanded) {
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = palette.divider)
            Spacer(Modifier.height(12.dp))

            // Request Headers
            if (result.requestHeaders.isNotEmpty()) {
                SectionTitle(LocalAppStrings.current.requestHeaders, palette, typography)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(palette.codeBg, RoundedCornerShape(6.dp))
                        .padding(10.dp)
                ) {
                    Text(
                        result.requestHeaders.entries.joinToString("\n") { "${it.key}: ${it.value}" },
                        style = typography.codeSmall.copy(color = palette.textSecondary)
                    )
                }
                Spacer(Modifier.height(10.dp))
            }

            // Request Body
            if (result.requestBody.isNotBlank()) {
                SectionTitle(LocalAppStrings.current.requestBody, palette, typography)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp)
                        .background(palette.codeBg, RoundedCornerShape(6.dp))
                        .verticalScroll(rememberScrollState())
                        .padding(10.dp)
                ) {
                    Text(
                        formatJson(result.requestBody),
                        style = typography.codeSmall.copy(color = AppColors.jsonString)
                    )
                }
                Spacer(Modifier.height(10.dp))
            }

            // Response Body
            if (result.responseBody.isNotBlank()) {
                SectionTitle(LocalAppStrings.current.responseBody, palette, typography)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp)
                        .background(palette.codeBg, RoundedCornerShape(6.dp))
                        .verticalScroll(rememberScrollState())
                        .padding(10.dp)
                ) {
                    Text(
                        formatJson(result.responseBody),
                        style = typography.codeSmall.copy(color = AppColors.jsonString)
                    )
                }
                Spacer(Modifier.height(10.dp))
            }

            // Extracted Variables
            if (result.extractedValues.isNotEmpty()) {
                SectionTitle(LocalAppStrings.current.extractedVariables, palette, typography)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(BrandColors.Purple.copy(alpha = 0.06f), RoundedCornerShape(6.dp))
                        .padding(10.dp)
                ) {
                    result.extractedValues.forEach { (key, value) ->
                        Row {
                            Text("$key", style = typography.codeSmall.copy(color = BrandColors.PurpleLight, fontWeight = FontWeight.Bold))
                            Text(" = ", style = typography.codeSmall.copy(color = palette.textTertiary))
                            Text(
                                if (value.length > 60) value.take(60) + "..." else value,
                                style = typography.codeSmall.copy(color = palette.textSecondary)
                            )
                        }
                    }
                }
            }

            // Error
            if (result.error != null) {
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(StatusColors.Error.copy(alpha = 0.1f), RoundedCornerShape(6.dp))
                        .padding(10.dp)
                ) {
                    Text(result.error, style = typography.codeSmall.copy(color = StatusColors.Error))
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String, palette: AppPalette, typography: AppTextStyles) {
    Text(
        title,
        style = typography.label.copy(color = palette.textTertiary),
        modifier = Modifier.padding(bottom = 4.dp)
    )
}

private fun formatJson(json: String): String {
    return try {
        val element = kotlinx.serialization.json.Json.parseToJsonElement(json)
        kotlinx.serialization.json.Json { prettyPrint = true }.encodeToString(kotlinx.serialization.json.JsonElement.serializer(), element)
    } catch (e: Exception) {
        json
    }
}
