package com.postsyncpro.presentation.screen.dependencygraph

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.postsyncpro.presentation.theme.*
import com.postsyncpro.presentation.components.*
import com.postsyncpro.presentation.i18n.LocalAppStrings
import com.postsyncpro.domain.model.*
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

private fun methodColor(method: String): Color = when (method.uppercase()) {
    "GET" -> HttpMethodColors.GET
    "POST" -> HttpMethodColors.POST
    "PUT" -> HttpMethodColors.PUT
    "DELETE" -> HttpMethodColors.DELETE
    "PATCH" -> HttpMethodColors.PATCH
    else -> BrandColors.Purple
}

private fun edgeColor(type: DependencyType): Color = when (type) {
    DependencyType.AUTH_TOKEN -> Color(0xFFFFD700)
    DependencyType.PATH_PARAM -> StatusColors.Info
    DependencyType.BODY_REFERENCE -> StatusColors.Success
    DependencyType.IMPLICIT -> Color(0xFF8A8A8A)
}

private fun edgeLabel(type: DependencyType): String = when (type) {
    DependencyType.AUTH_TOKEN -> "Auth Token"
    DependencyType.PATH_PARAM -> "Path Param"
    DependencyType.BODY_REFERENCE -> "Body Ref"
    DependencyType.IMPLICIT -> "Implicit"
}

private val clusterColors = listOf(
    HttpMethodColors.GET.copy(alpha = 0.08f),
    HttpMethodColors.POST.copy(alpha = 0.08f),
    HttpMethodColors.PUT.copy(alpha = 0.08f),
    HttpMethodColors.DELETE.copy(alpha = 0.08f),
    BrandColors.Purple.copy(alpha = 0.08f),
    HttpMethodColors.PATCH.copy(alpha = 0.08f)
)

@Composable
fun DependencyGraphScreen(viewModel: DependencyGraphViewModel) {
    val state by viewModel.state.collectAsState()
    val palette = LocalAppPalette.current
    val strings = LocalAppStrings.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
    ) {
        // Top Bar
        TopBar(
            state = state,
            palette = palette,
            onFileSelected = { viewModel.loadSwaggerFile(it) },
            onFilterChange = { viewModel.setFilter(it) },
            onZoomIn = { viewModel.updateZoom((state.zoom + 0.1f).coerceAtMost(3f)) },
            onZoomOut = { viewModel.updateZoom((state.zoom - 0.1f).coerceAtLeast(0.3f)) },
            onResetView = {
                viewModel.updateZoom(1f)
                viewModel.updateOffset(0f, 0f)
            }
        )

        Divider(color = palette.border)

        // Main content
        Row(modifier = Modifier.weight(1f)) {
            // Canvas area (75%)
            GraphCanvas(
                state = state,
                palette = palette,
                onNodeClick = { viewModel.selectNode(it) },
                onEdgeClick = { viewModel.selectEdge(it) },
                modifier = Modifier.weight(0.75f).fillMaxHeight()
            )

            Divider(
                modifier = Modifier.fillMaxHeight().width(1.dp),
                color = palette.border
            )

            // Detail panel (25%)
            DetailPanel(
                state = state,
                palette = palette,
                onDeselectNode = { viewModel.selectNode(null) },
                onDeselectEdge = { viewModel.selectEdge(null) },
                modifier = Modifier.weight(0.25f).fillMaxHeight()
            )
        }
    }
}

