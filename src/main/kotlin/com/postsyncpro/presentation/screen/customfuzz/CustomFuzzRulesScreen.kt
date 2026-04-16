package com.postsyncpro.presentation.screen.customfuzz

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.postsyncpro.presentation.i18n.LocalAppStrings
import com.postsyncpro.presentation.theme.*

@Composable
fun CustomFuzzRulesScreen(viewModel: CustomFuzzRulesViewModel) {
    val state by viewModel.state.collectAsState()
    val strings = LocalAppStrings.current
    val palette = LocalAppPalette.current
    val typography = LocalAppTypography.current

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
            .padding(AppDimensions.spacingLg),
        horizontalArrangement = Arrangement.spacedBy(AppDimensions.spacingLg)
    ) {
        // Left: Rules list
        Column(
            modifier = Modifier
                .weight(0.55f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(AppDimensions.spacingMd)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(AppDimensions.spacingXs)) {
                    Text(
                        text = strings.customFuzzTitle,
                        style = typography.heading1,
                        color = palette.textPrimary
                    )
                    Text(
                        text = strings.customFuzzSubtitle,
                        style = typography.bodySmall,
                        color = palette.textSecondary
                    )
                }
                NewRuleButton(
                    label = strings.customFuzzNewRule,
                    onClick = { viewModel.startCreating() }
                )
            }

            // Rules count
            if (state.rules.isNotEmpty()) {
                Text(
                    text = "${state.rules.size} ${strings.customFuzzRulesCount}",
                    style = typography.caption,
                    color = palette.textTertiary
                )
            }

            // Rules list or empty state
            if (state.rules.isEmpty() && !state.isCreating) {
                EmptyRulesState(strings.customFuzzNoRules)
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(AppDimensions.spacingSm)
                ) {
                    items(state.rules, key = { it.id }) { rule ->
                        RuleCard(
                            rule = rule,
                            isSelected = state.selectedRuleId == rule.id,
                            isTesting = state.isTesting && state.selectedRuleId == rule.id,
                            onSelect = { viewModel.selectRule(rule.id) },
                            onToggle = { viewModel.toggleRule(rule.id) },
                            onTest = { viewModel.testRule(rule.id) },
                            onEdit = {
                                viewModel.updateEditingRule(rule)
                                viewModel.startCreating()
                            },
                            onDelete = { viewModel.deleteRule(rule.id) }
                        )
                    }
                }
            }

            // Test result banner
            state.testResult?.let { result ->
                Surface(
                    shape = RoundedCornerShape(AppDimensions.cardRadius),
                    color = StatusColors.SuccessBackground,
                    border = BorderStroke(AppDimensions.borderWidth, StatusColors.Success.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(AppDimensions.spacingSm),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AppDimensions.spacingSm)
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = StatusColors.Success,
                            modifier = Modifier.size(AppDimensions.iconSizeSm)
                        )
                        Text(
                            text = result,
                            style = typography.bodySmall,
                            color = StatusColors.Success
                        )
                    }
                }
            }
        }

        // Right: Create/Edit form
        AnimatedVisibility(
            visible = state.isCreating,
            enter = fadeIn() + slideInHorizontally(initialOffsetX = { it / 4 }),
            exit = fadeOut() + slideOutHorizontally(targetOffsetX = { it / 4 })
        ) {
            state.editingRule?.let { editingRule ->
                RuleEditForm(
                    rule = editingRule,
                    onRuleChange = { viewModel.updateEditingRule(it) },
                    onSave = { viewModel.saveRule() },
                    onCancel = { viewModel.cancelCreating() },
                    modifier = Modifier.weight(0.45f)
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// New Rule Button
// ---------------------------------------------------------------------------

@Composable
private fun NewRuleButton(label: String, onClick: () -> Unit) {
    val palette = LocalAppPalette.current
    val typography = LocalAppTypography.current
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(AppDimensions.buttonRadius))
            .background(if (hovered) BrandColors.RedLight else BrandColors.Red)
            .hoverable(interaction)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(AppDimensions.spacingXs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(AppDimensions.iconSizeSm)
            )
            Text(
                text = label,
                style = typography.body,
                color = Color.White,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Rule Card
// ---------------------------------------------------------------------------

@Composable
private fun RuleCard(
    rule: FuzzRule,
    isSelected: Boolean,
    isTesting: Boolean,
    onSelect: () -> Unit,
    onToggle: () -> Unit,
    onTest: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val palette = LocalAppPalette.current
    val typography = LocalAppTypography.current
    val strings = LocalAppStrings.current
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()

    val borderColor = when {
        isSelected -> BrandColors.Red.copy(alpha = 0.6f)
        hovered -> palette.border
        else -> palette.borderSubtle
    }
    val bgColor = when {
        isSelected -> BrandColors.Red.copy(alpha = 0.05f)
        hovered -> palette.surfaceHover
        else -> palette.surface
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .hoverable(interaction)
            .clickable(onClick = onSelect),
        shape = RoundedCornerShape(AppDimensions.cardRadius),
        color = bgColor,
        border = BorderStroke(AppDimensions.borderWidth, borderColor)
    ) {
        Column(
            modifier = Modifier.padding(AppDimensions.spacingMd),
            verticalArrangement = Arrangement.spacedBy(AppDimensions.spacingSm)
        ) {
            // Top row: name + enabled toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = rule.name.ifEmpty { "Untitled Rule" },
                    style = typography.heading3,
                    color = if (rule.isEnabled) palette.textPrimary else palette.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = rule.isEnabled,
                    onCheckedChange = { onToggle() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = BrandColors.Red,
                        uncheckedThumbColor = palette.textTertiary,
                        uncheckedTrackColor = palette.surfaceVariant
                    ),
                    modifier = Modifier.height(20.dp)
                )
            }

            // Target field
            if (rule.targetField.isNotEmpty()) {
                Text(
                    text = rule.targetField,
                    style = typography.caption,
                    color = palette.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Badges row: mutation type + severity + test result
            Row(
                horizontalArrangement = Arrangement.spacedBy(AppDimensions.spacingSm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MutationTypeBadge(rule.mutationType)
                SeverityBadge(rule.severity)
                rule.testResults?.let { passRate ->
                    val color = when {
                        passRate >= 80 -> StatusColors.Success
                        passRate >= 60 -> StatusColors.Warning
                        else -> StatusColors.Error
                    }
                    Text(
                        text = "$passRate%",
                        style = typography.caption,
                        color = color,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Action row (visible on hover or selected)
            AnimatedVisibility(visible = hovered || isSelected) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AppDimensions.spacingSm)
                ) {
                    ActionChip(
                        label = strings.customFuzzTestRule,
                        icon = Icons.Default.PlayArrow,
                        color = BrandColors.Red,
                        isLoading = isTesting,
                        onClick = onTest
                    )
                    ActionChip(
                        label = "Edit",
                        icon = Icons.Default.Edit,
                        color = palette.textSecondary,
                        onClick = onEdit
                    )
                    ActionChip(
                        label = "Delete",
                        icon = Icons.Default.Delete,
                        color = StatusColors.Error,
                        onClick = onDelete
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Mutation Type Badge
// ---------------------------------------------------------------------------

@Composable
private fun MutationTypeBadge(type: MutationType) {
    val strings = LocalAppStrings.current
    val palette = LocalAppPalette.current
    val typography = LocalAppTypography.current

    val (label, color) = when (type) {
        MutationType.SQL_INJECTION -> strings.customFuzzMutationSqlInjection to BrandColors.Red
        MutationType.XSS -> strings.customFuzzMutationXss to StatusColors.Warning
        MutationType.OVERFLOW -> strings.customFuzzMutationOverflow to BrandColors.Purple
        MutationType.BOUNDARY -> strings.customFuzzMutationBoundary to StatusColors.Info
        MutationType.FORMAT -> strings.customFuzzMutationFormat to BrandColors.Orange
        MutationType.CUSTOM -> strings.customFuzzMutationCustom to palette.textSecondary
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(AppDimensions.chipRadius))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(
            text = label,
            style = typography.caption,
            color = color,
            fontWeight = FontWeight.SemiBold
        )
    }
}

// ---------------------------------------------------------------------------
// Severity Badge
// ---------------------------------------------------------------------------

@Composable
private fun SeverityBadge(severity: RuleSeverity) {
    val typography = LocalAppTypography.current

    val (label, color) = when (severity) {
        RuleSeverity.HIGH -> "HIGH" to StatusColors.Error
        RuleSeverity.MEDIUM -> "MEDIUM" to StatusColors.Warning
        RuleSeverity.LOW -> "LOW" to StatusColors.Success
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(AppDimensions.chipRadius))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(
            text = label,
            style = typography.caption,
            color = color,
            fontWeight = FontWeight.Bold
        )
    }
}

// ---------------------------------------------------------------------------
// Action Chip
// ---------------------------------------------------------------------------

@Composable
private fun ActionChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    isLoading: Boolean = false,
    onClick: () -> Unit
) {
    val palette = LocalAppPalette.current
    val typography = LocalAppTypography.current
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(AppDimensions.chipRadius))
            .background(if (hovered) color.copy(alpha = 0.15f) else Color.Transparent)
            .border(AppDimensions.borderWidth, color.copy(alpha = 0.3f), RoundedCornerShape(AppDimensions.chipRadius))
            .hoverable(interaction)
            .clickable(enabled = !isLoading, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    strokeWidth = 1.5.dp,
                    color = color
                )
            } else {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(12.dp)
                )
            }
            Text(
                text = label,
                style = typography.caption,
                color = color
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Empty State
// ---------------------------------------------------------------------------

@Composable
private fun EmptyRulesState(message: String) {
    val palette = LocalAppPalette.current
    val typography = LocalAppTypography.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = AppDimensions.spacingXxl),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AppDimensions.spacingSm)
        ) {
            Icon(
                Icons.Default.Shield,
                contentDescription = null,
                tint = palette.textTertiary,
                modifier = Modifier.size(48.dp)
            )
            Text(
                text = message,
                style = typography.body,
                color = palette.textTertiary
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Rule Edit Form
// ---------------------------------------------------------------------------

@Composable
private fun RuleEditForm(
    rule: FuzzRule,
    onRuleChange: (FuzzRule) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    val palette = LocalAppPalette.current
    val typography = LocalAppTypography.current

    Surface(
        modifier = modifier.fillMaxHeight(),
        shape = RoundedCornerShape(AppDimensions.cardRadius),
        color = palette.surface,
        border = BorderStroke(AppDimensions.borderWidth, palette.border)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(AppDimensions.spacingLg),
            verticalArrangement = Arrangement.spacedBy(AppDimensions.spacingMd)
        ) {
            // Form header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (rule.name.isEmpty()) strings.customFuzzNewRule else rule.name,
                    style = typography.heading3,
                    color = palette.textPrimary
                )
                val closeInteraction = remember { MutableInteractionSource() }
                val closeHovered by closeInteraction.collectIsHoveredAsState()
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(if (closeHovered) palette.surfaceHover else Color.Transparent)
                        .hoverable(closeInteraction)
                        .clickable(onClick = onCancel),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = null,
                        tint = palette.textSecondary,
                        modifier = Modifier.size(AppDimensions.iconSizeSm)
                    )
                }
            }

            Divider(color = palette.border)

            // Rule Name
            FormField(label = strings.customFuzzRuleName) {
                OutlinedTextField(
                    value = rule.name,
                    onValueChange = { onRuleChange(rule.copy(name = it)) },
                    placeholder = {
                        Text(
                            strings.customFuzzRuleNamePlaceholder,
                            style = typography.bodySmall,
                            color = palette.textTertiary
                        )
                    },
                    textStyle = typography.body.copy(color = palette.textPrimary),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(AppDimensions.buttonRadius),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BrandColors.Red,
                        unfocusedBorderColor = palette.inputBorder,
                        cursorColor = BrandColors.Red,
                        focusedContainerColor = palette.inputBackground,
                        unfocusedContainerColor = palette.inputBackground
                    )
                )
            }

            // Target Field
            FormField(label = strings.customFuzzTargetField) {
                OutlinedTextField(
                    value = rule.targetField,
                    onValueChange = { onRuleChange(rule.copy(targetField = it)) },
                    placeholder = {
                        Text(
                            strings.customFuzzTargetFieldPlaceholder,
                            style = typography.bodySmall,
                            color = palette.textTertiary
                        )
                    },
                    textStyle = typography.body.copy(color = palette.textPrimary),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(AppDimensions.buttonRadius),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BrandColors.Red,
                        unfocusedBorderColor = palette.inputBorder,
                        cursorColor = BrandColors.Red,
                        focusedContainerColor = palette.inputBackground,
                        unfocusedContainerColor = palette.inputBackground
                    )
                )
            }

            // Mutation Type Selector
            FormField(label = strings.customFuzzMutationType) {
                MutationTypeSelector(
                    selected = rule.mutationType,
                    onSelect = { onRuleChange(rule.copy(mutationType = it)) }
                )
            }

            // Custom Payloads
            FormField(label = strings.customFuzzPayloads) {
                OutlinedTextField(
                    value = rule.customPayloads,
                    onValueChange = { onRuleChange(rule.copy(customPayloads = it)) },
                    placeholder = {
                        Text(
                            strings.customFuzzPayloadsPlaceholder,
                            style = typography.bodySmall,
                            color = palette.textTertiary
                        )
                    },
                    textStyle = typography.body.copy(color = palette.textPrimary),
                    minLines = 4,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(AppDimensions.buttonRadius),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BrandColors.Red,
                        unfocusedBorderColor = palette.inputBorder,
                        cursorColor = BrandColors.Red,
                        focusedContainerColor = palette.inputBackground,
                        unfocusedContainerColor = palette.inputBackground
                    )
                )
            }

            // Severity Selector
            FormField(label = strings.customFuzzSeverity) {
                SeveritySelector(
                    selected = rule.severity,
                    onSelect = { onRuleChange(rule.copy(severity = it)) }
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Save button
            val saveInteraction = remember { MutableInteractionSource() }
            val saveHovered by saveInteraction.collectIsHoveredAsState()
            val canSave = rule.name.isNotBlank()

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(AppDimensions.buttonRadius))
                    .background(
                        when {
                            !canSave -> palette.textTertiary.copy(alpha = 0.3f)
                            saveHovered -> BrandColors.RedLight
                            else -> BrandColors.Red
                        }
                    )
                    .hoverable(saveInteraction)
                    .clickable(enabled = canSave, onClick = onSave)
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = strings.customFuzzSaveRule,
                    style = typography.body,
                    color = if (canSave) Color.White else palette.textTertiary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Form Field Label
// ---------------------------------------------------------------------------

@Composable
private fun FormField(label: String, content: @Composable () -> Unit) {
    val palette = LocalAppPalette.current
    val typography = LocalAppTypography.current

    Column(verticalArrangement = Arrangement.spacedBy(AppDimensions.spacingXs)) {
        Text(
            text = label,
            style = typography.bodySmall,
            color = palette.textSecondary,
            fontWeight = FontWeight.SemiBold
        )
        content()
    }
}

// ---------------------------------------------------------------------------
// Mutation Type Selector
// ---------------------------------------------------------------------------

@Composable
private fun MutationTypeSelector(
    selected: MutationType,
    onSelect: (MutationType) -> Unit
) {
    val strings = LocalAppStrings.current
    val palette = LocalAppPalette.current
    val typography = LocalAppTypography.current

    @Composable
    fun label(type: MutationType): String = when (type) {
        MutationType.SQL_INJECTION -> strings.customFuzzMutationSqlInjection
        MutationType.XSS -> strings.customFuzzMutationXss
        MutationType.OVERFLOW -> strings.customFuzzMutationOverflow
        MutationType.BOUNDARY -> strings.customFuzzMutationBoundary
        MutationType.FORMAT -> strings.customFuzzMutationFormat
        MutationType.CUSTOM -> strings.customFuzzMutationCustom
    }

    fun color(type: MutationType): Color = when (type) {
        MutationType.SQL_INJECTION -> BrandColors.Red
        MutationType.XSS -> StatusColors.Warning
        MutationType.OVERFLOW -> BrandColors.Purple
        MutationType.BOUNDARY -> StatusColors.Info
        MutationType.FORMAT -> BrandColors.Orange
        MutationType.CUSTOM -> StatusColors.Success
    }

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(AppDimensions.spacingSm),
        verticalArrangement = Arrangement.spacedBy(AppDimensions.spacingSm)
    ) {
        MutationType.entries.forEach { type ->
            val isSelected = type == selected
            val interaction = remember { MutableInteractionSource() }
            val hovered by interaction.collectIsHoveredAsState()
            val typeColor = color(type)

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(AppDimensions.chipRadius))
                    .background(
                        when {
                            isSelected -> typeColor.copy(alpha = 0.2f)
                            hovered -> typeColor.copy(alpha = 0.08f)
                            else -> Color.Transparent
                        }
                    )
                    .border(
                        AppDimensions.borderWidth,
                        if (isSelected) typeColor.copy(alpha = 0.5f) else palette.border,
                        RoundedCornerShape(AppDimensions.chipRadius)
                    )
                    .hoverable(interaction)
                    .clickable { onSelect(type) }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = label(type),
                    style = typography.caption,
                    color = if (isSelected) typeColor else palette.textSecondary,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Severity Selector
// ---------------------------------------------------------------------------

@Composable
private fun SeveritySelector(
    selected: RuleSeverity,
    onSelect: (RuleSeverity) -> Unit
) {
    val palette = LocalAppPalette.current
    val typography = LocalAppTypography.current

    fun color(severity: RuleSeverity): Color = when (severity) {
        RuleSeverity.HIGH -> StatusColors.Error
        RuleSeverity.MEDIUM -> StatusColors.Warning
        RuleSeverity.LOW -> StatusColors.Success
    }

    Row(horizontalArrangement = Arrangement.spacedBy(AppDimensions.spacingSm)) {
        RuleSeverity.entries.forEach { severity ->
            val isSelected = severity == selected
            val interaction = remember { MutableInteractionSource() }
            val hovered by interaction.collectIsHoveredAsState()
            val sevColor = color(severity)

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(AppDimensions.chipRadius))
                    .background(
                        when {
                            isSelected -> sevColor.copy(alpha = 0.2f)
                            hovered -> sevColor.copy(alpha = 0.08f)
                            else -> Color.Transparent
                        }
                    )
                    .border(
                        AppDimensions.borderWidth,
                        if (isSelected) sevColor.copy(alpha = 0.5f) else palette.border,
                        RoundedCornerShape(AppDimensions.chipRadius)
                    )
                    .hoverable(interaction)
                    .clickable { onSelect(severity) }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = severity.name,
                    style = typography.caption,
                    color = if (isSelected) sevColor else palette.textSecondary,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Flow Row (simple replacement for missing Compose FlowRow in desktop)
// ---------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRow(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable () -> Unit
) {
    androidx.compose.foundation.layout.FlowRow(
        modifier = modifier,
        horizontalArrangement = horizontalArrangement,
        verticalArrangement = verticalArrangement
    ) {
        content()
    }
}
