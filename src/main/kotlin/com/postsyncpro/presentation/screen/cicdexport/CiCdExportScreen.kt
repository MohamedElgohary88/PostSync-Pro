package com.postsyncpro.presentation.screen.cicdexport

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.postsyncpro.presentation.i18n.LocalAppStrings
import com.postsyncpro.presentation.theme.*
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

private val Indigo = Color(0xFF6366F1)
private val IndigoLight = Color(0xFF818CF8)
private val IndigoDark = BrandColors.Orange

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun CiCdExportScreen(viewModel: CiCdExportViewModel) {
    val state by viewModel.state.collectAsState()
    val strings = LocalAppStrings.current
    val palette = LocalAppPalette.current
    val typography = LocalAppTypography.current

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
            .padding(AppDimensions.spacingMd),
        horizontalArrangement = Arrangement.spacedBy(AppDimensions.spacingMd)
    ) {
        // ── Left Panel: Configuration ──────────────────────────────
        Column(
            modifier = Modifier
                .weight(0.4f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(AppDimensions.spacingMd)
        ) {
            // Header
            Text(
                text = strings.cicdTitle,
                style = typography.heading1,
                color = palette.textPrimary
            )
            Text(
                text = strings.cicdSubtitle,
                style = typography.body,
                color = palette.textSecondary
            )

            Spacer(modifier = Modifier.height(AppDimensions.spacingSm))

            // ── Pipeline Provider Selector ──────────────────────────
            Text(
                text = strings.cicdSelectPipeline,
                style = typography.heading3,
                color = palette.textPrimary
            )

            val providers = listOf(
                PipelineProvider.GITHUB_ACTIONS to strings.cicdGitHubActions,
                PipelineProvider.GITLAB_CI to strings.cicdGitLabCi,
                PipelineProvider.JENKINS to strings.cicdJenkins,
                PipelineProvider.CIRCLE_CI to strings.cicdCircleCi
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(AppDimensions.spacingSm)
            ) {
                providers.chunked(2).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(AppDimensions.spacingSm)
                    ) {
                        row.forEach { (provider, label) ->
                            ProviderCard(
                                label = label,
                                isSelected = state.config.provider == provider,
                                palette = palette,
                                typography = typography,
                                onClick = {
                                    viewModel.updateConfig(state.config.copy(provider = provider))
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        // Fill remaining space if odd number
                        if (row.size < 2) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(AppDimensions.spacingSm))

            // ── Test Types Checkboxes ───────────────────────────────
            Text(
                text = strings.cicdTestTypes,
                style = typography.heading3,
                color = palette.textPrimary
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(palette.surface, RoundedCornerShape(AppDimensions.cardRadius))
                    .border(AppDimensions.borderWidth, palette.border, RoundedCornerShape(AppDimensions.cardRadius))
                    .padding(AppDimensions.spacingMd),
                verticalArrangement = Arrangement.spacedBy(AppDimensions.spacingXs)
            ) {
                CheckboxRow(
                    label = strings.cicdIncludeFuzz,
                    checked = state.config.includeFuzz,
                    onCheckedChange = { viewModel.updateConfig(state.config.copy(includeFuzz = it)) },
                    palette = palette,
                    typography = typography
                )
                CheckboxRow(
                    label = strings.cicdIncludeRegression,
                    checked = state.config.includeRegression,
                    onCheckedChange = { viewModel.updateConfig(state.config.copy(includeRegression = it)) },
                    palette = palette,
                    typography = typography
                )
                CheckboxRow(
                    label = strings.cicdIncludeChain,
                    checked = state.config.includeChain,
                    onCheckedChange = { viewModel.updateConfig(state.config.copy(includeChain = it)) },
                    palette = palette,
                    typography = typography
                )
                CheckboxRow(
                    label = strings.cicdIncludeCoverage,
                    checked = state.config.includeCoverage,
                    onCheckedChange = { viewModel.updateConfig(state.config.copy(includeCoverage = it)) },
                    palette = palette,
                    typography = typography
                )
            }

            Spacer(modifier = Modifier.height(AppDimensions.spacingSm))

            // ── Trigger Type Selector ───────────────────────────────
            Text(
                text = strings.cicdScheduleTrigger,
                style = typography.heading3,
                color = palette.textPrimary
            )

            val triggers = listOf(
                TriggerType.ON_PUSH to strings.cicdOnPush,
                TriggerType.ON_PR to strings.cicdOnPr,
                TriggerType.SCHEDULED to strings.cicdScheduleTrigger,
                TriggerType.MANUAL to strings.cicdManual
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(palette.surface, RoundedCornerShape(AppDimensions.cardRadius))
                    .border(AppDimensions.borderWidth, palette.border, RoundedCornerShape(AppDimensions.cardRadius))
                    .padding(AppDimensions.spacingMd),
                verticalArrangement = Arrangement.spacedBy(AppDimensions.spacingXs)
            ) {
                triggers.forEach { (trigger, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(AppDimensions.buttonRadius))
                            .clickable { viewModel.updateConfig(state.config.copy(trigger = trigger)) }
                            .background(
                                if (state.config.trigger == trigger) Indigo.copy(alpha = 0.12f)
                                else Color.Transparent,
                                RoundedCornerShape(AppDimensions.buttonRadius)
                            )
                            .padding(horizontal = AppDimensions.spacingSm, vertical = AppDimensions.spacingXs),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AppDimensions.spacingSm)
                    ) {
                        RadioButton(
                            selected = state.config.trigger == trigger,
                            onClick = { viewModel.updateConfig(state.config.copy(trigger = trigger)) },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = Indigo,
                                unselectedColor = palette.textTertiary
                            )
                        )
                        Text(
                            text = label,
                            style = typography.body,
                            color = if (state.config.trigger == trigger) palette.textPrimary else palette.textSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(AppDimensions.spacingSm))

            // ── Docker Support Toggle ───────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(palette.surface, RoundedCornerShape(AppDimensions.cardRadius))
                    .border(AppDimensions.borderWidth, palette.border, RoundedCornerShape(AppDimensions.cardRadius))
                    .padding(AppDimensions.spacingMd),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = strings.cicdDockerSupport,
                        style = typography.body,
                        color = palette.textPrimary,
                        fontWeight = FontWeight.Medium
                    )
                }
                Switch(
                    checked = state.config.dockerSupport,
                    onCheckedChange = { viewModel.updateConfig(state.config.copy(dockerSupport = it)) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Indigo,
                        checkedTrackColor = Indigo.copy(alpha = 0.4f),
                        uncheckedThumbColor = palette.textTertiary,
                        uncheckedTrackColor = palette.border
                    )
                )
            }

            Spacer(modifier = Modifier.height(AppDimensions.spacingSm))

            // ── Generate Config Button ──────────────────────────────
            var generateHovered by remember { mutableStateOf(false) }
            Button(
                onClick = { viewModel.generateConfig() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .onPointerEvent(PointerEventType.Enter) { generateHovered = true }
                    .onPointerEvent(PointerEventType.Exit) { generateHovered = false },
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = if (generateHovered) IndigoDark else Indigo,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(AppDimensions.cardRadius),
                elevation = ButtonDefaults.elevation(
                    defaultElevation = 0.dp,
                    pressedElevation = 0.dp
                )
            ) {
                Text(
                    text = strings.cicdGenerateConfig,
                    style = typography.button,
                    color = Color.White
                )
            }
        }

        // ── Right Panel: Config Preview ────────────────────────────
        Column(
            modifier = Modifier
                .weight(0.6f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(AppDimensions.spacingMd)
        ) {
            // Preview Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = strings.cicdPreview,
                    style = typography.heading2,
                    color = palette.textPrimary
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(AppDimensions.spacingSm)
                ) {
                    // Copy to Clipboard button
                    var copyHovered by remember { mutableStateOf(false) }
                    Button(
                        onClick = { viewModel.copyToClipboard() },
                        enabled = state.generatedConfig != null,
                        modifier = Modifier
                            .onPointerEvent(PointerEventType.Enter) { copyHovered = true }
                            .onPointerEvent(PointerEventType.Exit) { copyHovered = false },
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = if (state.isCopied) StatusColors.Success
                            else if (copyHovered) IndigoLight
                            else Indigo,
                            contentColor = Color.White,
                            disabledBackgroundColor = palette.surfaceVariant,
                            disabledContentColor = palette.textTertiary
                        ),
                        shape = RoundedCornerShape(AppDimensions.buttonRadius),
                        elevation = ButtonDefaults.elevation(
                            defaultElevation = 0.dp,
                            pressedElevation = 0.dp
                        )
                    ) {
                        Text(
                            text = if (state.isCopied) strings.cicdCopied else strings.cicdCopyToClipboard,
                            style = typography.button
                        )
                    }

                    // Export File button
                    var exportHovered by remember { mutableStateOf(false) }
                    Button(
                        onClick = { exportConfigFile(state) },
                        enabled = state.generatedConfig != null,
                        modifier = Modifier
                            .onPointerEvent(PointerEventType.Enter) { exportHovered = true }
                            .onPointerEvent(PointerEventType.Exit) { exportHovered = false },
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = if (exportHovered) palette.surfaceHover else palette.surface,
                            contentColor = palette.textPrimary,
                            disabledBackgroundColor = palette.surfaceVariant,
                            disabledContentColor = palette.textTertiary
                        ),
                        shape = RoundedCornerShape(AppDimensions.buttonRadius),
                        border = BorderStroke(AppDimensions.borderWidth, palette.border),
                        elevation = ButtonDefaults.elevation(
                            defaultElevation = 0.dp,
                            pressedElevation = 0.dp
                        )
                    ) {
                        Text(
                            text = strings.cicdExportFile,
                            style = typography.button
                        )
                    }
                }
            }

            // Config Preview Area
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(palette.codeBg, RoundedCornerShape(AppDimensions.cardRadius))
                    .border(AppDimensions.borderWidth, palette.border, RoundedCornerShape(AppDimensions.cardRadius))
            ) {
                if (state.generatedConfig != null) {
                    val scrollState = rememberScrollState()
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .horizontalScroll(rememberScrollState())
                            .padding(AppDimensions.spacingLg)
                    ) {
                        Text(
                            text = highlightYaml(state.generatedConfig!!, palette),
                            style = typography.code
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = strings.cicdNoPreview,
                            style = typography.body,
                            color = palette.textTertiary
                        )
                    }
                }
            }
        }
    }
}