@Composable
private fun TopBar(
    state: DependencyGraphState,
    palette: AppPalette,
    onFileSelected: (java.io.File) -> Unit,
    onFilterChange: (String?) -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onResetView: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(palette.surface)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val strings = LocalAppStrings.current
        Text(
            text = strings.dependencyGraph,
            style = AppTypography.titleMedium,
            color = palette.textPrimary
        )

        Spacer(Modifier.width(8.dp))

        // File picker
        FilePickerButton(
            label = if (state.swaggerFileName.isNotEmpty()) state.swaggerFileName else strings.loadSwagger,
            fileExtensions = listOf("json", "yaml", "yml"),
            onFileSelected = onFileSelected
        )

        if (state.isAnalyzing) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = BrandColors.Orange,
                strokeWidth = 2.dp
            )
        }

        Spacer(Modifier.weight(1f))

        // Filter chips
        state.graph?.let { graph ->
            val categories = listOf(null to strings.all) + graph.clusters.map { it.name to it.name }.distinct()

            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(categories) { (category, label) ->
                    val isSelected = state.filterCategory == category
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (isSelected) BrandColors.Orange else palette.codeBg
                            )
                            .clickable { onFilterChange(category) }
                            .padding(horizontal = 12.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = label,
                            style = AppTypography.labelSmall,
                            color = if (isSelected) Color.White else palette.textSecondary,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }

        Spacer(Modifier.width(8.dp))

        // Zoom controls
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            IconButton(onClick = onZoomOut, modifier = Modifier.size(28.dp)) {
                Icon(
                    imageVector = Icons.Default.Remove,
                    contentDescription = strings.zoomOut,
                    tint = palette.textSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }

            Text(
                text = "${(state.zoom * 100).toInt()}%",
                style = AppTypography.labelSmall,
                color = palette.textSecondary
            )

            IconButton(onClick = onZoomIn, modifier = Modifier.size(28.dp)) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = strings.zoomIn,
                    tint = palette.textSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(Modifier.width(4.dp))

            IconButton(onClick = onResetView, modifier = Modifier.size(28.dp)) {
                Icon(
                    imageVector = Icons.Default.CenterFocusStrong,
                    contentDescription = strings.resetView,
                    tint = palette.textSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }

    // Error bar
    state.error?.let { error ->
        Text(
            text = error,
            style = AppTypography.labelSmall,
            color = StatusColors.Error,
            modifier = Modifier
                .fillMaxWidth()
                .background(StatusColors.Error.copy(alpha = 0.1f))
                .padding(horizontal = 16.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun GraphCanvas(
    state: DependencyGraphState,
    palette: AppPalette,
    onNodeClick: (String?) -> Unit,
    onEdgeClick: (DependencyEdge?) -> Unit,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val nodeWidth = 180f
    val nodeHeight = 56f

    val graph = state.graph
    val filteredNodes = if (state.filterCategory != null && graph != null) {
        val filteredCluster = graph.clusters.find { it.name == state.filterCategory }
        graph.nodes.filter { node -> filteredCluster?.nodeIds?.contains(node.id) == true }
    } else {
        graph?.nodes ?: emptyList()
    }
    val filteredNodeIds = filteredNodes.map { it.id }.toSet()
    val filteredEdges = graph?.edges?.filter {
        it.fromId in filteredNodeIds && it.toId in filteredNodeIds
    } ?: emptyList()

    Box(modifier = modifier.background(palette.background)) {
        if (graph == null) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Hub,
                    contentDescription = null,
                    tint = palette.textTertiary,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = LocalAppStrings.current.loadSwaggerForGraph,
                    style = AppTypography.bodyMedium,
                    color = palette.textTertiary
                )
            }
        } else {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = state.zoom
                        scaleY = state.zoom
                        translationX = state.canvasOffsetX
                        translationY = state.canvasOffsetY
                    }
                    .pointerInput(filteredNodes, state.zoom) {
                        detectTapGestures { offset ->
                            // Adjust for zoom and offset
                            val adjustedX = (offset.x - state.canvasOffsetX) / state.zoom
                            val adjustedY = (offset.y - state.canvasOffsetY) / state.zoom

                            val clickedNode = filteredNodes.find { node ->
                                val rect = Rect(
                                    left = node.posX,
                                    top = node.posY,
                                    right = node.posX + nodeWidth,
                                    bottom = node.posY + nodeHeight
                                )
                                rect.contains(Offset(adjustedX, adjustedY))
                            }
                            if (clickedNode != null) {
                                onNodeClick(clickedNode.id)
                                onEdgeClick(null)
                            } else {
                                // Check if clicked near an edge
                                val clickedEdge = filteredEdges.find { edge ->
                                    val fromNode = filteredNodes.find { it.id == edge.fromId }
                                    val toNode = filteredNodes.find { it.id == edge.toId }
                                    if (fromNode != null && toNode != null) {
                                        val midX = (fromNode.posX + nodeWidth + toNode.posX) / 2
                                        val midY = (fromNode.posY + nodeHeight / 2 + toNode.posY + nodeHeight / 2) / 2
                                        val dist = kotlin.math.sqrt(
                                            (adjustedX - midX) * (adjustedX - midX) +
                                                    (adjustedY - midY) * (adjustedY - midY)
                                        )
                                        dist < 20f
                                    } else false
                                }
                                if (clickedEdge != null) {
                                    onEdgeClick(clickedEdge)
                                    onNodeClick(null)
                                } else {
                                    onNodeClick(null)
                                    onEdgeClick(null)
                                }
                            }
                        }
                    }
            ) {
                // Draw cluster backgrounds
                graph.clusters.forEachIndexed { index, cluster ->
                    val clusterNodes = filteredNodes.filter { it.id in cluster.nodeIds }
                    if (clusterNodes.isNotEmpty()) {
                        val minX = clusterNodes.minOf { it.posX } - 20f
                        val minY = clusterNodes.minOf { it.posY } - 30f
                        val maxX = clusterNodes.maxOf { it.posX + nodeWidth } + 20f
                        val maxY = clusterNodes.maxOf { it.posY + nodeHeight } + 20f

                        val bgColor = clusterColors[index % clusterColors.size]

                        drawRoundRect(
                            color = bgColor,
                            topLeft = Offset(minX, minY),
                            size = androidx.compose.ui.geometry.Size(maxX - minX, maxY - minY),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(12f, 12f)
                        )

                        // Cluster label
                        val clusterLabel = buildAnnotatedString {
                            withStyle(SpanStyle(
                                color = palette.textTertiary.toArgb().let { Color(it) },
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )) {
                                append(cluster.name.uppercase())
                            }
                        }
                        drawText(
                            textMeasurer = textMeasurer,
                            text = clusterLabel,
                            topLeft = Offset(minX + 8f, minY + 6f)
                        )
                    }
                }

                // Draw edges
                filteredEdges.forEach { edge ->
                    val fromNode = filteredNodes.find { it.id == edge.fromId }
                    val toNode = filteredNodes.find { it.id == edge.toId }
                    if (fromNode != null && toNode != null) {
                        val isSelected = state.selectedEdge == edge

                        val from = Offset(
                            fromNode.posX + nodeWidth,
                            fromNode.posY + nodeHeight / 2
                        )
                        val to = Offset(
                            toNode.posX,
                            toNode.posY + nodeHeight / 2
                        )

                        val color = edgeColor(edge.type)
                        val isDashed = edge.type == DependencyType.IMPLICIT

                        drawDependencyEdge(
                            from = from,
                            to = to,
                            color = color,
                            isDashed = isDashed,
                            isSelected = isSelected
                        )

                        // Edge field label at midpoint
                        val midX = (from.x + to.x) / 2
                        val midY = (from.y + to.y) / 2 - 10f
                        val fieldLabel = buildAnnotatedString {
                            withStyle(SpanStyle(
                                color = color.toArgb().let { Color(it) },
                                fontSize = 8.sp
                            )) {
                                append(edge.field)
                            }
                        }
                        drawText(
                            textMeasurer = textMeasurer,
                            text = fieldLabel,
                            topLeft = Offset(midX - 15f, midY)
                        )
                    }
                }

                // Draw nodes
                filteredNodes.forEach { node ->
                    val isSelected = state.selectedNodeId == node.id

                    // Node shadow / selection highlight
                    if (isSelected) {
                        drawRoundRect(
                            color = BrandColors.Orange.copy(alpha = 0.3f),
                            topLeft = Offset(node.posX - 4f, node.posY - 4f),
                            size = androidx.compose.ui.geometry.Size(nodeWidth + 8f, nodeHeight + 8f),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(12f, 12f)
                        )
                    }

                    // Node body
                    drawRoundRect(
                        color = palette.surface,
                        topLeft = Offset(node.posX, node.posY),
                        size = androidx.compose.ui.geometry.Size(nodeWidth, nodeHeight),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f)
                    )

                    // Node border
                    drawRoundRect(
                        color = if (isSelected) BrandColors.Orange else palette.border,
                        topLeft = Offset(node.posX, node.posY),
                        size = androidx.compose.ui.geometry.Size(nodeWidth, nodeHeight),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f),
                        style = Stroke(width = if (isSelected) 2.5f else 1f)
                    )

                    // Method color header
                    drawRoundRect(
                        color = methodColor(node.method),
                        topLeft = Offset(node.posX, node.posY),
                        size = androidx.compose.ui.geometry.Size(nodeWidth, 20f),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f)
                    )
                    drawRect(
                        color = methodColor(node.method),
                        topLeft = Offset(node.posX, node.posY + 12f),
                        size = androidx.compose.ui.geometry.Size(nodeWidth, 8f)
                    )

                    // Method text
                    val methodText = buildAnnotatedString {
                        withStyle(SpanStyle(color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)) {
                            append(node.method.uppercase())
                        }
                    }
                    drawText(
                        textMeasurer = textMeasurer,
                        text = methodText,
                        topLeft = Offset(node.posX + 6f, node.posY + 3f)
                    )

                    // Category badge on header
                    val catText = buildAnnotatedString {
                        withStyle(SpanStyle(color = Color.White.copy(alpha = 0.8f), fontSize = 8.sp)) {
                            append(node.category)
                        }
                    }
                    drawText(
                        textMeasurer = textMeasurer,
                        text = catText,
                        topLeft = Offset(node.posX + nodeWidth - 60f, node.posY + 4f)
                    )

                    // Path text
                    val pathText = buildAnnotatedString {
                        withStyle(SpanStyle(
                            color = palette.textPrimary.toArgb().let { Color(it) },
                            fontSize = 10.sp
                        )) {
                            append(node.path.take(26))
                        }
                    }
                    drawText(
                        textMeasurer = textMeasurer,
                        text = pathText,
                        topLeft = Offset(node.posX + 6f, node.posY + 26f)
                    )

                    // Label
                    val labelText = buildAnnotatedString {
                        withStyle(SpanStyle(
                            color = palette.textTertiary.toArgb().let { Color(it) },
                            fontSize = 8.sp
                        )) {
                            append(node.label.take(30))
                        }
                    }
                    drawText(
                        textMeasurer = textMeasurer,
                        text = labelText,
                        topLeft = Offset(node.posX + 6f, node.posY + 40f)
                    )
                }
            }
        }
    }
}

