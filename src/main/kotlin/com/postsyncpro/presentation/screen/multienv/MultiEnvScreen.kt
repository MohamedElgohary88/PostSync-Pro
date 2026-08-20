package com.postsyncpro.presentation.screen.multienv

import androidx.compose.animation.*
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.postsyncpro.presentation.components.*
import com.postsyncpro.presentation.i18n.LocalAppStrings
import com.postsyncpro.presentation.theme.*

@Composable
fun MultiEnvScreen(viewModel: MultiEnvViewModel) {
    val state by viewModel.state.collectAsState()
    val strings = LocalAppStrings.current
    val palette = LocalAppPalette.current
    val typography = LocalAppTypography.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
            .padding(AppDimensions.spacingMd)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(AppDimensions.spacingMd)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = strings.multiEnvTitle,
                    style = typography.heading1.copy(color = palette.textPrimary)
                )
                Spacer(Modifier.height(AppDimensions.spacingXs))
                Text(
                    text = strings.multiEnvSubtitle,
                    style = typography.body.copy(color = palette.textSecondary)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(AppDimensions.spacingSm)) {
                // Compare Results button
                if (state.environments.any { it.lastTestResult != null }) {
                    var compareHover by remember { mutableStateOf(false) }
                    Button(
                        onClick = { viewModel.toggleComparison() },
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = if (compareHover) BrandColors.BlueLight else palette.surface
                        ),
                        shape = RoundedCornerShape(AppDimensions.buttonRadius),
                        elevation = ButtonDefaults.elevation(defaultElevation = 0.dp),
                        border = BorderStroke(AppDimensions.borderWidth, BrandColors.Blue),
                        modifier = Modifier
                            .hoverable(
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                enabled = true
                            )
                    ) {
                        Icon(
                            Icons.Default.CompareArrows,
                            contentDescription = null,
                            tint = BrandColors.Blue,
                            modifier = Modifier.size(AppDimensions.iconSizeSm)
                        )
                        Spacer(Modifier.width(AppDimensions.spacingXs))
                        Text(
                            text = strings.multiEnvCompare,
                            style = typography.button.copy(color = BrandColors.Blue)
                        )
                    }
                }

                // Run All button
                var runHover by remember { mutableStateOf(false) }
                Button(
                    onClick = { viewModel.runAllEnvironments() },
                    enabled = state.environments.any { it.isActive } && !state.isRunningAll,
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = if (runHover) BrandColors.BlueDark else BrandColors.Blue,
                        disabledBackgroundColor = BrandColors.Blue.copy(alpha = 0.4f)
                    ),
                    shape = RoundedCornerShape(AppDimensions.buttonRadius),
                    elevation = ButtonDefaults.elevation(defaultElevation = 0.dp),
                    modifier = Modifier
                        .hoverable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            enabled = true
                        )
                ) {
                    if (state.isRunningAll) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(AppDimensions.iconSizeSm),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(AppDimensions.iconSizeSm)
                        )
                    }
                    Spacer(Modifier.width(AppDimensions.spacingXs))
                    Text(
                        text = strings.multiEnvRunAll,
                        style = typography.button.copy(color = Color.White)
                    )
                }

                // Add Environment button
                var addHover by remember { mutableStateOf(false) }
                Button(
                    onClick = { viewModel.startAdding() },
                    enabled = !state.isAdding,
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = if (addHover) palette.surfaceHover else palette.surface
                    ),
                    shape = RoundedCornerShape(AppDimensions.buttonRadius),
                    elevation = ButtonDefaults.elevation(defaultElevation = 0.dp),
                    border = BorderStroke(AppDimensions.borderWidth, palette.border),
                    modifier = Modifier
                        .hoverable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            enabled = true
                        )
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        tint = BrandColors.Blue,
                        modifier = Modifier.size(AppDimensions.iconSizeSm)
                    )
                    Spacer(Modifier.width(AppDimensions.spacingXs))
                    Text(
                        text = strings.multiEnvAdd,
                        style = typography.button.copy(color = palette.textPrimary)
                    )
                }
            }
        }

        // Add Environment Form
        AnimatedVisibility(
            visible = state.isAdding,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            state.editingEnv?.let { editingEnv ->
                SectionCard(
                    title = strings.multiEnvAdd,
                    accentColor = BrandColors.Blue
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(AppDimensions.spacingMd)
                    ) {
                        // Environment Name
                        Column(verticalArrangement = Arrangement.spacedBy(AppDimensions.spacingXs)) {
                            Text(
                                text = strings.multiEnvName,
                                style = AppTypography.labelSmall,
                                color = palette.textSecondary
                            )
                            OutlinedTextField(
                                value = editingEnv.name,
                                onValueChange = { viewModel.updateEditing(editingEnv.copy(name = it)) },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = {
                                    Text(
                                        strings.multiEnvNamePlaceholder,
                                        style = AppTypography.bodyMedium,
                                        color = palette.textTertiary
                                    )
                                },
                                textStyle = AppTypography.bodyMedium.copy(color = palette.textPrimary),
                                singleLine = true,
                                colors = TextFieldDefaults.outlinedTextFieldColors(
                                    textColor = palette.textPrimary,
                                    backgroundColor = palette.inputBackground,
                                    focusedBorderColor = BrandColors.Blue,
                                    unfocusedBorderColor = palette.inputBorder,
                                    cursorColor = BrandColors.Blue
                                ),
                                shape = RoundedCornerShape(AppDimensions.inputRadius)
                            )
                        }

                        // Base URL
                        Column(verticalArrangement = Arrangement.spacedBy(AppDimensions.spacingXs)) {
                            Text(
                                text = strings.multiEnvBaseUrl,
                                style = AppTypography.labelSmall,
                                color = palette.textSecondary
                            )
                            OutlinedTextField(
                                value = editingEnv.baseUrl,
                                onValueChange = { viewModel.updateEditing(editingEnv.copy(baseUrl = it)) },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = {
                                    Text(
                                        strings.multiEnvBaseUrlPlaceholder,
                                        style = AppTypography.codeMedium,
                                        color = palette.textTertiary
                                    )
                                },
                                textStyle = AppTypography.codeMedium.copy(color = palette.textPrimary),
                                singleLine = true,
                                colors = TextFieldDefaults.outlinedTextFieldColors(
                                    textColor = palette.textPrimary,
                                    backgroundColor = palette.codeBg,
                                    focusedBorderColor = BrandColors.Blue,
                                    unfocusedBorderColor = palette.inputBorder,
                                    cursorColor = BrandColors.Blue
                                ),
                                shape = RoundedCornerShape(AppDimensions.inputRadius)
                            )
                        }

                        // Auth Token
                        Column(verticalArrangement = Arrangement.spacedBy(AppDimensions.spacingXs)) {
                            Text(
                                text = strings.multiEnvAuthToken,
                                style = AppTypography.labelSmall,
                                color = palette.textSecondary
                            )
                            OutlinedTextField(
                                value = editingEnv.authToken,
                                onValueChange = { viewModel.updateEditing(editingEnv.copy(authToken = it)) },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = {
                                    Text(
                                        "Bearer ...",
                                        style = AppTypography.codeMedium,
                                        color = palette.textTertiary
                                    )
                                },
                                textStyle = AppTypography.codeMedium.copy(color = palette.textPrimary),
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                colors = TextFieldDefaults.outlinedTextFieldColors(
                                    textColor = palette.textPrimary,
                                    backgroundColor = palette.codeBg,
                                    focusedBorderColor = BrandColors.Blue,
                                    unfocusedBorderColor = palette.inputBorder,
                                    cursorColor = BrandColors.Blue
                                ),
                                shape = RoundedCornerShape(AppDimensions.inputRadius)
                            )
                        }

                        Divider(color = palette.divider)

                        // Action buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = { viewModel.cancelAdding() }) {
                                Text(
                                    text = strings.cancel,
                                    style = typography.button.copy(color = palette.textSecondary)
                                )
                            }
                            Spacer(Modifier.width(AppDimensions.spacingSm))
                            Button(
                                onClick = { viewModel.saveEnvironment() },
                                enabled = editingEnv.name.isNotBlank() && editingEnv.baseUrl.isNotBlank(),
                                colors = ButtonDefaults.buttonColors(
                                    backgroundColor = BrandColors.Blue,
                                    disabledBackgroundColor = BrandColors.Blue.copy(alpha = 0.4f)
                                ),
                                shape = RoundedCornerShape(AppDimensions.buttonRadius),
                                elevation = ButtonDefaults.elevation(defaultElevation = 0.dp)
                            ) {
                                Text(
                                    text = strings.save,
                                    style = typography.button.copy(color = Color.White)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Empty state
        if (state.environments.isEmpty() && !state.isAdding) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(AppDimensions.spacingXxl),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Dns,
                        contentDescription = null,
                        tint = palette.textTertiary,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(Modifier.height(AppDimensions.spacingMd))
                    Text(
                        text = strings.multiEnvNoEnvs,
                        style = typography.body.copy(color = palette.textTertiary),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // Environment Cards
        state.environments.forEach { env ->
            EnvironmentCard(
                env = env,
                isRunning = state.runningEnvId == env.id,
                viewModel = viewModel
            )
        }

        // Comparison Table
        AnimatedVisibility(
            visible = state.showComparison && state.environments.any { it.lastTestResult != null },
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            ComparisonTable(environments = state.environments.filter { it.lastTestResult != null })
        }
    }
}

@Composable
private fun EnvironmentCard(
    env: Environment,
    isRunning: Boolean,
    viewModel: MultiEnvViewModel
) {
    val palette = LocalAppPalette.current
    val typography = LocalAppTypography.current
    val strings = LocalAppStrings.current
    var isHovered by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppDimensions.cardRadius))
            .background(if (isHovered) palette.surfaceHover else palette.surface)
            .border(
                width = AppDimensions.borderWidth,
                color = if (isRunning) BrandColors.Blue.copy(alpha = 0.6f)
                else if (env.isDefault) BrandColors.Blue.copy(alpha = 0.3f)
                else palette.border,
                shape = RoundedCornerShape(AppDimensions.cardRadius)
            )
            .hoverable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                enabled = true
            )
            .padding(AppDimensions.spacingLg)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(AppDimensions.spacingSm)) {
            // Top row: name, badges, actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Active indicator dot
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(
                            if (env.isActive) StatusColors.Success else palette.textTertiary
                        )
                )
                Spacer(Modifier.width(AppDimensions.spacingSm))

                // Environment name
                Text(
                    text = env.name.ifBlank { strings.multiEnvCustom },
                    style = typography.heading3.copy(color = palette.textPrimary),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                // Default badge
                if (env.isDefault) {
                    Spacer(Modifier.width(AppDimensions.spacingSm))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(AppDimensions.chipRadius))
                            .background(BrandColors.Blue.copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = strings.multiEnvDefault,
                            style = typography.caption.copy(
                                color = BrandColors.Blue,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
                }

                // Running indicator
                if (isRunning) {
                    Spacer(Modifier.width(AppDimensions.spacingSm))
                    CircularProgressIndicator(
                        modifier = Modifier.size(AppDimensions.iconSizeSm),
                        color = BrandColors.Blue,
                        strokeWidth = 2.dp
                    )
                }

                Spacer(Modifier.width(AppDimensions.spacingMd))

                // Active toggle
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppDimensions.spacingXs)
                ) {
                    Text(
                        text = strings.multiEnvActive,
                        style = typography.caption.copy(color = palette.textSecondary)
                    )
                    Switch(
                        checked = env.isActive,
                        onCheckedChange = { viewModel.toggleActive(env.id) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = BrandColors.Blue,
                            checkedTrackColor = BrandColors.Blue.copy(alpha = 0.4f),
                            uncheckedThumbColor = palette.textTertiary,
                            uncheckedTrackColor = palette.border
                        )
                    )
                }

                // Actions
                IconButton(onClick = { viewModel.setDefault(env.id) }) {
                    Icon(
                        if (env.isDefault) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = strings.multiEnvSetDefault,
                        tint = if (env.isDefault) BrandColors.Blue else palette.textTertiary,
                        modifier = Modifier.size(AppDimensions.iconSizeMd)
                    )
                }
                IconButton(onClick = { viewModel.deleteEnvironment(env.id) }) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = null,
                        tint = StatusColors.Error,
                        modifier = Modifier.size(AppDimensions.iconSizeMd)
                    )
                }
            }

            // Base URL
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppDimensions.spacingXs)
            ) {
                Icon(
                    Icons.Default.Link,
                    contentDescription = null,
                    tint = palette.textTertiary,
                    modifier = Modifier.size(AppDimensions.iconSizeSm)
                )
                Text(
                    text = env.baseUrl.ifBlank { "-" },
                    style = AppTypography.codeSmall.copy(color = palette.textSecondary),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Auth Token (masked)
            if (env.authToken.isNotBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppDimensions.spacingXs)
                ) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = null,
                        tint = palette.textTertiary,
                        modifier = Modifier.size(AppDimensions.iconSizeSm)
                    )
                    Text(
                        text = "${strings.multiEnvAuthToken}: ${"*".repeat(20)}",
                        style = AppTypography.codeSmall.copy(color = palette.textTertiary),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Test result summary (if available)
            env.lastTestResult?.let { result ->
                Divider(color = palette.divider, modifier = Modifier.padding(vertical = AppDimensions.spacingXs))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AppDimensions.spacingLg),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Pass rate
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AppDimensions.spacingXs)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(
                                    if (result.passRate >= 90) StatusColors.Success
                                    else if (result.passRate >= 70) StatusColors.Warning
                                    else StatusColors.Error
                                )
                        )
                        Text(
                            text = "${"%.1f".format(result.passRate)}%",
                            style = typography.body.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = if (result.passRate >= 90) StatusColors.Success
                                else if (result.passRate >= 70) StatusColors.Warning
                                else StatusColors.Error
                            )
                        )
                    }

                    // Tests count
                    Text(
                        text = "${result.passed}/${result.totalTests}",
                        style = typography.bodySmall.copy(color = palette.textSecondary)
                    )

                    // Avg response time
                    Text(
                        text = "${result.avgResponseTime}ms",
                        style = typography.bodySmall.copy(color = palette.textSecondary)
                    )

                    Spacer(Modifier.weight(1f))

                    // Timestamp
                    Text(
                        text = result.timestamp,
                        style = typography.caption.copy(color = palette.textTertiary)
                    )
                }
            }
        }
    }
}

