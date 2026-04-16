package com.postsyncpro.presentation.screen.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.postsyncpro.presentation.i18n.Language
import com.postsyncpro.presentation.i18n.LocalAppStrings
import com.postsyncpro.presentation.navigation.NavigationState
import com.postsyncpro.presentation.navigation.Screen
import com.postsyncpro.presentation.theme.*

@Composable
fun SettingsScreen(navigationState: NavigationState) {
    val strings = LocalAppStrings.current
    val palette = LocalAppPalette.current
    val typography = LocalAppTypography.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val backInteraction = remember { MutableInteractionSource() }
            val backHovered by backInteraction.collectIsHoveredAsState()
            IconButton(
                onClick = { navigationState.navigateBackFromSettings() },
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(AppDimensions.buttonRadius))
                    .background(if (backHovered) palette.surfaceHover else Color.Transparent)
                    .hoverable(backInteraction)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = strings.back,
                    tint = palette.textSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    strings.settingsTitle,
                    style = typography.heading1.copy(color = palette.textPrimary)
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    strings.settingsSubtitle,
                    style = typography.bodySmall.copy(color = palette.textTertiary)
                )
            }
        }

        Spacer(Modifier.height(32.dp))

        // ─── Appearance Section ─────────────────────────────────
        SectionHeader(strings.appearance, Icons.Default.Palette, palette, typography)

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ThemeCard(
                title = strings.darkMode,
                description = strings.darkModeDesc,
                icon = Icons.Default.DarkMode,
                isSelected = navigationState.isDarkTheme,
                accentColor = BrandColors.Purple,
                palette = palette,
                typography = typography,
                onClick = { if (!navigationState.isDarkTheme) navigationState.toggleTheme() },
                modifier = Modifier.weight(1f)
            )
            ThemeCard(
                title = strings.lightMode,
                description = strings.lightModeDesc,
                icon = Icons.Default.LightMode,
                isSelected = !navigationState.isDarkTheme,
                accentColor = BrandColors.Orange,
                palette = palette,
                typography = typography,
                onClick = { if (navigationState.isDarkTheme) navigationState.toggleTheme() },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(32.dp))

        // ─── Language Section ───────────────────────────────────
        SectionHeader(strings.languageSettings, Icons.Default.Translate, palette, typography)

        Spacer(Modifier.height(4.dp))

        Text(
            strings.languageSettingsDesc,
            style = typography.bodySmall.copy(color = palette.textTertiary),
            modifier = Modifier.padding(start = 4.dp)
        )

        Spacer(Modifier.height(12.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(AppDimensions.cardRadius))
                .border(AppDimensions.borderWidth, palette.border, RoundedCornerShape(AppDimensions.cardRadius))
                .background(palette.surface)
        ) {
            Language.entries.forEachIndexed { index, lang ->
                val isSelected = navigationState.currentLanguage == lang
                val interaction = remember { MutableInteractionSource() }
                val isHovered by interaction.collectIsHoveredAsState()

                val bgColor by animateColorAsState(
                    when {
                        isSelected -> if (palette.isDark) BrandColors.Orange.copy(alpha = 0.1f) else BrandColors.Orange.copy(alpha = 0.08f)
                        isHovered -> palette.surfaceHover
                        else -> Color.Transparent
                    }
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(bgColor)
                        .hoverable(interaction)
                        .clickable { navigationState.setLanguage(lang) }
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Flag/Language indicator
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(
                                if (isSelected) BrandColors.Orange.copy(alpha = 0.15f)
                                else palette.surfaceVariant
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = when (lang) {
                                Language.ENGLISH -> "EN"
                                Language.ARABIC -> "ع"
                                Language.FRENCH -> "FR"
                                Language.GERMAN -> "DE"
                            },
                            style = typography.body.copy(
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) BrandColors.Orange else palette.textSecondary
                            )
                        )
                    }

                    Spacer(Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            lang.nativeName,
                            style = typography.body.copy(
                                color = palette.textPrimary,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                            )
                        )
                        Text(
                            lang.displayName,
                            style = typography.bodySmall.copy(color = palette.textTertiary)
                        )
                    }

                    if (isSelected) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = BrandColors.Orange,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                if (index < Language.entries.size - 1) {
                    HorizontalDivider(
                        color = palette.borderSubtle,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(32.dp))

        // ─── AI Configuration Section ────────────────────────────
        SectionHeader(strings.aiConfiguration, Icons.Default.Psychology, palette, typography)

        Spacer(Modifier.height(4.dp))

        Text(
            strings.aiConfigurationDesc,
            style = typography.bodySmall.copy(color = palette.textTertiary),
            modifier = Modifier.padding(start = 4.dp)
        )

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ThemeCard(
                title = strings.reconfigureAi,
                description = strings.reconfigureAiDesc,
                icon = Icons.Default.Tune,
                isSelected = false,
                accentColor = BrandColors.Teal,
                palette = palette,
                typography = typography,
                onClick = { navigationState.resetToOnboarding() },
                modifier = Modifier.weight(1f)
            )
            ThemeCard(
                title = strings.screenSetup,
                description = strings.settingsSubtitle,
                icon = Icons.Default.SettingsApplications,
                isSelected = false,
                accentColor = BrandColors.Purple,
                palette = palette,
                typography = typography,
                onClick = { navigationState.navigateTo(Screen.ONBOARDING) },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(32.dp))

        // ─── About Section ──────────────────────────────────────
        SectionHeader(strings.about, Icons.Default.Info, palette, typography)

        Spacer(Modifier.height(12.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(AppDimensions.cardRadius))
                .border(AppDimensions.borderWidth, palette.border, RoundedCornerShape(AppDimensions.cardRadius))
                .background(palette.surface)
                .padding(20.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(BrandColors.Orange)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "PostSync Pro",
                        style = typography.heading2.copy(color = palette.textPrimary)
                    )
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(AppDimensions.chipRadius))
                            .background(BrandColors.Orange.copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            strings.version,
                            style = typography.caption.copy(
                                color = BrandColors.Orange,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                Text(
                    strings.aboutDesc,
                    style = typography.body.copy(color = palette.textSecondary, lineHeight = 22.sp)
                )

                Spacer(Modifier.height(16.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    InfoChip("Kotlin + Compose", palette, typography)
                    InfoChip("12 Modules", palette, typography)
                    InfoChip("4 Languages", palette, typography)
                    InfoChip("AI-Powered", palette, typography)
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SectionHeader(
    title: String,
    icon: ImageVector,
    palette: AppPalette,
    typography: AppTextStyles
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            icon,
            contentDescription = null,
            tint = BrandColors.Orange,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            title,
            style = typography.heading3.copy(color = palette.textPrimary)
        )
    }
}

@Composable
private fun ThemeCard(
    title: String,
    description: String,
    icon: ImageVector,
    isSelected: Boolean,
    accentColor: Color,
    palette: AppPalette,
    typography: AppTextStyles,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    val isHovered by interaction.collectIsHoveredAsState()

    val borderColor by animateColorAsState(
        when {
            isSelected -> accentColor
            isHovered -> palette.border
            else -> palette.borderSubtle
        }
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(AppDimensions.cardRadius))
            .border(
                width = if (isSelected) 2.dp else AppDimensions.borderWidth,
                color = borderColor,
                shape = RoundedCornerShape(AppDimensions.cardRadius)
            )
            .background(if (isSelected) accentColor.copy(alpha = 0.05f) else palette.surface)
            .hoverable(interaction)
            .clickable(onClick = onClick)
            .padding(20.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(Modifier.weight(1f))
                if (isSelected) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                title,
                style = typography.heading3.copy(
                    color = if (isSelected) accentColor else palette.textPrimary
                )
            )
            Spacer(Modifier.height(4.dp))
            Text(
                description,
                style = typography.bodySmall.copy(color = palette.textTertiary)
            )
        }
    }
}

@Composable
private fun InfoChip(text: String, palette: AppPalette, typography: AppTextStyles) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(AppDimensions.chipRadius))
            .background(palette.surfaceVariant)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text,
            style = typography.caption.copy(color = palette.textSecondary, fontWeight = FontWeight.Medium)
        )
    }
}
