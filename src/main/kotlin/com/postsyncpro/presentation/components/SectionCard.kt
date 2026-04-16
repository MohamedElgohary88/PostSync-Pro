package com.postsyncpro.presentation.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.postsyncpro.presentation.theme.*

@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    accentColor: Color = BrandColors.Orange,
    badge: String? = null,
    collapsible: Boolean = false,
    initiallyExpanded: Boolean = true,
    headerAction: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val palette = LocalAppPalette.current
    val typography = LocalAppTypography.current
    var isExpanded by remember { mutableStateOf(initiallyExpanded) }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(AppDimensions.cardRadius))
            .background(palette.surface)
            .border(
                width = AppDimensions.borderWidth,
                color = palette.border,
                shape = RoundedCornerShape(AppDimensions.cardRadius)
            )
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (collapsible) Modifier.clickable { isExpanded = !isExpanded } else Modifier)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Accent bar
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(18.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(accentColor)
            )
            Spacer(Modifier.width(12.dp))

            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(AppDimensions.iconSizeMd)
                )
                Spacer(Modifier.width(8.dp))
            }

            Text(
                title,
                style = typography.heading3.copy(color = palette.textPrimary)
            )

            if (badge != null) {
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(AppDimensions.chipRadius))
                        .background(accentColor.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        badge,
                        style = typography.caption.copy(color = accentColor)
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            headerAction?.invoke()

            if (collapsible) {
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = palette.textTertiary,
                    modifier = Modifier.size(AppDimensions.iconSizeMd)
                )
            }
        }

        // Content
        AnimatedVisibility(
            visible = isExpanded || !collapsible,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column {
                HorizontalDivider(color = palette.divider, thickness = 1.dp)
                Column(
                    modifier = Modifier.padding(16.dp),
                    content = content
                )
            }
        }
    }
}