private fun DrawScope.drawDependencyEdge(
    from: Offset,
    to: Offset,
    color: Color,
    isDashed: Boolean,
    isSelected: Boolean
) {
    val controlOffset = (to.x - from.x) * 0.4f
    val path = Path().apply {
        moveTo(from.x, from.y)
        cubicTo(
            from.x + controlOffset, from.y,
            to.x - controlOffset, to.y,
            to.x, to.y
        )
    }

    val strokeWidth = if (isSelected) 3f else 1.8f

    if (isDashed) {
        drawPath(
            path = path,
            color = color,
            style = Stroke(
                width = strokeWidth,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))
            )
        )
    } else {
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = strokeWidth)
        )
    }

    // Arrowhead
    val angle = atan2(to.y - (to.y + from.y) / 2, to.x - (to.x + from.x) / 2)
    val arrowSize = 10f
    val arrowPath = Path().apply {
        moveTo(to.x, to.y)
        lineTo(
            to.x - arrowSize * cos(angle - Math.PI.toFloat() / 6),
            to.y - arrowSize * sin(angle - Math.PI.toFloat() / 6)
        )
        lineTo(
            to.x - arrowSize * cos(angle + Math.PI.toFloat() / 6),
            to.y - arrowSize * sin(angle + Math.PI.toFloat() / 6)
        )
        close()
    }
    drawPath(path = arrowPath, color = color)
}

