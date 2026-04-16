package com.postsyncpro.presentation.components

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.postsyncpro.domain.model.LogLevel
import com.postsyncpro.domain.model.MergeLog
import com.postsyncpro.presentation.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun LogConsole(
    logs: List<MergeLog>,
    modifier: Modifier = Modifier,
    onClear: (() -> Unit)? = null
) {
    val palette = LocalAppPalette.current
    val typography = LocalAppTypography.current
    val listState = rememberLazyListState()
    val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    // Auto-scroll to bottom when new logs are added
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(AppDimensions.cardRadius))
            .background(palette.codeBg)
            .border(AppDimensions.borderWidth, palette.border, RoundedCornerShape(AppDimensions.cardRadius))
    ) {
        // Terminal header bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(palette.surfaceVariant)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Terminal dots
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(Color(0xFFFF5F57)))
                Box(Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(Color(0xFFFFBD2E)))
                Box(Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(Color(0xFF28C840)))
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = "Console",
                style = typography.label.copy(color = palette.textTertiary)
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "${logs.size} entries",
                style = typography.caption.copy(color = palette.textTertiary)
            )
            if (onClear != null && logs.isNotEmpty()) {
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = onClear,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Clear logs",
                        tint = palette.textTertiary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }

        HorizontalDivider(color = palette.border, thickness = 1.dp)

        // Log content
        if (logs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 80.dp)
                    .padding(AppDimensions.spacingLg),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No log entries yet.",
                    style = typography.codeSmall.copy(color = palette.textTertiary)
                )
            }
        } else {
            Box(modifier = Modifier.fillMaxWidth()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    itemsIndexed(logs) { index, log ->
                        LogEntryRow(
                            index = index + 1,
                            log = log,
                            timeFormat = timeFormat,
                            palette = palette
                        )
                    }
                }

                // Custom scrollbar
                VerticalScrollbar(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .padding(end = 2.dp, top = 2.dp, bottom = 2.dp),
                    adapter = rememberScrollbarAdapter(listState)
                )
            }
        }
    }
}

@Composable
private fun LogEntryRow(
    index: Int,
    log: MergeLog,
    timeFormat: SimpleDateFormat,
    palette: AppPalette
) {
    val (levelColor, levelTag) = when (log.level) {
        LogLevel.ERROR -> StatusColors.Error to "ERR"
        LogLevel.WARNING -> StatusColors.Warning to "WRN"
        LogLevel.SUCCESS -> StatusColors.Success to " OK"
        LogLevel.INFO -> StatusColors.Info to "INF"
    }

    val timestamp = timeFormat.format(Date(log.timestamp))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(0.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Line number
        Text(
            text = index.toString().padStart(4),
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            color = palette.textTertiary.copy(alpha = 0.5f),
            modifier = Modifier.width(36.dp)
        )

        // Timestamp
        Text(
            text = timestamp,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            color = palette.textTertiary,
            modifier = Modifier.padding(end = 8.dp)
        )

        // Level badge
        Text(
            text = levelTag,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = levelColor,
            modifier = Modifier
                .width(32.dp)
                .padding(end = 8.dp)
        )

        // Message
        Text(
            text = log.message,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            color = levelColor.copy(alpha = 0.85f),
            lineHeight = 16.sp
        )
    }
}
