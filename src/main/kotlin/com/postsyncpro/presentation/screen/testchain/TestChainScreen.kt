package com.postsyncpro.presentation.screen.testchain

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.postsyncpro.presentation.theme.*
import com.postsyncpro.presentation.components.*
import com.postsyncpro.presentation.i18n.LocalAppStrings
import com.postsyncpro.domain.model.*

private fun methodColor(method: String): Color = when (method.uppercase()) {
    "GET" -> HttpMethodColors.GET
    "POST" -> HttpMethodColors.POST
    "PUT" -> HttpMethodColors.PUT
    "DELETE" -> HttpMethodColors.DELETE
    "PATCH" -> HttpMethodColors.PATCH
    else -> BrandColors.Purple
}

@Composable
fun TestChainScreen(viewModel: TestChainViewModel) {
    val state by viewModel.state.collectAsState()
    val palette = LocalAppPalette.current
    val strings = LocalAppStrings.current

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
    ) {
        // Left Panel - Endpoint List (20%)
        LeftPanel(
            state = state,
            palette = palette,
            onFileSelected = { viewModel.loadSwaggerFile(it) },
            onBaseUrlChange = { viewModel.updateBaseUrl(it) },
            onAddNode = { viewModel.addNode(it) },
            modifier = Modifier.fillMaxHeight().weight(0.20f)
        )

        Divider(
            modifier = Modifier.fillMaxHeight().width(1.dp),
            color = palette.border
        )

        // Center Panel - Canvas (55%)
        CanvasPanel(
            state = state,
            palette = palette,
            onNodeClick = { viewModel.selectNode(it) },
            modifier = Modifier.fillMaxHeight().weight(0.55f)
        )

        Divider(
            modifier = Modifier.fillMaxHeight().width(1.dp),
            color = palette.border
        )

        // Right Panel - Node Details (25%)
        RightPanel(
            state = state,
            palette = palette,
            onExecuteChain = { viewModel.executeChain() },
            onClearChain = { viewModel.clearChain() },
            onRemoveNode = { viewModel.removeNode(it) },
            onBodyUpdate = { nodeId, body -> viewModel.updateNodeBody(nodeId, body) },
            onAddExtractor = { nodeId, varName, jsonPath -> viewModel.addExtractor(nodeId, varName, jsonPath) },
            onStartConnecting = { viewModel.startConnecting(it) },
            onFinishConnecting = { viewModel.finishConnecting(it) },
            modifier = Modifier.fillMaxHeight().weight(0.25f)
        )
    }
}

@Composable
private fun LeftPanel(
    state: TestChainState,
    palette: AppPalette,
    onFileSelected: (java.io.File) -> Unit,
    onBaseUrlChange: (String) -> Unit,
    onAddNode: (EndpointInfo) -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current

    Column(
        modifier = modifier
            .background(palette.surface)
            .padding(16.dp)
    ) {
        Text(
            text = strings.testChainBuilder,
            style = AppTypography.titleMedium,
            color = palette.textPrimary
        )

        Spacer(Modifier.height(16.dp))

        // Swagger File Picker
        SectionCard(title = strings.swaggerFile, badge = null, icon = null) {
            FilePickerButton(
                label = if (state.swaggerFileName.isNotEmpty()) state.swaggerFileName else strings.selectSwaggerFile,
                fileExtensions = listOf("json", "yaml", "yml"),
                onFileSelected = onFileSelected
            )
        }

        Spacer(Modifier.height(12.dp))

        // Base URL
        SectionCard(title = strings.baseUrl, badge = null, icon = null) {
            OutlinedTextField(
                value = state.baseUrl,
                onValueChange = onBaseUrlChange,
                placeholder = { Text(strings.baseUrlPlaceholder, color = palette.textTertiary) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = AppTypography.bodyMedium.copy(color = palette.textPrimary),
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    focusedBorderColor = BrandColors.Orange,
                    unfocusedBorderColor = palette.border,
                    cursorColor = BrandColors.Orange
                )
            )
        }

        Spacer(Modifier.height(12.dp))

        // Endpoint List
        Text(
            text = strings.endpoints,
            style = AppTypography.titleMedium,
            color = palette.textPrimary
        )

        if (state.endpoints.isEmpty()) {
            Text(
                text = strings.loadSwaggerToSeeEndpoints,
                style = AppTypography.bodyMedium,
                color = palette.textTertiary,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        Spacer(Modifier.height(8.dp))

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(state.endpoints) { endpoint ->
                EndpointListItem(
                    endpoint = endpoint,
                    palette = palette,
                    onClick = { onAddNode(endpoint) }
                )
                Spacer(Modifier.height(4.dp))
            }
        }

        state.error?.let { error ->
            Spacer(Modifier.height(8.dp))
            Text(
                text = error,
                style = AppTypography.labelSmall,
                color = StatusColors.Error
            )
        }
    }
}

@Composable
private fun EndpointListItem(
    endpoint: EndpointInfo,
    palette: AppPalette,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .background(palette.codeBg)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(methodColor(endpoint.method))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                text = endpoint.method.uppercase(),
                style = AppTypography.labelSmall,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = endpoint.path,
            style = AppTypography.codeSmall,
            color = palette.textPrimary,
            maxLines = 1
        )
    }
}

