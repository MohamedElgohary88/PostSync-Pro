package com.postsyncpro.presentation.screen.brandedreport

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.postsyncpro.presentation.components.SectionCard
import com.postsyncpro.presentation.i18n.LocalAppStrings
import com.postsyncpro.presentation.theme.*
import java.awt.FileDialog
import java.awt.Frame
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private fun parseHexColor(hex: String): Color {
    return try {
        val cleaned = hex.removePrefix("#")
        val rgb = cleaned.toLong(16)
        when (cleaned.length) {
            6 -> Color(
                red = ((rgb shr 16) and 0xFF) / 255f,
                green = ((rgb shr 8) and 0xFF) / 255f,
                blue = (rgb and 0xFF) / 255f
            )
            8 -> Color(
                alpha = ((rgb shr 24) and 0xFF) / 255f,
                red = ((rgb shr 16) and 0xFF) / 255f,
                green = ((rgb shr 8) and 0xFF) / 255f,
                blue = (rgb and 0xFF) / 255f
            )
            else -> BrandColors.Purple
        }
    } catch (_: Exception) {
        BrandColors.Purple
    }
}

@Composable
fun BrandedReportScreen(viewModel: BrandedReportViewModel) {
    val state by viewModel.state.collectAsState()
    val strings = LocalAppStrings.current
    val palette = LocalAppPalette.current
    val typography = LocalAppTypography.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
    ) {
        // Header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = AppDimensions.spacingLg,
                    end = AppDimensions.spacingLg,
                    top = AppDimensions.spacingLg,
                    bottom = AppDimensions.spacingSm
                )
        ) {
            Text(
                text = strings.brandedReportTitle,
                style = typography.heading1.copy(color = palette.textPrimary)
            )
            Spacer(Modifier.height(AppDimensions.spacingXs))
            Text(
                text = strings.brandedReportSubtitle,
                style = typography.bodySmall.copy(color = palette.textTertiary)
            )
        }

        // Success message
        AnimatedVisibility(
            visible = state.saveMessage != null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            state.saveMessage?.let { message ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AppDimensions.spacingLg)
                        .padding(bottom = AppDimensions.spacingSm)
                        .background(
                            StatusColors.Success.copy(alpha = 0.1f),
                            RoundedCornerShape(AppDimensions.cornerSmall)
                        )
                        .padding(AppDimensions.spacingSm),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppDimensions.spacingSm)
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = StatusColors.Success,
                        modifier = Modifier.size(AppDimensions.iconSizeMd)
                    )
                    Text(
                        text = message,
                        style = typography.body.copy(color = StatusColors.Success)
                    )
                }
            }
        }

        // Main content: Left config + Right preview
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = AppDimensions.spacingLg)
                .padding(bottom = AppDimensions.spacingLg),
            horizontalArrangement = Arrangement.spacedBy(AppDimensions.spacingMd)
        ) {
            // ─── Left Panel: Configuration Form ────────────────────
            Column(
                modifier = Modifier
                    .weight(0.5f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(AppDimensions.spacingMd)
            ) {
                // Company Name
                SectionCard(
                    title = strings.brandedReportCompanyName,
                    accentColor = BrandColors.Purple,
                    icon = Icons.Default.Business
                ) {
                    OutlinedTextField(
                        value = state.config.companyName,
                        onValueChange = { viewModel.updateConfig(state.config.copy(companyName = it)) },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = {
                            Text(
                                strings.brandedReportCompanyNamePlaceholder,
                                style = typography.body.copy(color = palette.textTertiary)
                            )
                        },
                        textStyle = typography.body.copy(color = palette.textPrimary),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = palette.textPrimary,
                            unfocusedTextColor = palette.textPrimary,
                            focusedContainerColor = palette.inputBackground,
                            unfocusedContainerColor = palette.inputBackground,
                            focusedBorderColor = BrandColors.Purple,
                            unfocusedBorderColor = palette.inputBorder,
                            cursorColor = BrandColors.Purple
                        ),
                        shape = RoundedCornerShape(AppDimensions.inputRadius)
                    )
                }

                // Logo Selector
                SectionCard(
                    title = strings.brandedReportLogo,
                    accentColor = BrandColors.Purple,
                    icon = Icons.Default.Image
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(AppDimensions.spacingSm)
                    ) {
                        val logoInteraction = remember { MutableInteractionSource() }
                        val logoHovered by logoInteraction.collectIsHoveredAsState()
                        val logoBgColor by animateColorAsState(
                            if (logoHovered) BrandColors.Purple.copy(alpha = 0.15f) else palette.surfaceVariant
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(AppDimensions.inputRadius))
                                .background(logoBgColor)
                                .border(
                                    AppDimensions.borderWidth,
                                    if (logoHovered) BrandColors.Purple else palette.inputBorder,
                                    RoundedCornerShape(AppDimensions.inputRadius)
                                )
                                .hoverable(logoInteraction)
                                .clickable {
                                    val dialog = FileDialog(null as Frame?, strings.brandedReportSelectLogo, FileDialog.LOAD)
                                    dialog.setFilenameFilter { _, name ->
                                        name.endsWith(".png") || name.endsWith(".jpg") ||
                                                name.endsWith(".jpeg") || name.endsWith(".svg")
                                    }
                                    dialog.isVisible = true
                                    val dir = dialog.directory
                                    val file = dialog.file
                                    if (dir != null && file != null) {
                                        viewModel.setLogoPath("$dir$file")
                                    }
                                }
                                .padding(AppDimensions.spacingMd),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(AppDimensions.spacingSm)
                        ) {
                            Icon(
                                Icons.Default.UploadFile,
                                contentDescription = null,
                                tint = BrandColors.Purple,
                                modifier = Modifier.size(AppDimensions.iconSizeMd)
                            )
                            Text(
                                text = strings.brandedReportSelectLogo,
                                style = typography.button.copy(color = BrandColors.Purple)
                            )
                        }

                        if (state.config.logoPath != null) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        StatusColors.Success.copy(alpha = 0.1f),
                                        RoundedCornerShape(AppDimensions.cornerSmall)
                                    )
                                    .padding(AppDimensions.spacingXs),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(AppDimensions.spacingXs)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(StatusColors.Success)
                                )
                                Text(
                                    text = strings.brandedReportLogoSelected,
                                    style = typography.bodySmall.copy(color = StatusColors.Success)
                                )
                            }
                        }
                    }
                }

                // Primary Color
                SectionCard(
                    title = strings.brandedReportPrimaryColor,
                    accentColor = BrandColors.Purple,
                    icon = Icons.Default.ColorLens
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AppDimensions.spacingSm)
                    ) {
                        // Color preview swatch
                        val parsedColor = parseHexColor(state.config.primaryColorHex)
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(AppDimensions.inputRadius))
                                .background(parsedColor)
                                .border(
                                    AppDimensions.borderWidth,
                                    palette.border,
                                    RoundedCornerShape(AppDimensions.inputRadius)
                                )
                        )
                        OutlinedTextField(
                            value = state.config.primaryColorHex,
                            onValueChange = { viewModel.updateConfig(state.config.copy(primaryColorHex = it)) },
                            modifier = Modifier.weight(1f),
                            textStyle = typography.code.copy(color = palette.textPrimary),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = palette.textPrimary,
                                unfocusedTextColor = palette.textPrimary,
                                focusedContainerColor = palette.inputBackground,
                                unfocusedContainerColor = palette.inputBackground,
                                focusedBorderColor = BrandColors.Purple,
                                unfocusedBorderColor = palette.inputBorder,
                                cursorColor = BrandColors.Purple
                            ),
                            shape = RoundedCornerShape(AppDimensions.inputRadius)
                        )
                    }
                }

                // Footer Text
                SectionCard(
                    title = strings.brandedReportFooterText,
                    accentColor = BrandColors.Purple,
                    icon = Icons.Default.Notes
                ) {
                    OutlinedTextField(
                        value = state.config.footerText,
                        onValueChange = { viewModel.updateConfig(state.config.copy(footerText = it)) },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = {
                            Text(
                                strings.brandedReportFooterPlaceholder,
                                style = typography.body.copy(color = palette.textTertiary)
                            )
                        },
                        textStyle = typography.body.copy(color = palette.textPrimary),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = palette.textPrimary,
                            unfocusedTextColor = palette.textPrimary,
                            focusedContainerColor = palette.inputBackground,
                            unfocusedContainerColor = palette.inputBackground,
                            focusedBorderColor = BrandColors.Purple,
                            unfocusedBorderColor = palette.inputBorder,
                            cursorColor = BrandColors.Purple
                        ),
                        shape = RoundedCornerShape(AppDimensions.inputRadius)
                    )
                }

                // Include Options (checkboxes)
                SectionCard(
                    title = strings.brandedReportPreview,
                    accentColor = BrandColors.Purple,
                    icon = Icons.Default.Tune
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(AppDimensions.spacingXs)
                    ) {
                        BrandedCheckboxRow(
                            label = strings.brandedReportIncludeLogo,
                            checked = state.config.includeLogo,
                            onCheckedChange = {
                                viewModel.updateConfig(state.config.copy(includeLogo = it))
                            },
                            palette = palette,
                            typography = typography
                        )
                        BrandedCheckboxRow(
                            label = strings.brandedReportIncludeDate,
                            checked = state.config.includeDate,
                            onCheckedChange = {
                                viewModel.updateConfig(state.config.copy(includeDate = it))
                            },
                            palette = palette,
                            typography = typography
                        )
                        BrandedCheckboxRow(
                            label = strings.brandedReportIncludePageNumbers,
                            checked = state.config.includePageNumbers,
                            onCheckedChange = {
                                viewModel.updateConfig(state.config.copy(includePageNumbers = it))
                            },
                            palette = palette,
                            typography = typography
                        )
                    }
                }

                // Template Selector
                SectionCard(
                    title = strings.brandedReportTemplate,
                    accentColor = BrandColors.Purple,
                    icon = Icons.Default.Description
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(AppDimensions.spacingSm)
                    ) {
                        ReportTemplate.entries.forEach { template ->
                            val isSelected = state.config.template == template
                            val interaction = remember { MutableInteractionSource() }
                            val isHovered by interaction.collectIsHoveredAsState()
                            val bgColor by animateColorAsState(
                                when {
                                    isSelected -> BrandColors.Purple.copy(alpha = 0.15f)
                                    isHovered -> palette.surfaceHover
                                    else -> palette.surfaceVariant
                                }
                            )
                            val borderColor by animateColorAsState(
                                if (isSelected) BrandColors.Purple else palette.borderSubtle
                            )

                            val templateLabel = when (template) {
                                ReportTemplate.MINIMAL -> strings.brandedReportTemplateMinimal
                                ReportTemplate.DETAILED -> strings.brandedReportTemplateDetailed
                                ReportTemplate.EXECUTIVE -> strings.brandedReportTemplateExecutive
                            }
                            val templateIcon = when (template) {
                                ReportTemplate.MINIMAL -> Icons.Default.ViewStream
                                ReportTemplate.DETAILED -> Icons.Default.ViewList
                                ReportTemplate.EXECUTIVE -> Icons.Default.Summarize
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(AppDimensions.inputRadius))
                                    .background(bgColor)
                                    .border(AppDimensions.borderWidth, borderColor, RoundedCornerShape(AppDimensions.inputRadius))
                                    .hoverable(interaction)
                                    .clickable {
                                        viewModel.updateConfig(state.config.copy(template = template))
                                    }
                                    .padding(AppDimensions.spacingMd),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(AppDimensions.spacingSm)
                            ) {
                                Icon(
                                    templateIcon,
                                    contentDescription = null,
                                    tint = if (isSelected) BrandColors.Purple else palette.textTertiary,
                                    modifier = Modifier.size(AppDimensions.iconSizeMd)
                                )
                                Text(
                                    text = templateLabel,
                                    style = typography.body.copy(
                                        color = if (isSelected) BrandColors.Purple else palette.textPrimary,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                    )
                                )
                                Spacer(Modifier.weight(1f))
                                if (isSelected) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = BrandColors.Purple,
                                        modifier = Modifier.size(AppDimensions.iconSizeSm)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ─── Right Panel: Live Preview ─────────────────────────
            Column(
                modifier = Modifier
                    .weight(0.5f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(AppDimensions.spacingMd)
            ) {
                SectionCard(
                    title = strings.brandedReportPreview,
                    accentColor = BrandColors.Purple,
                    icon = Icons.Default.Visibility,
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Preview: Simulated report header
                        val previewColor = parseHexColor(state.config.primaryColorHex)

                        // Report page simulation
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(AppDimensions.cardRadius))
                                .background(Color.White)
                                .border(
                                    AppDimensions.borderWidth,
                                    palette.border,
                                    RoundedCornerShape(AppDimensions.cardRadius)
                                )
                        ) {
                            // Color banner at top
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .background(previewColor)
                            )

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(AppDimensions.spacingLg)
                            ) {
                                // Header row: logo placeholder + company name
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(AppDimensions.spacingSm)
                                ) {
                                    if (state.config.includeLogo) {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(RoundedCornerShape(AppDimensions.cornerSmall))
                                                .background(previewColor.copy(alpha = 0.15f))
                                                .border(
                                                    AppDimensions.borderWidth,
                                                    previewColor.copy(alpha = 0.3f),
                                                    RoundedCornerShape(AppDimensions.cornerSmall)
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (state.config.logoPath != null) {
                                                Icon(
                                                    Icons.Default.CheckCircle,
                                                    contentDescription = null,
                                                    tint = previewColor,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            } else {
                                                Icon(
                                                    Icons.Default.Image,
                                                    contentDescription = null,
                                                    tint = previewColor.copy(alpha = 0.5f),
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                    }

                                    Column {
                                        Text(
                                            text = state.config.companyName.ifEmpty { "Company Name" },
                                            style = typography.heading2.copy(
                                                color = Color(0xFF212121),
                                                fontWeight = FontWeight.Bold
                                            )
                                        )
                                        if (state.config.includeDate) {
                                            Text(
                                                text = LocalDate.now().format(DateTimeFormatter.ofPattern("MMMM d, yyyy")),
                                                style = typography.bodySmall.copy(color = Color(0xFF757575))
                                            )
                                        }
                                    }
                                }

                                Spacer(Modifier.height(AppDimensions.spacingMd))

                                // Divider
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(2.dp)
                                        .background(previewColor)
                                )

                                Spacer(Modifier.height(AppDimensions.spacingMd))

                                // Report title
                                Text(
                                    text = "QA Report",
                                    style = typography.heading3.copy(color = Color(0xFF212121))
                                )

                                Spacer(Modifier.height(AppDimensions.spacingSm))

                                // Template-specific content preview
                                when (state.config.template) {
                                    ReportTemplate.MINIMAL -> {
                                        PreviewContentLine(0.7f, previewColor)
                                        Spacer(Modifier.height(6.dp))
                                        PreviewContentLine(0.5f, previewColor)
                                    }
                                    ReportTemplate.DETAILED -> {
                                        PreviewContentLine(0.9f, previewColor)
                                        Spacer(Modifier.height(6.dp))
                                        PreviewContentLine(0.75f, previewColor)
                                        Spacer(Modifier.height(6.dp))
                                        PreviewContentLine(0.6f, previewColor)
                                        Spacer(Modifier.height(6.dp))
                                        PreviewContentLine(0.85f, previewColor)
                                    }
                                    ReportTemplate.EXECUTIVE -> {
                                        PreviewContentLine(0.8f, previewColor)
                                        Spacer(Modifier.height(6.dp))
                                        PreviewContentLine(0.65f, previewColor)
                                        Spacer(Modifier.height(6.dp))
                                        PreviewContentLine(0.45f, previewColor)
                                    }
                                }

                                Spacer(Modifier.height(AppDimensions.spacingLg))

                                // Footer area
                                if (state.config.footerText.isNotEmpty() || state.config.includePageNumbers) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(1.dp)
                                            .background(Color(0xFFE0E0E0))
                                    )
                                    Spacer(Modifier.height(AppDimensions.spacingXs))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = state.config.footerText,
                                            style = typography.caption.copy(color = Color(0xFF9E9E9E)),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        if (state.config.includePageNumbers) {
                                            Text(
                                                text = "1 / 1",
                                                style = typography.caption.copy(color = Color(0xFF9E9E9E))
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(AppDimensions.spacingMd))

                        // Template badge
                        val templateName = when (state.config.template) {
                            ReportTemplate.MINIMAL -> strings.brandedReportTemplateMinimal
                            ReportTemplate.DETAILED -> strings.brandedReportTemplateDetailed
                            ReportTemplate.EXECUTIVE -> strings.brandedReportTemplateExecutive
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(AppDimensions.chipRadius))
                                    .background(BrandColors.Purple.copy(alpha = 0.12f))
                                    .padding(horizontal = AppDimensions.spacingSm, vertical = AppDimensions.spacingXs)
                            ) {
                                Text(
                                    text = templateName,
                                    style = typography.caption.copy(color = BrandColors.Purple)
                                )
                            }
                        }
                    }
                }
            }
        }

        // ─── Bottom Action Bar ─────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppDimensions.spacingLg)
                .padding(bottom = AppDimensions.spacingLg),
            horizontalArrangement = Arrangement.spacedBy(AppDimensions.spacingSm)
        ) {
            // Reset button
            val resetInteraction = remember { MutableInteractionSource() }
            val resetHovered by resetInteraction.collectIsHoveredAsState()
            val resetBg by animateColorAsState(
                if (resetHovered) palette.surfaceHover else palette.surfaceVariant
            )

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(AppDimensions.buttonRadius))
                    .background(resetBg)
                    .border(AppDimensions.borderWidth, palette.border, RoundedCornerShape(AppDimensions.buttonRadius))
                    .hoverable(resetInteraction)
                    .clickable { viewModel.resetConfig() }
                    .padding(horizontal = AppDimensions.spacingLg, vertical = AppDimensions.spacingMd)
            ) {
                Text(
                    text = strings.brandedReportReset,
                    style = typography.button.copy(color = palette.textSecondary)
                )
            }

            Spacer(Modifier.weight(1f))

            // Save button
            val saveInteraction = remember { MutableInteractionSource() }
            val saveHovered by saveInteraction.collectIsHoveredAsState()
            val saveBg by animateColorAsState(
                if (saveHovered) BrandColors.PurpleLight else BrandColors.Purple
            )

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(AppDimensions.buttonRadius))
                    .background(saveBg)
                    .hoverable(saveInteraction)
                    .clickable { viewModel.saveConfig() }
                    .padding(horizontal = AppDimensions.spacingXl, vertical = AppDimensions.spacingMd)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppDimensions.spacingSm)
                ) {
                    Icon(
                        Icons.Default.Save,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(AppDimensions.iconSizeSm)
                    )
                    Text(
                        text = strings.brandedReportSaveConfig,
                        style = typography.button.copy(color = Color.White)
                    )
                }
            }
        }
    }
}

// ─── Helper Composables ────────────────────────────────────────────

@Composable
private fun BrandedCheckboxRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    palette: AppPalette,
    typography: AppTextStyles
) {
    val interaction = remember { MutableInteractionSource() }
    val isHovered by interaction.collectIsHoveredAsState()
    val bgColor by animateColorAsState(
        if (isHovered) palette.surfaceHover else Color.Transparent
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppDimensions.inputRadius))
            .background(bgColor)
            .hoverable(interaction)
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = AppDimensions.spacingSm, vertical = AppDimensions.spacingXs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppDimensions.spacingSm)
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = CheckboxDefaults.colors(
                checkedColor = BrandColors.Purple,
                uncheckedColor = palette.textTertiary,
                checkmarkColor = Color.White
            )
        )
        Text(
            text = label,
            style = typography.body.copy(color = palette.textPrimary)
        )
    }
}

@Composable
private fun PreviewContentLine(widthFraction: Float, color: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth(widthFraction)
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.12f))
    )
}