@Composable
private fun ComparisonTable(environments: List<Environment>) {
    val palette = LocalAppPalette.current
    val typography = LocalAppTypography.current
    val strings = LocalAppStrings.current

    SectionCard(
        title = strings.multiEnvCompare,
        accentColor = BrandColors.Blue
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
            // Table header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(palette.surfaceVariant)
                    .padding(horizontal = AppDimensions.spacingMd, vertical = AppDimensions.spacingSm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = strings.multiEnvName,
                    style = typography.label.copy(color = palette.textSecondary),
                    modifier = Modifier.weight(2f)
                )
                Text(
                    text = "Pass Rate",
                    style = typography.label.copy(color = palette.textSecondary),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Tests",
                    style = typography.label.copy(color = palette.textSecondary),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Avg Response",
                    style = typography.label.copy(color = palette.textSecondary),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
                Text(
                    text = strings.multiEnvDefault,
                    style = typography.label.copy(color = palette.textSecondary),
                    modifier = Modifier.weight(0.7f),
                    textAlign = TextAlign.Center
                )
            }

            Divider(color = palette.divider)

            // Table rows
            environments.forEach { env ->
                val result = env.lastTestResult ?: return@forEach
                var rowHover by remember { mutableStateOf(false) }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (rowHover) palette.surfaceHover else Color.Transparent)
                        .hoverable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            enabled = true
                        )
                        .padding(horizontal = AppDimensions.spacingMd, vertical = AppDimensions.spacingSm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Name
                    Row(
                        modifier = Modifier.weight(2f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AppDimensions.spacingXs)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (env.isActive) StatusColors.Success else palette.textTertiary)
                        )
                        Text(
                            text = env.name,
                            style = typography.body.copy(
                                color = palette.textPrimary,
                                fontWeight = FontWeight.Medium
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Pass rate
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        val passColor = if (result.passRate >= 90) StatusColors.Success
                        else if (result.passRate >= 70) StatusColors.Warning
                        else StatusColors.Error
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(AppDimensions.chipRadius))
                                .background(passColor.copy(alpha = 0.12f))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "${"%.1f".format(result.passRate)}%",
                                style = typography.bodySmall.copy(
                                    color = passColor,
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                        }
                    }

                    // Tests
                    Text(
                        text = "${result.passed}/${result.totalTests}",
                        style = typography.bodySmall.copy(color = palette.textSecondary),
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center
                    )

                    // Avg response time
                    Text(
                        text = "${result.avgResponseTime}ms",
                        style = AppTypography.codeSmall.copy(color = palette.textSecondary),
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center
                    )

                    // Default indicator
                    Box(modifier = Modifier.weight(0.7f), contentAlignment = Alignment.Center) {
                        if (env.isDefault) {
                            Icon(
                                Icons.Default.Star,
                                contentDescription = strings.multiEnvDefault,
                                tint = BrandColors.Blue,
                                modifier = Modifier.size(AppDimensions.iconSizeSm)
                            )
                        }
                    }
                }

                Divider(color = palette.divider.copy(alpha = 0.5f))
            }
        }
    }
}
