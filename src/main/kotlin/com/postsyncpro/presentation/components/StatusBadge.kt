package com.postsyncpro.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.postsyncpro.presentation.theme.*

enum class BadgeType { SUCCESS, ERROR, WARNING, INFO, NEUTRAL }

@Composable
fun StatusBadge(
    text: String,
    type: BadgeType,
    modifier: Modifier = Modifier
) {
    val palette = LocalAppPalette.current
    val (bgColor, textColor) = when (type) {
        BadgeType.SUCCESS -> StatusColors.Success.copy(alpha = 0.15f) to StatusColors.Success
        BadgeType.ERROR -> StatusColors.Error.copy(alpha = 0.15f) to StatusColors.Error
        BadgeType.WARNING -> StatusColors.Warning.copy(alpha = 0.15f) to StatusColors.Warning
        BadgeType.INFO -> StatusColors.Info.copy(alpha = 0.15f) to StatusColors.Info
        BadgeType.NEUTRAL -> palette.surfaceVariant to palette.textSecondary
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(AppDimensions.chipRadius))
            .background(bgColor)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = textColor,
            letterSpacing = 0.3.sp
        )
    }
}

@Composable
fun HttpMethodBadge(
    method: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val color = HttpMethodColors.forMethod(method)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(AppDimensions.chipRadius))
            .background(color.copy(alpha = 0.15f))
            .border(
                width = 1.dp,
                color = color.copy(alpha = 0.3f),
                shape = RoundedCornerShape(AppDimensions.chipRadius)
            )
            .padding(horizontal = if (compact) 6.dp else 10.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = method.uppercase(),
            fontSize = if (compact) 9.sp else 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = color,
            letterSpacing = 0.5.sp
        )
    }
}

@Composable
fun HttpStatusBadge(
    statusCode: Int,
    modifier: Modifier = Modifier
) {
    val (bgColor, textColor) = when {
        statusCode in 200..299 -> StatusColors.Success.copy(alpha = 0.15f) to StatusColors.Success
        statusCode in 300..399 -> StatusColors.Info.copy(alpha = 0.15f) to StatusColors.Info
        statusCode in 400..499 -> StatusColors.Warning.copy(alpha = 0.15f) to StatusColors.Warning
        statusCode >= 500 -> StatusColors.Error.copy(alpha = 0.15f) to StatusColors.Error
        else -> Color(0xFF424242) to Color(0xFF9E9E9E)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(AppDimensions.chipRadius))
            .background(bgColor)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = statusCode.toString(),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = textColor
        )
    }
}

@Composable
fun ScoreBadge(
    score: Float,
    modifier: Modifier = Modifier
) {
    val percentage = (score * 100).toInt()
    val (bgColor, textColor) = when {
        percentage >= 80 -> StatusColors.Success.copy(alpha = 0.15f) to StatusColors.Success
        percentage >= 60 -> StatusColors.Warning.copy(alpha = 0.15f) to StatusColors.Warning
        else -> StatusColors.Error.copy(alpha = 0.15f) to StatusColors.Error
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(AppDimensions.chipRadius))
            .background(bgColor)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "$percentage%",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = textColor
        )
    }
}
