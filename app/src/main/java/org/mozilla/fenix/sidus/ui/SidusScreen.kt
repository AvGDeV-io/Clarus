/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.sidus.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.Flow
import org.mozilla.fenix.sidus.storage.TrailNode
import org.mozilla.fenix.theme.glass.ClarusGlassSurface
import org.mozilla.fenix.theme.glass.ClarusGlassTokens

private val Obsidian = Color(0xFF131319)
private val DustLavender = Color(0xFFAAA0D2)
private val AccentCopper = Color(0xFFB8754B)

/**
 * Top-level composable for the Sidus trail view.
 *
 * Manages three UI states:
 * 1. **Empty** — no trail nodes exist yet for this session ([SidusEmptyState])
 * 2. **Trail map** — nodes are present, show the zoomable graph ([TrailMapView])
 *    plus the timeline scrubber ([SidusScrubber])
 * 3. **Branch point** — a new branch formed from an earlier node (shown as the
 *    trail map with a contextual notification banner)
 *
 * This composable is driven by a [Flow] of [TrailNode] lists so it reactively
 * updates whenever the [SidusMiddleware] inserts new nodes.
 *
 * @param nodesFlow     Flow emitting the current session's trail nodes.
 * @param activeTabId   The currently active tab ID (for node highlighting).
 * @param onOpenNewTab  Called when the user taps "Open new tab" in the empty state.
 * @param onNodeTapped  Called when a trail node is tapped (tap-to-jump).
 * @param modifier      Outer modifier.
 */
@Composable
fun SidusScreen(
    nodesFlow: Flow<List<TrailNode>>,
    activeTabId: String,
    onOpenNewTab: () -> Unit = {},
    onNodeTapped: (TrailNode) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val nodes by nodesFlow.collectAsState(initial = emptyList())
    val isEmpty = nodes.isEmpty()

    // Branch point detection:
    // Shown when the active node's parent has more than one child (i.e. navigating from a node that already branched)
    val activeNode = remember(nodes, activeTabId) {
        nodes.lastOrNull { it.tabId == activeTabId } ?: nodes.lastOrNull()
    }
    val parentNode = remember(nodes, activeNode) {
        activeNode?.parentNodeId?.let { parentId ->
            nodes.firstOrNull { it.nodeId == parentId }
        }
    }
    val branchChildrenCount = remember(nodes, activeNode) {
        if (activeNode?.parentNodeId != null) {
            nodes.count { it.parentNodeId == activeNode.parentNodeId }
        } else {
            0
        }
    }
    var bannerDismissedNodeId by remember {
        mutableStateOf<String?>(null)
    }
    val showBranchBanner = !isEmpty && branchChildrenCount > 1 && bannerDismissedNodeId != activeNode?.nodeId

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Obsidian),
    ) {
        AnimatedContent(
            targetState = isEmpty,
            transitionSpec = {
                fadeIn(tween(300)) + scaleIn(
                    initialScale = 0.96f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMedium,
                    ),
                ) togetherWith fadeOut(tween(200))
            },
            label = "sidus_content",
        ) { empty ->
            if (empty) {
                SidusEmptyState(
                    onOpenNewTab = onOpenNewTab,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Zoomable trail graph fills all available space
                    TrailMapView(
                        nodes = nodes,
                        activeTabId = activeTabId,
                        onNodeTapped = onNodeTapped,
                        modifier = Modifier.weight(1f),
                    )

                    // Scrubber strip above the bottom navigation dock.
                    // 72dp = 56dp FAB row height + 16dp breathing room, matching
                    // the approach used by other pages for the TabManagerFloatingToolbar overlay.
                    SidusScrubber(
                        nodes = nodes,
                        activeTabId = activeTabId,
                        onChipTapped = onNodeTapped,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 72.dp),
                    )
                }
            }
        }

        // Branch-point banner notification overlay
        AnimatedVisibility(
            visible = showBranchBanner,
            enter = fadeIn(tween(300)) + expandVertically(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium,
                ),
            ),
            exit = fadeOut(tween(200)) + shrinkVertically(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 74.dp, start = 14.dp, end = 14.dp)
                .fillMaxWidth(),
        ) {
            SidusBranchPointBanner(
                parentNode = parentNode,
                branchCount = branchChildrenCount,
                onDismiss = {
                    bannerDismissedNodeId = activeNode?.nodeId
                },
            )
        }

        // Header overlay — always visible
        SidusHeader(
            nodeCount = nodes.size,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth(),
        )
    }
}

/**
 * Frosted glass branch-point notification banner.
 *
 * Appears when the active node's parent has multiple child branches, alerting the
 * user that they are navigating along an existing branch point in the trail.
 */
@Composable
private fun SidusBranchPointBanner(
    parentNode: TrailNode?,
    branchCount: Int,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ClarusGlassSurface(
        shape = RoundedCornerShape(16.dp),
        isDarkTheme = true,
        blurRadius = ClarusGlassTokens.Blur.Subtle,
        modifier = modifier
            .border(
                width = 1.dp,
                color = AccentCopper.copy(alpha = 0.5f),
                shape = RoundedCornerShape(16.dp),
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            // Branch icon badge
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(AccentCopper.copy(alpha = 0.2f))
                    .border(
                        0.5.dp,
                        AccentCopper.copy(alpha = 0.6f),
                        CircleShape,
                    ),
            ) {
                Text(
                    text = "⑂",
                    color = AccentCopper,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Branch point · $branchCount paths",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                val parentDesc = parentNode?.title?.ifBlank { parentNode.url } ?: "earlier page"
                Text(
                    text = "Navigated from \"$parentDesc\"",
                    color = DustLavender,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.width(8.dp))

            // Dismiss button
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onDismiss),
            ) {
                Text(
                    text = "✕",
                    color = DustLavender.copy(alpha = 0.8f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

/**
 * Frosted glass header bar for the Sidus screen.
 *
 * Displays the 'Sidus' title, subtitle, and a node-count chip.
 * Back/share navigation actions are Phase C scope.
 */
@Composable
private fun SidusHeader(
    nodeCount: Int,
    modifier: Modifier = Modifier,
) {
    androidx.compose.material3.Surface(
        color = ClarusGlassTokens.Colors.DarkBase.copy(alpha = 0.85f),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
        ) {
            Text(
                text = "Sidus",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.3).sp,
            )
            Text(
                text = if (nodeCount > 0) "Your navigation trail · $nodeCount nodes" else "Your navigation trail",
                color = DustLavender,
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal,
            )
        }
    }
}
