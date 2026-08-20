package com.postsyncpro.presentation.components

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.postsyncpro.presentation.i18n.Language
import com.postsyncpro.presentation.i18n.LocalAppStrings
import com.postsyncpro.presentation.navigation.NavigationState
import com.postsyncpro.presentation.navigation.Screen
import com.postsyncpro.presentation.theme.*

data class NavItem(
    val screen: Screen,
    val icon: ImageVector,
    val label: String,
    val badge: String? = null,
    val description: String = ""
)

data class NavSection(
    val title: String,
    val items: List<NavItem>
)

@Composable
fun AppSidebar(
    navigationState: NavigationState,
    modifier: Modifier = Modifier
) {
    val palette = LocalAppPalette.current
    val typography = LocalAppTypography.current
    val strings = LocalAppStrings.current
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    val sections = listOf(
        NavSection(strings.sectionWorkspace, listOf(
            NavItem(Screen.WORKBENCH, Icons.Default.Http, strings.screenWorkbench, strings.badgeNew, description = strings.hintWorkbench),
            NavItem(Screen.SYNC, Icons.Default.SyncAlt, strings.screenPostmanSync, description = strings.hintPostmanSync),
            NavItem(Screen.VALIDATOR, Icons.AutoMirrored.Filled.FactCheck, strings.screenSrsValidator, description = strings.hintSrsValidator),
            NavItem(Screen.FUZZ, Icons.Default.BugReport, strings.screenFuzzTesting, description = strings.hintFuzzTesting)
        )),
        NavSection(strings.sectionTools, listOf(
            NavItem(Screen.MOCK_SERVER, Icons.Default.Dns, strings.screenMockServer, strings.badgeNew, description = strings.hintMockServer),
            NavItem(Screen.TEST_CHAIN, Icons.Default.AccountTree, strings.screenTestChain, strings.badgeNew, description = strings.hintTestChain),
            NavItem(Screen.AUTO_FLOW, Icons.Default.RocketLaunch, strings.screenAutoFlow, strings.badgeNew, description = strings.hintAutoFlow)
        )),
        NavSection(strings.sectionAnalytics, listOf(
            NavItem(Screen.REPORT, Icons.Default.Description, strings.screenQaReport, strings.badgeNew, description = strings.hintQaReport),
            NavItem(Screen.COVERAGE, Icons.Default.GridOn, strings.screenCoverageMap, strings.badgeNew, description = strings.hintCoverageMap),
            NavItem(Screen.BREAKING_CHANGE, Icons.AutoMirrored.Filled.CompareArrows, strings.screenChangeDetector, strings.badgeNew, description = strings.hintChangeDetector),
            NavItem(Screen.REGRESSION, Icons.Default.Timeline, strings.screenRegression, strings.badgeNew, description = strings.hintRegression),
            NavItem(Screen.DEPENDENCY_GRAPH, Icons.Default.Hub, strings.screenDependencies, strings.badgeNew, description = strings.hintDependencies)
        ))
    )

    // RTL-aware border on the end edge
    val borderColor = palette.border
    val borderWidthPx = AppDimensions.borderWidth

    Column(
        modifier = modifier
            .width(AppDimensions.sidebarWidth)
            .fillMaxHeight()
            .background(palette.sidebar)
            .drawBehind {
                val strokeWidth = borderWidthPx.toPx()
                val x = if (isRtl) 0f else size.width - strokeWidth
                drawLine(
                    color = borderColor,
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = strokeWidth
                )
            }
    ) {
        // Brand Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Orange accent dot
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(
                            Brush.radialGradient(
                                listOf(palette.accentHover, palette.accent)
                            )
                        )
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        strings.brandName,
                        style = typography.heading3.copy(
                            color = palette.textPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Text(
                        strings.brandSuffix,
                        style = typography.caption.copy(
                            color = palette.accent,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp
                        )
                    )
                }
            }
        }

        HorizontalDivider(color = palette.divider, thickness = 1.dp)

        // Scrollable nav sections
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            sections.forEach { section ->
                Spacer(Modifier.height(14.dp))

                // Section title with subtle line
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        section.title.uppercase(),
                        style = typography.label.copy(
                            color = palette.textTertiary,
                            letterSpacing = 1.sp,
                            fontSize = 10.sp
                        )
                    )
                    Spacer(Modifier.width(10.dp))
                    HorizontalDivider(
                        modifier = Modifier.weight(1f),
                        color = palette.divider,
                        thickness = 0.5.dp
                    )
                }

                Spacer(Modifier.height(4.dp))

                section.items.forEach { item ->
                    SidebarNavItem(
                        item = item,
                        isSelected = navigationState.currentScreen == item.screen,
                        isRtl = isRtl,
                        palette = palette,
                        typography = typography,
                        onClick = { navigationState.navigateTo(item.screen) }
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        HorizontalDivider(color = palette.divider, thickness = 1.dp)

        // Language Selector
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Text(
                strings.language,
                style = typography.label.copy(
                    color = palette.textTertiary,
                    letterSpacing = 0.5.sp
                ),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Language.entries.forEach { lang ->
                    val isSelected = navigationState.currentLanguage == lang
                    val langInteraction = remember { MutableInteractionSource() }
                    val langHovered by langInteraction.collectIsHoveredAsState()

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(AppDimensions.buttonRadius))
                            .background(
                                when {
                                    isSelected -> palette.accent.copy(alpha = 0.15f)
                                    langHovered -> palette.surfaceHover
                                    else -> Color.Transparent
                                }
                            )
                            .then(
                                if (isSelected) Modifier.border(
                                    1.dp,
                                    palette.accent.copy(alpha = 0.3f),
                                    RoundedCornerShape(AppDimensions.buttonRadius)
                                ) else Modifier
                            )
                            .hoverable(langInteraction)
                            .semantics {
                                role = Role.Button
                                selected = isSelected
                                contentDescription = lang.nativeName
                            }
                            .clickable { navigationState.setLanguage(lang) }
                            .padding(horizontal = 6.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            lang.code.uppercase(),
                            style = typography.caption.copy(
                                color = if (isSelected) palette.accent else palette.textSecondary,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 10.sp
                            ),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        HorizontalDivider(color = palette.divider, thickness = 1.dp)

        // Footer
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(StatusColors.Success)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                strings.version,
                style = typography.caption.copy(color = palette.textTertiary)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SidebarNavItem(
    item: NavItem,
    isSelected: Boolean,
    isRtl: Boolean,
    palette: AppPalette,
    typography: AppTextStyles,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val bgColor = when {
        isSelected -> palette.sidebarSelected
        isHovered -> palette.surfaceHover
        else -> Color.Transparent
    }

    TooltipArea(
        tooltip = {
            if (item.description.isNotEmpty()) {
                Surface(
                    modifier = Modifier.shadow(4.dp),
                    color = palette.surfaceElevated,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = item.description,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        style = typography.bodySmall.copy(color = palette.textPrimary)
                    )
                }
            }
        },
        delayMillis = 300,
        tooltipPlacement = TooltipPlacement.ComponentRect(
            anchor = Alignment.CenterEnd,
            alignment = Alignment.CenterStart,
            offset = androidx.compose.ui.unit.DpOffset(8.dp, 0.dp)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 1.dp)
                .clip(RoundedCornerShape(AppDimensions.buttonRadius))
                .background(bgColor)
                .hoverable(interactionSource)
                .semantics {
                    role = Role.Button
                    selected = isSelected
                    contentDescription = item.label
                }
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Selection indicator on the start edge (auto-flips with RTL Row)
            if (isSelected) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(18.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(palette.accent)
            )
            Spacer(Modifier.width(10.dp))
        }

        // Icon with subtle background when selected
        if (isSelected) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(palette.accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = null,
                    tint = palette.accent,
                    modifier = Modifier.size(16.dp)
                )
            }
        } else {
            Icon(
                imageVector = item.icon,
                contentDescription = null,
                tint = if (isHovered) palette.textPrimary else palette.textSecondary,
                modifier = Modifier.size(AppDimensions.iconSizeMd)
            )
        }

        Spacer(Modifier.width(10.dp))

        Text(
            item.label,
            style = typography.body.copy(
                color = if (isSelected) palette.textPrimary else palette.textSecondary,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                fontSize = 13.sp
            ),
            modifier = Modifier.weight(1f)
        )

        if (item.badge != null) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(AppDimensions.chipRadius))
                    .background(palette.accent.copy(alpha = 0.12f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    item.badge,
                    style = typography.caption.copy(
                        color = palette.accent,
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.sp
                    )
                )
            }
        }

        // Directional arrow indicator for selected item (auto-mirrored)
        if (isSelected) {
            Spacer(Modifier.width(4.dp))
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = palette.accent.copy(alpha = 0.7f),
                modifier = Modifier.size(14.dp)
            )
        }
    }
}
}
