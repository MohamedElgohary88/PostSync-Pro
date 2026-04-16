package com.postsyncpro.presentation.screen.mockserver

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.postsyncpro.presentation.theme.*
import com.postsyncpro.presentation.components.*
import com.postsyncpro.presentation.i18n.LocalAppStrings
import com.postsyncpro.domain.model.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun MockServerScreen(viewModel: MockServerViewModel) {
    val state by viewModel.state.collectAsState()
    val palette = LocalAppPalette.current
    val strings = LocalAppStrings.current

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
            .padding(AppDimensions.spacingMd),
        horizontalArrangement = Arrangement.spacedBy(AppDimensions.spacingMd)
    ) {
        // Left panel (25%) - Swagger file & Routes
        Column(
            modifier = Modifier
                .weight(0.25f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(AppDimensions.spacingMd)
        ) {
            // Swagger File Picker
            SectionCard(
                title = strings.apiSpecification,
                badge = null,
                icon = null
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(AppDimensions.spacingSm)
                ) {
                    FilePickerButton(
                        label = strings.loadSwaggerFile,
                        fileExtensions = listOf("json", "yaml", "yml"),
                        onFileSelected = { file -> viewModel.loadSwaggerFile(file) }
                    )

                    if (state.swaggerFileName.isNotEmpty()) {
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
                                text = state.swaggerFileName,
                                style = AppTypography.codeSmall,
                                color = palette.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // Routes List
            SectionCard(
                title = strings.registeredRoutes,
                badge = "${state.routes.size} ${strings.routes}",
                icon = null
            ) {
                if (state.routes.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(AppDimensions.spacingMd),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = strings.noRoutesLoaded,
                            style = AppTypography.bodyMedium,
                            color = palette.textTertiary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(state.routes) { route ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        palette.codeBg,
                                        RoundedCornerShape(AppDimensions.cornerSmall)
                                    )
                                    .padding(
                                        horizontal = AppDimensions.spacingXs,
                                        vertical = 4.dp
                                    ),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(AppDimensions.spacingXs)
                            ) {
                                MethodBadge(method = route.method)
                                Text(
                                    text = route.path,
                                    style = AppTypography.codeSmall,
                                    color = palette.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Center panel (45%) - Server Configuration
        Column(
            modifier = Modifier
                .weight(0.45f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(AppDimensions.spacingMd)
        ) {
            SectionCard(
                title = strings.serverConfiguration,
                badge = if (state.isRunning) strings.running else strings.stopped,
                icon = null
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(AppDimensions.spacingMd)
                ) {
                    // Port field
                    Column(
                        verticalArrangement = Arrangement.spacedBy(AppDimensions.spacingXs)
                    ) {
                        Text(
                            text = strings.port,
                            style = AppTypography.labelSmall,
                            color = palette.textSecondary
                        )
                        OutlinedTextField(
                            value = state.port.toString(),
                            onValueChange = { value ->
                                viewModel.updatePort(value)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = AppTypography.codeMedium.copy(color = palette.textPrimary),
                            singleLine = true,
                            enabled = !state.isRunning,
                            colors = TextFieldDefaults.outlinedTextFieldColors(
                                textColor = palette.textPrimary,
                                backgroundColor = palette.codeBg,
                                focusedBorderColor = BrandColors.Orange,
                                unfocusedBorderColor = palette.border,
                                cursorColor = BrandColors.Orange,
                                disabledTextColor = palette.textTertiary,
                                disabledBorderColor = palette.border.copy(alpha = 0.5f)
                            ),
                            shape = RoundedCornerShape(AppDimensions.cornerSmall)
                        )
                    }

                    Divider(color = palette.divider)

                    // Toggle switches
                    ToggleRow(
                        label = strings.requestValidation,
                        description = strings.validateRequestBodies,
                        checked = state.enableValidation,
                        onCheckedChange = { viewModel.toggleValidation() },
                        palette = palette
                    )

                    ToggleRow(
                        label = strings.logging,
                        description = strings.logAllRequests,
                        checked = state.enableLogging,
                        onCheckedChange = { viewModel.toggleLogging() },
                        palette = palette
                    )

                    Divider(color = palette.divider)

                    // START/STOP button
                    Button(
                        onClick = { viewModel.toggleServer() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = if (state.isRunning) StatusColors.Success else BrandColors.Orange,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(AppDimensions.cornerMedium)
                    ) {
                        Text(
                            text = if (state.isRunning) strings.stopServer else strings.startServer,
                            style = AppTypography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // Server URL display
                    AnimatedVisibility(visible = state.isRunning) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    StatusColors.Success.copy(alpha = 0.08f),
                                    RoundedCornerShape(AppDimensions.cornerSmall)
                                )
                                .border(
                                    1.dp,
                                    StatusColors.Success.copy(alpha = 0.3f),
                                    RoundedCornerShape(AppDimensions.cornerSmall)
                                )
                                .padding(AppDimensions.spacingSm),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(AppDimensions.spacingSm)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(StatusColors.Success)
                            )
                            Column {
                                Text(
                                    text = strings.serverRunningAt,
                                    style = AppTypography.labelSmall,
                                    color = palette.textTertiary
                                )
                                Text(
                                    text = "http://localhost:${state.port}",
                                    style = AppTypography.codeMedium,
                                    color = StatusColors.Success,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    Divider(color = palette.divider)

                    // Stats row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        StatCard(
                            label = strings.total,
                            value = state.totalRequests.toString(),
                            color = palette.textPrimary,
                            palette = palette,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(AppDimensions.spacingSm))
                        StatCard(
                            label = strings.success,
                            value = state.successRequests.toString(),
                            color = StatusColors.Success,
                            palette = palette,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(AppDimensions.spacingSm))
                        StatCard(
                            label = strings.failed,
                            value = state.failedRequests.toString(),
                            color = StatusColors.Error,
                            palette = palette,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Error display
            state.error?.let { error ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            StatusColors.Error.copy(alpha = 0.1f),
                            RoundedCornerShape(AppDimensions.cornerSmall)
                        )
                        .padding(AppDimensions.spacingSm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = error,
                        style = AppTypography.bodyMedium,
                        color = StatusColors.Error
                    )
                }
            }
        }

        // Right panel (30%) - Live Request Logs
        Column(
            modifier = Modifier
                .weight(0.30f)
                .fillMaxHeight()
        ) {
            SectionCard(
                title = strings.liveRequestLogs,
                badge = "${state.logs.size} ${strings.requests}",
                icon = null
            ) {
                if (state.logs.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight()
                            .padding(AppDimensions.spacingMd),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = strings.noRequestsYet,
                            style = AppTypography.bodyMedium,
                            color = palette.textTertiary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                } else {
                    val listState = rememberLazyListState()

                    LaunchedEffect(state.logs.size) {
                        if (state.logs.isNotEmpty()) {
                            listState.animateScrollToItem(state.logs.size - 1)
                        }
                    }

                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(state.logs) { log ->
                            LogEntry(log = log, palette = palette)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: () -> Unit,
    palette: AppPalette
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = AppTypography.bodyMedium,
                color = palette.textPrimary,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = description,
                style = AppTypography.labelSmall,
                color = palette.textTertiary
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = { onCheckedChange() },
            colors = SwitchDefaults.colors(
                checkedThumbColor = BrandColors.Orange,
                checkedTrackColor = BrandColors.Orange.copy(alpha = 0.4f),
                uncheckedThumbColor = palette.textTertiary,
                uncheckedTrackColor = palette.border
            )
        )
    }
}

@Composable
private fun StatCard(
    label: String,
    value: String,
    color: Color,
    palette: AppPalette,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(palette.codeBg, RoundedCornerShape(AppDimensions.cornerSmall))
            .padding(AppDimensions.spacingSm),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = AppTypography.titleMedium,
            color = color,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = AppTypography.labelSmall,
            color = palette.textTertiary
        )
    }
}

@Composable
private fun LogEntry(log: MockRequestLog, palette: AppPalette) {
    val strings = LocalAppStrings.current
    var expanded by remember { mutableStateOf(false) }
    val dateFormat = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .background(palette.surface, RoundedCornerShape(AppDimensions.cornerSmall))
            .border(1.dp, palette.border.copy(alpha = 0.5f), RoundedCornerShape(AppDimensions.cornerSmall))
            .padding(AppDimensions.spacingXs)
    ) {
        // Compact log header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppDimensions.spacingXs)
        ) {
            Text(
                text = dateFormat.format(Date(log.timestamp)),
                style = AppTypography.labelSmall,
                color = palette.textTertiary
            )
            MethodBadge(method = log.method)
            Text(
                text = log.path,
                style = AppTypography.codeSmall,
                color = palette.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            StatusCodeBadge(statusCode = log.responseStatus)
            Text(
                text = "${log.responseTimeMs}ms",
                style = AppTypography.labelSmall,
                color = palette.textTertiary
            )
        }

        // Validation errors
        if (log.validationErrors.isNotEmpty()) {
            Spacer(modifier = Modifier.height(2.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                StatusBadge(
                    text = "${log.validationErrors.size} validation error(s)",
                    type = BadgeType.ERROR
                )
            }
        }

        // Expanded details
        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = AppDimensions.spacingXs),
                verticalArrangement = Arrangement.spacedBy(AppDimensions.spacingXs)
            ) {
                Divider(color = palette.divider)

                // Request body
                log.requestBody?.let { body ->
                    Text(
                        text = strings.requestBody,
                        style = AppTypography.labelSmall,
                        color = palette.textTertiary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = body,
                        style = AppTypography.codeSmall,
                        color = palette.textSecondary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(palette.codeBg, RoundedCornerShape(4.dp))
                            .padding(AppDimensions.spacingXs),
                        maxLines = 10,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Response body
                Text(
                    text = strings.responseBody,
                    style = AppTypography.labelSmall,
                    color = palette.textTertiary,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = log.responseBody,
                    style = AppTypography.codeSmall,
                    color = palette.textSecondary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(palette.codeBg, RoundedCornerShape(4.dp))
                        .padding(AppDimensions.spacingXs),
                    maxLines = 10,
                    overflow = TextOverflow.Ellipsis
                )

                // Validation errors detail
                if (log.validationErrors.isNotEmpty()) {
                    Text(
                        text = strings.validationErrors,
                        style = AppTypography.labelSmall,
                        color = StatusColors.Error,
                        fontWeight = FontWeight.SemiBold
                    )
                    log.validationErrors.forEach { error ->
                        Text(
                            text = "- $error",
                            style = AppTypography.codeSmall,
                            color = StatusColors.Error.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MethodBadge(method: String) {
    val color = when (method.uppercase()) {
        "GET" -> HttpMethodColors.GET
        "POST" -> HttpMethodColors.POST
        "PUT" -> HttpMethodColors.PUT
        "DELETE" -> HttpMethodColors.DELETE
        "PATCH" -> HttpMethodColors.PATCH
        "HEAD" -> HttpMethodColors.HEAD
        "OPTIONS" -> HttpMethodColors.OPTIONS
        else -> HttpMethodColors.OPTIONS
    }

    Text(
        text = method.uppercase(),
        style = AppTypography.labelSmall,
        color = color,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

@Composable
private fun StatusCodeBadge(statusCode: Int) {
    val color = when {
        statusCode in 200..299 -> StatusColors.Success
        statusCode in 300..399 -> StatusColors.Warning
        statusCode in 400..499 -> StatusColors.Error
        statusCode >= 500 -> StatusColors.Error
        else -> HttpMethodColors.OPTIONS
    }

    Text(
        text = statusCode.toString(),
        style = AppTypography.labelSmall,
        color = color,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}
