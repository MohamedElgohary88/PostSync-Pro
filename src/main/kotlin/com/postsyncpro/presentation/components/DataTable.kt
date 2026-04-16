package com.postsyncpro.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.postsyncpro.presentation.theme.*

data class TableColumn<T>(
    val header: String,
    val weight: Float = 1f,
    val sortable: Boolean = false,
    val content: @Composable (T) -> Unit
)

enum class SortDirection { ASC, DESC, NONE }

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun <T> DataTable(
    columns: List<TableColumn<T>>,
    rows: List<T>,
    modifier: Modifier = Modifier,
    onRowClick: ((T) -> Unit)? = null
) {
    val palette = LocalAppPalette.current
    val typography = LocalAppTypography.current
    var sortColumnIndex by remember { mutableStateOf(-1) }
    var sortDirection by remember { mutableStateOf(SortDirection.NONE) }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(AppDimensions.cardRadius))
            .background(palette.surface)
    ) {
        // Header row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(palette.surfaceVariant)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            columns.forEachIndexed { index, column ->
                Row(
                    modifier = Modifier
                        .weight(column.weight)
                        .then(
                            if (column.sortable) {
                                Modifier.clickable {
                                    if (sortColumnIndex == index) {
                                        sortDirection = when (sortDirection) {
                                            SortDirection.NONE -> SortDirection.ASC
                                            SortDirection.ASC -> SortDirection.DESC
                                            SortDirection.DESC -> SortDirection.NONE
                                        }
                                    } else {
                                        sortColumnIndex = index
                                        sortDirection = SortDirection.ASC
                                    }
                                }
                            } else Modifier
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = column.header,
                        style = typography.label.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = palette.textSecondary
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (column.sortable) {
                        val icon = when {
                            sortColumnIndex == index && sortDirection == SortDirection.ASC -> Icons.Default.ArrowUpward
                            sortColumnIndex == index && sortDirection == SortDirection.DESC -> Icons.Default.ArrowDownward
                            else -> Icons.Default.UnfoldMore
                        }
                        val tint = if (sortColumnIndex == index && sortDirection != SortDirection.NONE) {
                            BrandColors.Orange
                        } else {
                            palette.textTertiary
                        }
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = tint,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }

        HorizontalDivider(color = palette.border, thickness = 1.dp)

        // Data rows
        if (rows.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(AppDimensions.spacingXl),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No data available",
                    style = typography.body.copy(color = palette.textTertiary)
                )
            }
        } else {
            LazyColumn {
                itemsIndexed(rows) { index, item ->
                    var isHovered by remember { mutableStateOf(false) }

                    val rowBg = when {
                        isHovered -> palette.surfaceHover
                        index % 2 == 0 -> palette.surface
                        else -> palette.surfaceVariant.copy(alpha = 0.3f)
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(rowBg)
                            .onPointerEvent(PointerEventType.Enter) { isHovered = true }
                            .onPointerEvent(PointerEventType.Exit) { isHovered = false }
                            .then(
                                if (onRowClick != null) {
                                    Modifier.clickable { onRowClick(item) }
                                } else Modifier
                            )
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        columns.forEach { column ->
                            Box(modifier = Modifier.weight(column.weight)) {
                                column.content(item)
                            }
                        }
                    }

                    if (index < rows.size - 1) {
                        HorizontalDivider(
                            color = palette.divider.copy(alpha = 0.5f),
                            thickness = 0.5.dp
                        )
                    }
                }
            }
        }
    }
}