// ── Provider Card ──────────────────────────────────────────────────
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun ProviderCard(
    label: String,
    isSelected: Boolean,
    palette: AppPalette,
    typography: AppTextStyles,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var hovered by remember { mutableStateOf(false) }
    val bgColor = when {
        isSelected -> Indigo.copy(alpha = 0.15f)
        hovered -> palette.surfaceHover
        else -> palette.surface
    }
    val borderColor = if (isSelected) Indigo else palette.border

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(AppDimensions.cardRadius))
            .background(bgColor, RoundedCornerShape(AppDimensions.cardRadius))
            .border(AppDimensions.borderWidth, borderColor, RoundedCornerShape(AppDimensions.cardRadius))
            .clickable { onClick() }
            .onPointerEvent(PointerEventType.Enter) { hovered = true }
            .onPointerEvent(PointerEventType.Exit) { hovered = false }
            .padding(AppDimensions.spacingMd),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = typography.body,
            color = if (isSelected) Indigo else palette.textPrimary,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

// ── Checkbox Row ───────────────────────────────────────────────────
@Composable
private fun CheckboxRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    palette: AppPalette,
    typography: AppTextStyles
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppDimensions.buttonRadius))
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = AppDimensions.spacingXs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppDimensions.spacingSm)
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = CheckboxDefaults.colors(
                checkedColor = Indigo,
                uncheckedColor = palette.textTertiary,
                checkmarkColor = Color.White
            )
        )
        Text(
            text = label,
            style = typography.body,
            color = palette.textPrimary
        )
    }
}