@Composable
private fun CanvasPanel(
    state: TestChainState,
    palette: AppPalette,
    onNodeClick: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val nodeWidth = 200f
    val nodeHeight = 60f

    val strings = LocalAppStrings.current

    Box(modifier = modifier.background(palette.background)) {
        if (state.nodes.isEmpty()) {
            // Empty state
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.AccountTree,
                    contentDescription = null,
                    tint = palette.textTertiary,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = strings.clickEndpointsToAdd,
                    style = AppTypography.bodyMedium,
                    color = palette.textTertiary
                )
            }
        } else {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(state.nodes) {
                        detectTapGestures { offset ->
                            val clickedNode = state.nodes.find { node ->
                                val rect = Rect(
                                    left = node.posX,
                                    top = node.posY,
                                    right = node.posX + nodeWidth,
                                    bottom = node.posY + nodeHeight
                                )
                                rect.contains(offset)
                            }
                            onNodeClick(clickedNode?.id)
                        }
                    }
            ) {
                // Draw connections as Bezier curves
                state.connections.forEach { connection ->
                    val fromNode = state.nodes.find { it.id == connection.fromNodeId }
                    val toNode = state.nodes.find { it.id == connection.toNodeId }
                    if (fromNode != null && toNode != null) {
                        drawConnectionArrow(
                            from = Offset(fromNode.posX + nodeWidth, fromNode.posY + nodeHeight / 2),
                            to = Offset(toNode.posX, toNode.posY + nodeHeight / 2),
                            color = palette.textTertiary
                        )
                    }
                }

                // Draw nodes
                state.nodes.forEachIndexed { index, node ->
                    val result = state.executionState.nodeResults[node.id]
                    val isCurrentlyRunning = state.executionState.isRunning &&
                            state.executionState.currentNodeIndex == index
                    val isSelected = state.selectedNodeId == node.id

                    val borderColor = when {
                        isCurrentlyRunning -> BrandColors.Orange
                        result?.passed == true -> StatusColors.Success
                        result?.passed == false -> StatusColors.Error
                        isSelected -> BrandColors.Orange
                        else -> palette.border
                    }

                    val borderWidth = if (isSelected || isCurrentlyRunning) 3f else 1.5f

                    // Node body
                    drawRoundRect(
                        color = palette.surface,
                        topLeft = Offset(node.posX, node.posY),
                        size = androidx.compose.ui.geometry.Size(nodeWidth, nodeHeight),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f)
                    )

                    // Node border
                    drawRoundRect(
                        color = borderColor,
                        topLeft = Offset(node.posX, node.posY),
                        size = androidx.compose.ui.geometry.Size(nodeWidth, nodeHeight),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f),
                        style = Stroke(width = borderWidth)
                    )

                    // Method color header bar
                    drawRoundRect(
                        color = methodColor(node.method),
                        topLeft = Offset(node.posX, node.posY),
                        size = androidx.compose.ui.geometry.Size(nodeWidth, 22f),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f)
                    )
                    // Fix bottom corners of header
                    drawRect(
                        color = methodColor(node.method),
                        topLeft = Offset(node.posX, node.posY + 14f),
                        size = androidx.compose.ui.geometry.Size(nodeWidth, 8f)
                    )

                    // Node index
                    val indexText = buildAnnotatedString {
                        withStyle(SpanStyle(color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)) {
                            append("#${index + 1}")
                        }
                    }
                    drawText(
                        textMeasurer = textMeasurer,
                        text = indexText,
                        topLeft = Offset(node.posX + 6f, node.posY + 3f)
                    )

                    // Method text on header
                    val methodText = buildAnnotatedString {
                        withStyle(SpanStyle(color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)) {
                            append(node.method.uppercase())
                        }
                    }
                    drawText(
                        textMeasurer = textMeasurer,
                        text = methodText,
                        topLeft = Offset(node.posX + 32f, node.posY + 3f)
                    )

                    // Path text
                    val pathText = buildAnnotatedString {
                        withStyle(SpanStyle(color = palette.textPrimary.toArgb().let { Color(it) }, fontSize = 11.sp)) {
                            append(node.path.take(28))
                        }
                    }
                    drawText(
                        textMeasurer = textMeasurer,
                        text = pathText,
                        topLeft = Offset(node.posX + 8f, node.posY + 30f)
                    )

                    // Status indicator
                    if (result != null) {
                        val statusText = buildAnnotatedString {
                            val statusColor = if (result.passed) StatusColors.Success else StatusColors.Error
                            withStyle(SpanStyle(color = statusColor, fontSize = 9.sp)) {
                                append("${result.httpStatus} - ${result.responseTimeMs}ms")
                            }
                        }
                        drawText(
                            textMeasurer = textMeasurer,
                            text = statusText,
                            topLeft = Offset(node.posX + 8f, node.posY + 46f)
                        )
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawConnectionArrow(from: Offset, to: Offset, color: Color) {
    val controlPointOffset = (to.x - from.x) * 0.5f
    val path = Path().apply {
        moveTo(from.x, from.y)
        cubicTo(
            from.x + controlPointOffset, from.y,
            to.x - controlPointOffset, to.y,
            to.x, to.y
        )
    }
    drawPath(
        path = path,
        color = color,
        style = Stroke(width = 2f)
    )

    // Arrowhead
    val arrowSize = 8f
    val arrowPath = Path().apply {
        moveTo(to.x, to.y)
        lineTo(to.x - arrowSize, to.y - arrowSize / 2)
        lineTo(to.x - arrowSize, to.y + arrowSize / 2)
        close()
    }
    drawPath(path = arrowPath, color = color)
}

@Composable
private fun RightPanel(
    state: TestChainState,
    palette: AppPalette,
    onExecuteChain: () -> Unit,
    onClearChain: () -> Unit,
    onRemoveNode: (String) -> Unit,
    onBodyUpdate: (String, String) -> Unit,
    onAddExtractor: (String, String, String) -> Unit,
    onStartConnecting: (String) -> Unit,
    onFinishConnecting: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    val scrollState = rememberScrollState()
    val selectedNode = state.nodes.find { it.id == state.selectedNodeId }

    Column(
        modifier = modifier
            .background(palette.surface)
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        Text(
            text = strings.nodeDetails,
            style = AppTypography.titleMedium,
            color = palette.textPrimary
        )

        Spacer(Modifier.height(12.dp))

        if (selectedNode != null) {
            // Node Info
            SectionCard(title = "${selectedNode.method} ${selectedNode.path}", badge = null, icon = null) {
                Text(
                    text = "${strings.label}${selectedNode.label}",
                    style = AppTypography.bodyMedium,
                    color = palette.textSecondary
                )
                Text(
                    text = "${strings.id}${selectedNode.id}",
                    style = AppTypography.labelSmall,
                    color = palette.textTertiary
                )
            }

            Spacer(Modifier.height(12.dp))

            // Body Template Editor
            SectionCard(title = strings.bodyTemplate, badge = null, icon = null) {
                var bodyText by remember(selectedNode.id) { mutableStateOf(selectedNode.bodyTemplate) }
                OutlinedTextField(
                    value = bodyText,
                    onValueChange = {
                        bodyText = it
                        onBodyUpdate(selectedNode.id, it)
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
                    textStyle = AppTypography.codeMedium.copy(color = palette.textPrimary),
                    placeholder = { Text(strings.bodyTemplatePlaceholder, style = AppTypography.codeSmall, color = palette.textTertiary) },
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        focusedBorderColor = BrandColors.Orange,
                        unfocusedBorderColor = palette.border,
                        backgroundColor = palette.codeBg,
                        cursorColor = BrandColors.Orange
                    )
                )
            }

            Spacer(Modifier.height(12.dp))

            // Headers Editor
            SectionCard(title = strings.headers, badge = "${selectedNode.headers.size}", icon = null) {
                selectedNode.headers.forEach { (key, value) ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "$key:",
                            style = AppTypography.codeSmall,
                            color = BrandColors.Orange,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = value,
                            style = AppTypography.codeSmall,
                            color = palette.textSecondary,
                            maxLines = 1
                        )
                    }
                }
                if (selectedNode.headers.isEmpty()) {
                    Text(
                        text = strings.noCustomHeaders,
                        style = AppTypography.labelSmall,
                        color = palette.textTertiary
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Extractors
            SectionCard(title = strings.responseExtractors, badge = "${selectedNode.extractors.size}", icon = null) {
                selectedNode.extractors.forEach { extractor ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "\$${extractor.variableName}",
                            style = AppTypography.codeSmall,
                            color = StatusColors.Success,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = palette.textTertiary,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = extractor.jsonPath,
                            style = AppTypography.codeSmall,
                            color = palette.textSecondary
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                var varName by remember { mutableStateOf("") }
                var jsonPath by remember { mutableStateOf("") }

                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = varName,
                        onValueChange = { varName = it },
                        placeholder = { Text("var", style = AppTypography.labelSmall, color = palette.textTertiary) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        textStyle = AppTypography.codeSmall.copy(color = palette.textPrimary),
                        colors = TextFieldDefaults.outlinedTextFieldColors(
                            focusedBorderColor = BrandColors.Orange,
                            unfocusedBorderColor = palette.border,
                            cursorColor = BrandColors.Orange
                        )
                    )
                    Spacer(Modifier.width(4.dp))
                    OutlinedTextField(
                        value = jsonPath,
                        onValueChange = { jsonPath = it },
                        placeholder = { Text("$.path", style = AppTypography.labelSmall, color = palette.textTertiary) },
                        modifier = Modifier.weight(1.5f),
                        singleLine = true,
                        textStyle = AppTypography.codeSmall.copy(color = palette.textPrimary),
                        colors = TextFieldDefaults.outlinedTextFieldColors(
                            focusedBorderColor = BrandColors.Orange,
                            unfocusedBorderColor = palette.border,
                            cursorColor = BrandColors.Orange
                        )
                    )
                    Spacer(Modifier.width(4.dp))
                    IconButton(
                        onClick = {
                            if (varName.isNotBlank() && jsonPath.isNotBlank()) {
                                onAddExtractor(selectedNode.id, varName, jsonPath)
                                varName = ""
                                jsonPath = ""
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add extractor",
                            tint = BrandColors.Orange
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Actions
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { onStartConnecting(selectedNode.id) },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = BrandColors.Orange),
                    border = BorderStroke(1.dp, BrandColors.Orange),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(strings.connect, style = AppTypography.labelSmall)
                }
                OutlinedButton(
                    onClick = { onRemoveNode(selectedNode.id) },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusColors.Error),
                    border = BorderStroke(1.dp, StatusColors.Error),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(strings.remove, style = AppTypography.labelSmall)
                }
            }
        } else {
            Text(
                text = strings.selectNodeToView,
                style = AppTypography.bodyMedium,
                color = palette.textTertiary
            )
        }

        Spacer(Modifier.height(24.dp))

        Divider(color = palette.border)

        Spacer(Modifier.height(16.dp))

        // Execute Chain
        Text(
            text = strings.execution,
            style = AppTypography.titleMedium,
            color = palette.textPrimary
        )

        Spacer(Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onExecuteChain,
                enabled = state.nodes.isNotEmpty() && !state.executionState.isRunning,
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = BrandColors.Orange,
                    contentColor = Color.White,
                    disabledBackgroundColor = palette.border
                ),
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp)
            ) {
                if (state.executionState.isRunning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    if (state.executionState.isRunning) "Running..." else strings.executeChain,
                    style = AppTypography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            OutlinedButton(
                onClick = onClearChain,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = palette.textSecondary),
                border = BorderStroke(1.dp, palette.border)
            ) {
                Text(strings.clear, style = AppTypography.labelSmall)
            }
        }

        // Execution Results
        if (state.executionState.nodeResults.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))

            SectionCard(title = strings.results, badge = "${state.executionState.nodeResults.size}", icon = null) {
                state.nodes.forEach { node ->
                    val result = state.executionState.nodeResults[node.id]
                    if (result != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (result.passed) StatusColors.Success else StatusColors.Error)
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${node.method} ${node.path}",
                                    style = AppTypography.codeSmall,
                                    color = palette.textPrimary
                                )
                                Text(
                                    text = "${strings.status}: ${result.httpStatus} | ${result.responseTimeMs}${strings.ms}",
                                    style = AppTypography.labelSmall,
                                    color = palette.textSecondary
                                )
                                result.error?.let {
                                    Text(
                                        text = it,
                                        style = AppTypography.labelSmall,
                                        color = StatusColors.Error
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Extracted Variables
            if (state.executionState.variables.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                SectionCard(title = strings.extractedVariables, badge = "${state.executionState.variables.size}", icon = null) {
                    state.executionState.variables.forEach { (key, value) ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                        ) {
                            Text(
                                text = "\$$key",
                                style = AppTypography.codeSmall,
                                color = StatusColors.Success,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = value,
                                style = AppTypography.codeSmall,
                                color = palette.textSecondary,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }

        state.executionState.error?.let { error ->
            Spacer(Modifier.height(8.dp))
            Text(
                text = error,
                style = AppTypography.labelSmall,
                color = StatusColors.Error
            )
        }
    }
}