@Composable
private fun DetailPanel(
    state: DependencyGraphState,
    palette: AppPalette,
    onDeselectNode: () -> Unit,
    onDeselectEdge: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    val scrollState = rememberScrollState()
    val graph = state.graph
    val selectedNode = graph?.nodes?.find { it.id == state.selectedNodeId }
    val selectedEdge = state.selectedEdge

    Column(
        modifier = modifier
            .background(palette.surface)
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        Text(
            text = strings.details,
            style = AppTypography.titleMedium,
            color = palette.textPrimary
        )

        Spacer(Modifier.height(12.dp))

        when {
            selectedNode != null -> NodeDetail(
                node = selectedNode,
                edges = graph?.edges?.filter { it.fromId == selectedNode.id || it.toId == selectedNode.id } ?: emptyList(),
                allNodes = graph?.nodes ?: emptyList(),
                palette = palette,
                onClose = onDeselectNode
            )
            selectedEdge != null -> EdgeDetail(
                edge = selectedEdge,
                allNodes = graph?.nodes ?: emptyList(),
                palette = palette,
                onClose = onDeselectEdge
            )
            else -> {
                // Legend and instructions
                Text(
                    text = strings.clickNodeForDetails,
                    style = AppTypography.bodyMedium,
                    color = palette.textTertiary
                )

                Spacer(Modifier.height(20.dp))

                // Edge type legend
                SectionCard(title = strings.edgeTypes, badge = null, icon = null) {
                    DependencyType.values().forEach { type ->
                        Row(
                            modifier = Modifier.padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(24.dp)
                                    .height(3.dp)
                                    .background(edgeColor(type))
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = when (type) {
                                    DependencyType.AUTH_TOKEN -> strings.authToken
                                    DependencyType.PATH_PARAM -> strings.pathParam
                                    DependencyType.BODY_REFERENCE -> strings.bodyRef
                                    DependencyType.IMPLICIT -> strings.implicit
                                },
                                style = AppTypography.labelSmall,
                                color = palette.textSecondary
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Graph stats
                graph?.let { g ->
                    SectionCard(title = strings.graphStats, badge = null, icon = null) {
                        StatLine(strings.nodes, "${g.nodes.size}", palette)
                        StatLine(strings.edges, "${g.edges.size}", palette)
                        StatLine(strings.clusters, "${g.clusters.size}", palette)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatLine(label: String, value: String, palette: AppPalette) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = AppTypography.labelSmall, color = palette.textTertiary)
        Text(text = value, style = AppTypography.labelSmall, color = palette.textPrimary, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun NodeDetail(
    node: DependencyNode,
    edges: List<DependencyEdge>,
    allNodes: List<DependencyNode>,
    palette: AppPalette,
    onClose: () -> Unit
) {
    val strings = LocalAppStrings.current
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = strings.node,
                style = AppTypography.titleMedium,
                color = palette.textPrimary
            )
            IconButton(onClick = onClose, modifier = Modifier.size(24.dp)) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = strings.close,
                    tint = palette.textTertiary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // Method + Path
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(methodColor(node.method))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = node.method.uppercase(),
                    style = AppTypography.labelSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = node.path,
                style = AppTypography.codeSmall,
                color = palette.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(Modifier.height(4.dp))

        Text(
            text = node.label,
            style = AppTypography.bodyMedium,
            color = palette.textSecondary
        )

        Spacer(Modifier.height(4.dp))

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(palette.codeBg)
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                text = node.category,
                style = AppTypography.labelSmall,
                color = palette.textTertiary
            )
        }

        Spacer(Modifier.height(16.dp))

        // Input Fields
        SectionCard(title = strings.inputFields, badge = "${node.inputFields.size}", icon = null) {
            if (node.inputFields.isEmpty()) {
                Text(
                    text = strings.noInputFields,
                    style = AppTypography.labelSmall,
                    color = palette.textTertiary
                )
            }
            node.inputFields.forEach { field ->
                Row(
                    modifier = Modifier.padding(vertical = 1.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = StatusColors.Info,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = field,
                        style = AppTypography.codeSmall,
                        color = palette.textPrimary
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // Output Fields
        SectionCard(title = strings.outputFields, badge = "${node.outputFields.size}", icon = null) {
            if (node.outputFields.isEmpty()) {
                Text(
                    text = "No output fields",
                    style = AppTypography.labelSmall,
                    color = palette.textTertiary
                )
            }
            node.outputFields.forEach { field ->
                Row(
                    modifier = Modifier.padding(vertical = 1.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                        tint = StatusColors.Success,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = field,
                        style = AppTypography.codeSmall,
                        color = palette.textPrimary
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // Connected edges
        if (edges.isNotEmpty()) {
            SectionCard(title = "Connections", badge = "${edges.size}", icon = null) {
                edges.forEach { edge ->
                    val otherNodeId = if (edge.fromId == node.id) edge.toId else edge.fromId
                    val otherNode = allNodes.find { it.id == otherNodeId }
                    val direction = if (edge.fromId == node.id) "to" else "from"

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(edgeColor(edge.type).copy(alpha = 0.1f))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(edgeColor(edge.type))
                        )
                        Spacer(Modifier.width(6.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "$direction ${otherNode?.method?.uppercase() ?: ""} ${otherNode?.path ?: otherNodeId}",
                                style = AppTypography.codeSmall,
                                color = palette.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${edge.field} (${when (edge.type) {
                                DependencyType.AUTH_TOKEN -> strings.authToken
                                DependencyType.PATH_PARAM -> strings.pathParam
                                DependencyType.BODY_REFERENCE -> strings.bodyRef
                                DependencyType.IMPLICIT -> strings.implicit
                            }})",
                                style = AppTypography.labelSmall,
                                color = palette.textTertiary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EdgeDetail(
    edge: DependencyEdge,
    allNodes: List<DependencyNode>,
    palette: AppPalette,
    onClose: () -> Unit
) {
    val strings = LocalAppStrings.current
    val fromNode = allNodes.find { it.id == edge.fromId }
    val toNode = allNodes.find { it.id == edge.toId }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Edge",
                style = AppTypography.titleMedium,
                color = palette.textPrimary
            )
            IconButton(onClick = onClose, modifier = Modifier.size(24.dp)) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = strings.close,
                    tint = palette.textTertiary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // Type badge
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(edgeColor(edge.type))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = when (edge.type) {
                        DependencyType.AUTH_TOKEN -> strings.authToken
                        DependencyType.PATH_PARAM -> strings.pathParam
                        DependencyType.BODY_REFERENCE -> strings.bodyRef
                        DependencyType.IMPLICIT -> strings.implicit
                    },
                    style = AppTypography.labelSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = edge.field,
                style = AppTypography.codeMedium,
                color = palette.textPrimary,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.height(12.dp))

        // Description
        Text(
            text = "Description",
            style = AppTypography.labelSmall,
            color = palette.textTertiary,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = edge.description,
            style = AppTypography.bodyMedium,
            color = palette.textSecondary
        )

        Spacer(Modifier.height(16.dp))

        // From endpoint
        SectionCard(title = "From", badge = null, icon = null) {
            if (fromNode != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(methodColor(fromNode.method))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = fromNode.method.uppercase(),
                            style = AppTypography.labelSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = fromNode.path,
                        style = AppTypography.codeSmall,
                        color = palette.textPrimary
                    )
                }
                Text(
                    text = fromNode.label,
                    style = AppTypography.labelSmall,
                    color = palette.textTertiary,
                    modifier = Modifier.padding(top = 2.dp)
                )
            } else {
                Text(
                    text = edge.fromId,
                    style = AppTypography.codeSmall,
                    color = palette.textSecondary
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // Arrow indicator
        Icon(
            imageVector = Icons.Default.ArrowDownward,
            contentDescription = null,
            tint = edgeColor(edge.type),
            modifier = Modifier.align(Alignment.CenterHorizontally).size(20.dp)
        )

        Spacer(Modifier.height(8.dp))

        // To endpoint
        SectionCard(title = "To", badge = null, icon = null) {
            if (toNode != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(methodColor(toNode.method))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = toNode.method.uppercase(),
                            style = AppTypography.labelSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = toNode.path,
                        style = AppTypography.codeSmall,
                        color = palette.textPrimary
                    )
                }
                Text(
                    text = toNode.label,
                    style = AppTypography.labelSmall,
                    color = palette.textTertiary,
                    modifier = Modifier.padding(top = 2.dp)
                )
            } else {
                Text(
                    text = edge.toId,
                    style = AppTypography.codeSmall,
                    color = palette.textSecondary
                )
            }
        }
    }
}
