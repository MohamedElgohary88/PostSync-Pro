package com.postsyncpro.presentation.screen.pro

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.postsyncpro.presentation.i18n.LocalAppStrings
import com.postsyncpro.presentation.navigation.NavigationState
import com.postsyncpro.presentation.navigation.Screen
import com.postsyncpro.presentation.theme.*

data class ProFeature(
    val icon: ImageVector,
    val titleKey: String,
    val descKey: String,
    val hintKey: String,
    val tier: String, // "pro" or "team"
    val screen: Screen,
    val accentColor: Color
)

@Composable
fun ProFeaturesScreen(navigationState: NavigationState) {
    val strings = LocalAppStrings.current
    val palette = LocalAppPalette.current
    val typography = LocalAppTypography.current

    val features = listOf(
        ProFeature(Icons.Default.Schedule, strings.proFeatureScheduler, strings.proFeatureSchedulerDesc, strings.proHintScheduler, "pro", Screen.SCHEDULER, BrandColors.Orange),
        ProFeature(Icons.Default.Tune, strings.proFeatureCustomFuzz, strings.proFeatureCustomFuzzDesc, strings.proHintCustomFuzz, "pro", Screen.CUSTOM_FUZZ_RULES, BrandColors.Red),
        ProFeature(Icons.Default.Timeline, strings.proFeatureTestHistory, strings.proFeatureTestHistoryDesc, strings.proHintTestHistory, "pro", Screen.TEST_HISTORY, BrandColors.Blue),
        ProFeature(Icons.Default.Brush, strings.proFeatureBrandedReport, strings.proFeatureBrandedReportDesc, strings.proHintBrandedReport, "pro", Screen.BRANDED_REPORT, BrandColors.Purple),
        ProFeature(Icons.Default.Notifications, strings.proFeatureNotifications, strings.proFeatureNotificationsDesc, strings.proHintNotifications, "pro", Screen.NOTIFICATIONS, BrandColors.Green),
        ProFeature(Icons.Default.BugReport, strings.proFeatureIssueTracker, strings.proFeatureIssueTrackerDesc, strings.proHintIssueTracker, "pro", Screen.ISSUE_TRACKER, BrandColors.OrangeDark),
        ProFeature(Icons.Default.MonitorHeart, strings.proFeatureApiMonitor, strings.proFeatureApiMonitorDesc, strings.proHintApiMonitor, "team", Screen.API_MONITOR, BrandColors.Teal),
        ProFeature(Icons.Default.Cloud, strings.proFeatureMultiEnv, strings.proFeatureMultiEnvDesc, strings.proHintMultiEnv, "team", Screen.MULTI_ENV, BrandColors.Blue),
        ProFeature(Icons.Default.Terminal, strings.proFeatureCiCd, strings.proFeatureCiCdDesc, strings.proHintCiCd, "pro", Screen.CICD_EXPORT, Color(0xFF6366F1)),
        ProFeature(Icons.Default.Psychology, strings.proFeatureSmartTests, strings.proFeatureSmartTestsDesc, strings.proHintSmartTests, "pro", Screen.SMART_TESTS, Color(0xFFEC4899)),
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // Header with back button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val backInteraction = remember { MutableInteractionSource() }
            val backHovered by backInteraction.collectIsHoveredAsState()
            IconButton(
                onClick = { navigationState.navigateBackFromPro() },
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        strings.proTitle,
                        style = typography.heading1.copy(color = palette.textPrimary)
                    )
                    Spacer(Modifier.width(10.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(AppDimensions.chipRadius))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(BrandColors.Orange, BrandColors.OrangeDark)
                                )
                            )
                            .padding(horizontal = 10.dp, vertical = 3.dp)
                    ) {
                        Text(
                            strings.proFeaturesCount,
                            style = typography.caption.copy(
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    strings.proSubtitle,
                    style = typography.bodySmall.copy(color = palette.textTertiary)
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        // Pricing tiers
        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            TierCard(
                name = strings.proFreeTier,
                description = strings.proFreeTierDesc,
                price = "$0",
                isCurrentPlan = true,
                accentColor = palette.textTertiary,
                palette = palette,
                typography = typography,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
            TierCard(
                name = strings.proProTier,
                description = strings.proProTierDesc,
                price = "$19/mo",
                isCurrentPlan = false,
                accentColor = BrandColors.Orange,
                palette = palette,
                typography = typography,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
            TierCard(
                name = strings.proTeamTier,
                description = strings.proTeamTierDesc,
                price = "$49/mo",
                isCurrentPlan = false,
                accentColor = BrandColors.Purple,
                palette = palette,
                typography = typography,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
        }

        Spacer(Modifier.height(32.dp))

        // Pro Features Section
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Star, contentDescription = null, tint = BrandColors.Orange, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(strings.proProTier, style = typography.heading3.copy(color = palette.textPrimary))
            Spacer(Modifier.width(8.dp))
            Text("Features", style = typography.heading3.copy(color = palette.textTertiary))
        }

        Spacer(Modifier.height(16.dp))

        // Feature Grid - 2 columns
        val proFeatures = features.filter { it.tier == "pro" }
        for (i in proFeatures.indices step 2) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                ProFeatureCard(
                    feature = proFeatures[i],
                    palette = palette,
                    typography = typography,
                    onClick = { navigationState.navigateTo(proFeatures[i].screen) },
                    modifier = Modifier.weight(1f)
                )
                if (i + 1 < proFeatures.size) {
                    ProFeatureCard(
                        feature = proFeatures[i + 1],
                        palette = palette,
                        typography = typography,
                        onClick = { navigationState.navigateTo(proFeatures[i + 1].screen) },
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        Spacer(Modifier.height(16.dp))

        // Team Features Section
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Groups, contentDescription = null, tint = BrandColors.Purple, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(strings.proTeamTier, style = typography.heading3.copy(color = palette.textPrimary))
            Spacer(Modifier.width(8.dp))
            Text("Features", style = typography.heading3.copy(color = palette.textTertiary))
        }

        Spacer(Modifier.height(16.dp))

        val teamFeatures = features.filter { it.tier == "team" }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            teamFeatures.forEach { feature ->
                ProFeatureCard(
                    feature = feature,
                    palette = palette,
                    typography = typography,
                    onClick = { navigationState.navigateTo(feature.screen) },
                    modifier = Modifier.weight(1f)
                )
            }
            if (teamFeatures.size == 1) Spacer(Modifier.weight(1f))
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun TierCard(
    name: String,
    description: String,
    price: String,
    isCurrentPlan: Boolean,
    accentColor: Color,
    palette: AppPalette,
    typography: AppTextStyles,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    val interaction = remember { MutableInteractionSource() }
    val isHovered by interaction.collectIsHoveredAsState()

    val tierElevation by animateDpAsState(
        targetValue = if (isHovered && !isCurrentPlan) 6.dp else 0.dp,
        animationSpec = tween(200)
    )

    val borderAlpha by animateColorAsState(
        if (isHovered) accentColor.copy(alpha = 0.5f)
        else if (isCurrentPlan) accentColor
        else palette.borderSubtle
    )

    Box(
        modifier = modifier
            .shadow(
                elevation = tierElevation,
                shape = RoundedCornerShape(AppDimensions.cardRadius),
                ambientColor = accentColor.copy(alpha = 0.15f),
                spotColor = accentColor.copy(alpha = 0.1f)
            )
            .clip(RoundedCornerShape(AppDimensions.cardRadius))
            .border(
                width = if (isCurrentPlan || isHovered) 2.dp else AppDimensions.borderWidth,
                color = borderAlpha,
                shape = RoundedCornerShape(AppDimensions.cardRadius)
            )
            .background(
                if (isHovered && !isCurrentPlan) accentColor.copy(alpha = 0.03f)
                else palette.surface
            )
            .hoverable(interaction)
            .padding(20.dp)
    ) {
        Column(modifier = Modifier.fillMaxHeight()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!isCurrentPlan) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    name,
                    style = typography.heading3.copy(color = palette.textPrimary, fontWeight = FontWeight.Bold)
                )
                Spacer(Modifier.weight(1f))
                if (isCurrentPlan) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(AppDimensions.chipRadius))
                            .background(accentColor.copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            strings.proCurrentPlan,
                            style = typography.caption.copy(color = accentColor, fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                price,
                style = typography.heading1.copy(color = accentColor, fontWeight = FontWeight.Bold)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                description,
                style = typography.bodySmall.copy(color = palette.textTertiary)
            )
            Spacer(Modifier.weight(1f))
            if (!isCurrentPlan) {
                val btnInteraction = remember { MutableInteractionSource() }
                val btnHovered by btnInteraction.collectIsHoveredAsState()
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(AppDimensions.buttonRadius))
                        .background(if (btnHovered) accentColor.copy(alpha = 0.85f) else accentColor)
                        .hoverable(btnInteraction)
                        .clickable { /* upgrade flow later */ }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.RocketLaunch,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            strings.proUpgrade,
                            style = typography.body.copy(color = Color.White, fontWeight = FontWeight.SemiBold)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProFeatureCard(
    feature: ProFeature,
    palette: AppPalette,
    typography: AppTextStyles,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    val interaction = remember { MutableInteractionSource() }
    val isHovered by interaction.collectIsHoveredAsState()

    val borderColor by animateColorAsState(
        if (isHovered) feature.accentColor else palette.borderSubtle
    )

    val cardElevation by animateDpAsState(
        targetValue = if (isHovered) 4.dp else 0.dp,
        animationSpec = tween(durationMillis = 200)
    )

    Box(
        modifier = modifier
            .shadow(
                elevation = cardElevation,
                shape = RoundedCornerShape(AppDimensions.cardRadius),
                ambientColor = feature.accentColor.copy(alpha = 0.15f),
                spotColor = feature.accentColor.copy(alpha = 0.1f)
            )
            .clip(RoundedCornerShape(AppDimensions.cardRadius))
            .border(AppDimensions.borderWidth, borderColor, RoundedCornerShape(AppDimensions.cardRadius))
            .background(if (isHovered) feature.accentColor.copy(alpha = 0.04f) else palette.surface)
            .hoverable(interaction)
            .clickable(onClick = onClick)
    ) {
        // Subtle gradient overlay on hover
        if (isHovered) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                feature.accentColor.copy(alpha = 0.05f),
                                Color.Transparent
                            )
                        )
                    )
            )
        }

        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                listOf(
                                    feature.accentColor.copy(alpha = 0.18f),
                                    feature.accentColor.copy(alpha = 0.08f)
                                )
                            )
                        )
                        .border(
                            width = 1.dp,
                            color = feature.accentColor.copy(alpha = 0.15f),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        feature.icon,
                        contentDescription = null,
                        tint = feature.accentColor,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(AppDimensions.chipRadius))
                        .background(feature.accentColor.copy(alpha = 0.12f))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        if (feature.tier == "pro") strings.proProTier else strings.proTeamTier,
                        style = typography.caption.copy(
                            color = feature.accentColor,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                feature.titleKey,
                style = typography.body.copy(color = palette.textPrimary, fontWeight = FontWeight.SemiBold)
            )
            Spacer(Modifier.height(6.dp))
            Text(
                feature.descKey,
                style = typography.bodySmall.copy(color = palette.textTertiary, lineHeight = 18.sp)
            )
            Spacer(Modifier.height(10.dp))

            // Hint/tip row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(feature.accentColor.copy(alpha = 0.06f))
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.TipsAndUpdates,
                    contentDescription = null,
                    tint = feature.accentColor.copy(alpha = 0.7f),
                    modifier = Modifier.size(13.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    feature.hintKey,
                    style = typography.caption.copy(
                        color = feature.accentColor.copy(alpha = 0.85f),
                        lineHeight = 14.sp
                    ),
                    maxLines = 2
                )
            }

            Spacer(Modifier.height(14.dp))

            val openInteraction = remember { MutableInteractionSource() }
            val openHovered by openInteraction.collectIsHoveredAsState()
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .then(
                        if (!openHovered) Modifier.border(
                            width = 1.dp,
                            color = feature.accentColor.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(20.dp)
                        ) else Modifier
                    )
                    .background(if (openHovered) feature.accentColor else feature.accentColor.copy(alpha = 0.1f))
                    .hoverable(openInteraction)
                    .clickable(onClick = onClick)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    strings.proOpen,
                    style = typography.bodySmall.copy(
                        color = if (openHovered) Color.White else feature.accentColor,
                        fontWeight = FontWeight.SemiBold
                    )
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = if (openHovered) Color.White else feature.accentColor,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}
