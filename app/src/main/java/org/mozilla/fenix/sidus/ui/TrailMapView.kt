/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.sidus.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.mozilla.fenix.sidus.storage.TrailNode
import org.mozilla.fenix.theme.glass.ClarusGlassTokens
import kotlin.math.roundToInt

// Soft Copper accent: #B8754B
private val AccentCopper = Color(0xFFB8754B)

// Edge line colors
private val EdgeColorInactive = Color(0xFFAAA0D2).copy(alpha = 0.25f)
private val EdgeColorActive = AccentCopper.copy(alpha = 0.80f)

/**
 * Layout metadata for a positioned trail node on the zoomable canvas.
 * [x]/[y] are the card's top-left corner in Dp.
 */
data class NodePosition(val node: TrailNode, val x: Dp, val y: Dp)

private val NODE_WIDTH_DP = 130.dp
private val NODE_HEIGHT_DP = 85.dp

// Constellation spacing — organic, not a single vertical column
private val ORIGIN_Y = 48.dp
private val LAYER_GAP = 54.dp
private val BRANCH_SPREAD = 168.dp
private val CHAIN_DRIFT = 42.dp
private val ROOT_SPREAD = 340.dp
private val SINGLE_ROOT_X = 150.dp

/**
 * Zoomable, pannable constellation map of the Sidus trail graph.
 *
 * Nodes are placed with a spatial constellation layout: linear chains drift
 * gently, multi-child branches fan left/right around their parent (matching
 * the "Sidus Visual Navigation" design). Edges draw in when first created;
 * node cards spring-fade in on appearance.
 */
@Composable
fun TrailMapView(
    nodes: List<TrailNode>,
    activeTabId: String,
    onNodeTapped: (TrailNode) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    val visibleNodes = remember { mutableStateMapOf<String, Boolean>() }
    val edgeProgress = remember { mutableStateMapOf<String, Float>() }
    val edgesSeeded = remember { mutableStateOf(false) }
    val pathMeasure = remember { PathMeasure() }

    val positions = remember(nodes) { computeNodeLayout(nodes) }

    val activeNodeId = remember(nodes, activeTabId) {
        nodes.filter { it.tabId == activeTabId }.maxByOrNull { it.timestamp }?.nodeId
    }

    // Node entrance: staggered spring/fade when new nodes appear
    LaunchedEffect(nodes) {
        val unseen = nodes.filter { !visibleNodes.containsKey(it.nodeId) }
        unseen.forEachIndexed { index, node ->
            if (unseen.size > 1) {
                delay((index * 35L).coerceAtMost(210L))
            }
            visibleNodes[node.nodeId] = true
        }
    }

    // Edge draw-in: animate only edges that are new to this composition
    LaunchedEffect(nodes) {
        val newEdges = nodes.filter {
            it.parentNodeId != null && !edgeProgress.containsKey(it.nodeId)
        }
        if (newEdges.isEmpty()) {
            edgesSeeded.value = true
            return@LaunchedEffect
        }
        val wasSeeded = edgesSeeded.value
        edgesSeeded.value = true
        coroutineScope {
            newEdges.forEachIndexed { index, node ->
                launch {
                    if (!wasSeeded) {
                        delay((index * 25L).coerceAtMost(200L))
                    }
                    edgeProgress[node.nodeId] = 0f
                    val duration = if (wasSeeded) 360 else 280
                    animate(
                        initialValue = 0f,
                        targetValue = 1f,
                        animationSpec = tween(durationMillis = duration, easing = FastOutSlowInEasing),
                    ) { value, _ ->
                        edgeProgress[node.nodeId] = value
                    }
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ClarusGlassTokens.Colors.DarkDeep)
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(0.4f, 3.0f)
                    offsetX += pan.x
                    offsetY += pan.y
                }
            },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offsetX
                    translationY = offsetY
                    transformOrigin = TransformOrigin(0f, 0f)
                },
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                positions.forEach { (nodeId, position) ->
                    val node = nodes.firstOrNull { it.nodeId == nodeId } ?: return@forEach
                    val parentPos = node.parentNodeId?.let { positions[it] } ?: return@forEach
                    val progress = edgeProgress[nodeId] ?: 1f
                    if (progress <= 0f) return@forEach

                    val start = Offset(
                        x = parentPos.x.toPx() + NODE_WIDTH_DP.toPx() / 2f,
                        y = parentPos.y.toPx() + NODE_HEIGHT_DP.toPx(),
                    )
                    val end = Offset(
                        x = position.x.toPx() + NODE_WIDTH_DP.toPx() / 2f,
                        y = position.y.toPx(),
                    )
                    val dx = end.x - start.x
                    val midY = (start.y + end.y) / 2f
                    val lateral = dx * 0.18f

                    val path = Path().apply {
                        moveTo(start.x, start.y)
                        cubicTo(
                            start.x + lateral,
                            midY,
                            end.x - lateral,
                            midY,
                            end.x,
                            end.y,
                        )
                    }

                    val isActiveEdge = node.tabId == activeTabId
                    val color = if (isActiveEdge) EdgeColorActive else EdgeColorInactive
                    val stroke = Stroke(width = if (isActiveEdge) 2.5f else 1.5f)

                    if (progress >= 1f) {
                        drawPath(path = path, color = color, style = stroke)
                    } else {
                        pathMeasure.setPath(path, false)
                        val partial = Path()
                        pathMeasure.getSegment(
                            startDistance = 0f,
                            stopDistance = pathMeasure.length * progress,
                            destination = partial,
                            startWithMoveTo = true,
                        )
                        drawPath(path = partial, color = color, style = stroke)
                    }
                }
            }

            positions.forEach { (nodeId, position) ->
                val node = nodes.firstOrNull { it.nodeId == nodeId } ?: return@forEach
                val density = LocalDensity.current
                val xPx = with(density) { position.x.toPx().roundToInt() }
                val yPx = with(density) { position.y.toPx().roundToInt() }
                val isVisible = visibleNodes[nodeId] == true

                AnimatedVisibility(
                    visible = isVisible,
                    enter = fadeIn(animationSpec = tween(220)) +
                        scaleIn(
                            initialScale = 0.7f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium,
                            ),
                        ),
                    modifier = Modifier.offset { IntOffset(xPx, yPx) },
                ) {
                    SidusNodeCard(
                        node = node,
                        isActive = node.nodeId == activeNodeId,
                        onTapped = { onNodeTapped(node) },
                    )
                }
            }
        }
    }
}

