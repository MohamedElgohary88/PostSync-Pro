package com.postsyncpro.presentation.components

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.postsyncpro.presentation.theme.*
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

// Syntax highlighting colors (Postman-inspired)
private val JsonKeyColor = Color(0xFFFF6C37)       // Orange for keys
private val JsonStringColor = Color(0xFF49CC90)     // Green for strings
private val JsonNumberColor = Color(0xFF61AFFE)     // Blue for numbers
private val JsonBooleanColor = Color(0xFFAB47BC)    // Purple for booleans
private val JsonNullColor = Color(0xFFF93E3E)       // Red for null
private val JsonBracketColor = Color(0xFF9E9E9E)    // Gray for brackets
private val JsonCommaColor = Color(0xFF6B6B6B)      // Dim gray for commas/colons

@Composable
fun JsonViewer(
    json: String,
    modifier: Modifier = Modifier,
    onEdit: ((String) -> Unit)? = null
) {
    val palette = LocalAppPalette.current
    val typography = LocalAppTypography.current
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(AppDimensions.cardRadius))
            .background(palette.codeBg)
            .border(AppDimensions.borderWidth, palette.border, RoundedCornerShape(AppDimensions.cardRadius))
    ) {
        // Header bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(palette.surfaceVariant)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "JSON",
                style = typography.label.copy(color = palette.textTertiary)
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "${json.lines().size} lines",
                style = typography.caption.copy(color = palette.textTertiary)
            )
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = {
                    val clipboard = Toolkit.getDefaultToolkit().systemClipboard
                    clipboard.setContents(StringSelection(json), null)
                },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copy JSON",
                    tint = palette.textTertiary,
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        HorizontalDivider(color = palette.border, thickness = 1.dp)

        // JSON content with line numbers
        Box(modifier = Modifier.fillMaxWidth()) {
            SelectionContainer {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(vertical = 8.dp)
                ) {
                    // Line numbers gutter
                    val lines = json.lines()
                    Column(
                        modifier = Modifier
                            .background(palette.codeBg)
                            .padding(horizontal = 12.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                        lines.forEachIndexed { index, _ ->
                            Text(
                                text = "${index + 1}",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                lineHeight = 20.sp,
                                color = palette.textTertiary.copy(alpha = 0.4f)
                            )
                        }
                    }

                    // Divider between line numbers and code
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(IntrinsicSize.Max)
                            .background(palette.border.copy(alpha = 0.3f))
                    )

                    // Highlighted JSON content
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 12.dp)
                    ) {
                        lines.forEach { line ->
                            Text(
                                text = highlightJsonLine(line, palette),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                lineHeight = 20.sp
                            )
                        }
                    }
                }
            }

            // Custom scrollbar
            VerticalScrollbar(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .padding(end = 2.dp, top = 2.dp, bottom = 2.dp),
                adapter = rememberScrollbarAdapter(scrollState),
                style = ScrollbarStyle(
                    minimalHeight = 24.dp,
                    thickness = 6.dp,
                    shape = RoundedCornerShape(3.dp),
                    hoverDurationMillis = 200,
                    unhoverColor = palette.scrollbar.copy(alpha = 0.3f),
                    hoverColor = palette.scrollbar.copy(alpha = 0.6f)
                )
            )
        }
    }
}

private fun highlightJsonLine(line: String, palette: AppPalette): AnnotatedString {
    return buildAnnotatedString {
        var i = 0
        val defaultColor = palette.textPrimary

        while (i < line.length) {
            when {
                // JSON key (quoted string followed by colon)
                line[i] == '"' && isJsonKey(line, i) -> {
                    val end = line.indexOf('"', i + 1)
                    if (end != -1) {
                        withStyle(SpanStyle(color = JsonKeyColor, fontWeight = FontWeight.Normal)) {
                            append(line.substring(i, end + 1))
                        }
                        i = end + 1
                    } else {
                        withStyle(SpanStyle(color = JsonKeyColor)) { append(line[i]) }
                        i++
                    }
                }

                // String value
                line[i] == '"' -> {
                    val end = findClosingQuote(line, i + 1)
                    withStyle(SpanStyle(color = JsonStringColor)) {
                        append(line.substring(i, end + 1))
                    }
                    i = end + 1
                }

                // Numbers
                line[i].isDigit() || (line[i] == '-' && i + 1 < line.length && line[i + 1].isDigit()) -> {
                    val start = i
                    if (line[i] == '-') i++
                    while (i < line.length && (line[i].isDigit() || line[i] == '.' || line[i] == 'e' || line[i] == 'E' || line[i] == '+' || line[i] == '-')) {
                        i++
                    }
                    withStyle(SpanStyle(color = JsonNumberColor)) {
                        append(line.substring(start, i))
                    }
                }

                // Boolean true
                line.startsWith("true", i) && (i + 4 >= line.length || !line[i + 4].isLetterOrDigit()) -> {
                    withStyle(SpanStyle(color = JsonBooleanColor, fontWeight = FontWeight.SemiBold)) {
                        append("true")
                    }
                    i += 4
                }

                // Boolean false
                line.startsWith("false", i) && (i + 5 >= line.length || !line[i + 5].isLetterOrDigit()) -> {
                    withStyle(SpanStyle(color = JsonBooleanColor, fontWeight = FontWeight.SemiBold)) {
                        append("false")
                    }
                    i += 5
                }

                // Null
                line.startsWith("null", i) && (i + 4 >= line.length || !line[i + 4].isLetterOrDigit()) -> {
                    withStyle(SpanStyle(color = JsonNullColor, fontWeight = FontWeight.SemiBold)) {
                        append("null")
                    }
                    i += 4
                }

                // Brackets and braces
                line[i] in "{}[]" -> {
                    withStyle(SpanStyle(color = JsonBracketColor)) { append(line[i]) }
                    i++
                }

                // Comma and colon
                line[i] == ',' || line[i] == ':' -> {
                    withStyle(SpanStyle(color = JsonCommaColor)) { append(line[i]) }
                    i++
                }

                // Whitespace and other
                else -> {
                    append(line[i])
                    i++
                }
            }
        }
    }
}

private fun isJsonKey(line: String, quoteIndex: Int): Boolean {
    val closingQuote = line.indexOf('"', quoteIndex + 1)
    if (closingQuote == -1) return false
    val afterQuote = line.substring(closingQuote + 1).trimStart()
    return afterQuote.startsWith(":")
}

private fun findClosingQuote(line: String, startIndex: Int): Int {
    var i = startIndex
    while (i < line.length) {
        if (line[i] == '"' && (i == startIndex || line[i - 1] != '\\')) {
            return i
        }
        i++
    }
    return line.length - 1
}