// ── YAML Syntax Highlighting ───────────────────────────────────────
private fun highlightYaml(text: String, palette: AppPalette): AnnotatedString {
    val keyColor = Color(0xFF79C0FF)       // blue for keys
    val stringColor = Color(0xFFA5D6A7)    // green for string values
    val commentColor = Color(0xFF6A9955)   // green-grey for comments
    val punctuationColor = Color(0xFFD4D4D4) // light grey for colons, dashes
    val numberColor = Color(0xFFFFCC80)    // amber for numbers
    val keywordColor = Color(0xFFCE93D8)   // purple for booleans/keywords

    return buildAnnotatedString {
        text.lines().forEach { line ->
            when {
                line.trimStart().startsWith("#") -> {
                    withStyle(SpanStyle(color = commentColor)) { append(line) }
                }
                line.contains(":") -> {
                    val colonIndex = line.indexOf(":")
                    val key = line.substring(0, colonIndex)
                    val rest = line.substring(colonIndex)

                    // Key part (including leading spaces/dashes)
                    val trimmedKey = key.trimStart()
                    val leadingWhitespace = key.substring(0, key.length - trimmedKey.length)

                    if (leadingWhitespace.isNotEmpty()) {
                        // Render leading dashes separately
                        val dashPart = leadingWhitespace.replace(" ", " ")
                        if (dashPart.contains("-")) {
                            withStyle(SpanStyle(color = punctuationColor)) { append(dashPart) }
                        } else {
                            append(leadingWhitespace)
                        }
                    }
                    withStyle(SpanStyle(color = keyColor, fontWeight = FontWeight.Medium)) {
                        append(trimmedKey)
                    }
                    withStyle(SpanStyle(color = punctuationColor)) { append(":") }

                    val value = rest.drop(1) // after ':'
                    if (value.isNotEmpty()) {
                        val trimmedValue = value.trimStart()
                        val valueSpaces = value.substring(0, value.length - trimmedValue.length)
                        append(valueSpaces)
                        when {
                            trimmedValue.startsWith("'") || trimmedValue.startsWith("\"") ->
                                withStyle(SpanStyle(color = stringColor)) { append(trimmedValue) }
                            trimmedValue == "true" || trimmedValue == "false" ->
                                withStyle(SpanStyle(color = keywordColor)) { append(trimmedValue) }
                            trimmedValue.toDoubleOrNull() != null ->
                                withStyle(SpanStyle(color = numberColor)) { append(trimmedValue) }
                            else ->
                                withStyle(SpanStyle(color = stringColor)) { append(trimmedValue) }
                        }
                    }
                }
                line.trimStart().startsWith("-") -> {
                    val leadingSpaces = line.substring(0, line.length - line.trimStart().length)
                    append(leadingSpaces)
                    withStyle(SpanStyle(color = punctuationColor)) { append("- ") }
                    val content = line.trimStart().removePrefix("-").trimStart()
                    withStyle(SpanStyle(color = stringColor)) { append(content) }
                }
                else -> {
                    withStyle(SpanStyle(color = palette.textSecondary)) { append(line) }
                }
            }
            append("\n")
        }
    }
}

// ── Export Config to File (Desktop file chooser) ───────────────────
private fun exportConfigFile(state: CiCdExportState) {
    val config = state.generatedConfig ?: return
    val (defaultName, extension) = when (state.config.provider) {
        PipelineProvider.GITHUB_ACTIONS -> "github-actions.yml" to "yml"
        PipelineProvider.GITLAB_CI -> ".gitlab-ci.yml" to "yml"
        PipelineProvider.JENKINS -> "Jenkinsfile" to ""
        PipelineProvider.CIRCLE_CI -> "config.yml" to "yml"
    }

    val chooser = JFileChooser().apply {
        dialogTitle = "Export CI/CD Config"
        selectedFile = File(defaultName)
        if (extension.isNotEmpty()) {
            fileFilter = FileNameExtensionFilter("$extension files", extension)
        }
    }

    if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
        try {
            chooser.selectedFile.writeText(config)
        } catch (_: Exception) {}
    }
}