/**
 * Constellation spatial layout.
 *
 * - Linear chains (single child) drift gently instead of forming a rigid column.
 * - Multi-child branches fan left then right around the parent, with organic
 *   jitter derived from node ids (stable across recompositions).
 * - Depth advances downward; siblings separate laterally.
 */
private fun computeNodeLayout(nodes: List<TrailNode>): Map<String, NodePosition> {
    if (nodes.isEmpty()) return emptyMap()

    val byId = nodes.associateBy { it.nodeId }
    val children = mutableMapOf<String?, MutableList<TrailNode>>()
    nodes.sortedBy { it.timestamp }.forEach { node ->
        children.getOrPut(node.parentNodeId) { mutableListOf() }.add(node)
    }

    val roots = mutableListOf<TrailNode>()
    children[null]?.let { roots.addAll(it) }
    nodes.sortedBy { it.timestamp }.forEach { node ->
        if (node.parentNodeId != null && byId[node.parentNodeId] == null) {
            roots.add(node)
        }
    }

    val raw = mutableMapOf<String, Offset>()

    fun jitter(id: String, salt: Int): Float {
        var h = salt * 31 + 17
        for (c in id) {
            h = h * 31 + c.code
        }
        return ((h % 2000) / 2000f) - 0.5f
    }

    fun place(node: TrailNode, depth: Int, centerX: Float) {
        val y = ORIGIN_Y.value + depth * (NODE_HEIGHT_DP.value + LAYER_GAP.value)
        val kids = children[node.nodeId].orEmpty().sortedBy { it.timestamp }
        val x: Float

        when {
            kids.isEmpty() -> {
                x = centerX + jitter(node.nodeId, depth) * CHAIN_DRIFT.value
            }

            kids.size == 1 -> {
                x = centerX + jitter(node.nodeId, depth) * (CHAIN_DRIFT.value * 0.55f)
                val child = kids[0]
                val childX = x + jitter(child.nodeId, depth + 1) * CHAIN_DRIFT.value
                place(child, depth + 1, childX)
            }

            else -> {
                // Fan: first branch left, second right, then further out (design Branch A/B)
                x = centerX + jitter(node.nodeId, depth) * 18f
                kids.forEachIndexed { i, child ->
                    val side = if (i % 2 == 0) -1f else 1f
                    val magnitude = (i / 2 + 1).toFloat()
                    val slot = side * magnitude * BRANCH_SPREAD.value +
                        jitter(child.nodeId, depth + 1) * 28f
                    place(child, depth + 1, x + slot)
                }
            }
        }

        // Store top-left corner
        raw[node.nodeId] = Offset(x - NODE_WIDTH_DP.value / 2f, y)
    }

    val orderedRoots = roots.distinct().sortedBy { it.timestamp }
    when {
        orderedRoots.isEmpty() -> Unit
        orderedRoots.size == 1 -> place(orderedRoots[0], 0, SINGLE_ROOT_X.value)
        else -> {
            val mid = (orderedRoots.size - 1) / 2f
            orderedRoots.forEachIndexed { i, root ->
                val cx = SINGLE_ROOT_X.value + (i - mid) * ROOT_SPREAD.value
                place(root, 0, cx)
            }
        }
    }

    return raw.mapValues { (nodeId, offset) ->
        val node = byId.getValue(nodeId)
        NodePosition(node = node, x = offset.x.dp, y = offset.y.dp)
    }
}
