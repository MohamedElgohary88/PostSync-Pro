package com.postsyncpro.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.postsyncpro.presentation.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.io.FilenameFilter

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun FilePickerButton(
    label: String,
    selectedFile: String? = null,
    fileExtensions: List<String> = emptyList(),
    onSelectionCleared: () -> Unit = {},
    onFileSelected: (File) -> Unit
) {
    val palette = LocalAppPalette.current
    val typography = LocalAppTypography.current
    val coroutineScope = rememberCoroutineScope()
    var isHovered by remember { mutableStateOf(false) }
    var internalSelectedFile by remember(selectedFile) { mutableStateOf(selectedFile) }

    val shape = RoundedCornerShape(AppDimensions.cardRadius)
    val dashedBorderColor = if (isHovered) palette.accent else palette.border
    val bgColor = if (isHovered) palette.accent.copy(alpha = 0.05f) else palette.inputBackground

    // Shared pick logic
    val pickFile: () -> Unit = {
        coroutineScope.launch {
            val file = withContext(Dispatchers.IO) {
                openNativeFileDialog(label, fileExtensions)
            }
            if (file != null) {
                internalSelectedFile = file.name
                onFileSelected(file)
            }
        }
    }

    if (internalSelectedFile == null) {
        // Drop-zone style: dashed border, upload icon, label
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(bgColor)
                .onPointerEvent(PointerEventType.Enter) { isHovered = true }
                .onPointerEvent(PointerEventType.Exit) { isHovered = false }
                .semantics {
                    role = Role.Button
                    contentDescription = label
                }
                .clickable { pickFile() }
                .dashedBorder(
                    color = dashedBorderColor,
                    cornerRadiusDp = 8f,
                    strokeWidthDp = 1.5f,
                    dashLengthDp = 8f,
                    gapLengthDp = 6f
                )
                .padding(vertical = 28.dp, horizontal = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.UploadFile,
                    contentDescription = null,
                    tint = if (isHovered) palette.accent else palette.textTertiary,
                    modifier = Modifier.size(32.dp)
                )
                Text(
                    text = label,
                    style = typography.body.copy(
                        color = if (isHovered) palette.accent else palette.textSecondary
                    )
                )
                if (fileExtensions.isNotEmpty()) {
                    Text(
                        text = fileExtensions.joinToString(", ") { ".$it" },
                        style = typography.caption.copy(color = palette.textTertiary)
                    )
                }
            }
        }
    } else {
        // File selected state: show filename with icon
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(palette.surfaceVariant)
                .border(AppDimensions.borderWidth, palette.border, shape)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Description,
                contentDescription = null,
                tint = palette.accent,
                modifier = Modifier.size(AppDimensions.iconSizeMd)
            )
            Text(
                text = internalSelectedFile ?: "",
                style = typography.code.copy(color = palette.textPrimary),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            // Change file button
            Text(
                text = "Change",
                style = typography.label.copy(color = palette.accent),
                modifier = Modifier
                    .clip(RoundedCornerShape(AppDimensions.buttonRadius))
                    .semantics {
                        role = Role.Button
                        contentDescription = "Change $label"
                    }
                    .clickable { pickFile() }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
            // Clear button
            IconButton(
                onClick = {
                    internalSelectedFile = null
                    onSelectionCleared()
                },
                modifier = Modifier.size(20.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Clear selection",
                    tint = palette.textTertiary,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

/**
 * Draws a dashed rounded-rect border using drawBehind.
 */
private fun Modifier.dashedBorder(
    color: Color,
    cornerRadiusDp: Float,
    strokeWidthDp: Float,
    dashLengthDp: Float,
    gapLengthDp: Float
): Modifier = this.drawBehind {
    val strokeWidthPx = strokeWidthDp * density
    val dashLengthPx = dashLengthDp * density
    val gapLengthPx = gapLengthDp * density
    val cornerRadiusPx = cornerRadiusDp * density

    drawRoundRect(
        color = color,
        topLeft = Offset(strokeWidthPx / 2, strokeWidthPx / 2),
        size = Size(size.width - strokeWidthPx, size.height - strokeWidthPx),
        cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx),
        style = Stroke(
            width = strokeWidthPx,
            pathEffect = PathEffect.dashPathEffect(
                floatArrayOf(dashLengthPx, gapLengthPx),
                0f
            )
        )
    )
}

/**
 * Uses the native macOS/Windows/Linux file dialog (java.awt.FileDialog)
 * instead of Swing's JFileChooser. This avoids EDT deadlock issues on macOS.
 * MUST be called from a background thread (Dispatchers.IO).
 */
private fun openNativeFileDialog(title: String, extensions: List<String>): File? {
    val dialog = FileDialog(null as Frame?, title, FileDialog.LOAD)
    if (extensions.isNotEmpty()) {
        dialog.filenameFilter = FilenameFilter { _, name ->
            extensions.any { ext -> name.endsWith(".$ext", ignoreCase = true) }
        }
        // macOS uses setFile for filter hint
        dialog.file = extensions.joinToString(";") { "*.$it" }
    }
    dialog.isVisible = true
    val dir = dialog.directory
    val file = dialog.file
    dialog.dispose()
    return if (dir != null && file != null) File(dir, file) else null
}

/**
 * Opens a native directory chooser. MUST be called from a background thread.
 */
fun openNativeDirectoryDialog(title: String): File? {
    System.setProperty("apple.awt.fileDialogForDirectories", "true")
    val dialog = FileDialog(null as Frame?, title, FileDialog.LOAD)
    dialog.isVisible = true
    val dir = dialog.directory
    val file = dialog.file
    dialog.dispose()
    System.setProperty("apple.awt.fileDialogForDirectories", "false")
    return if (dir != null && file != null) File(dir, file) else if (dir != null) File(dir) else null
}
