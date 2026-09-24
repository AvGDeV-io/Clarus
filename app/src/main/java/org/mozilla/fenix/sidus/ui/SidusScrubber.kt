/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.sidus.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.mozilla.fenix.compose.Favicon
import org.mozilla.fenix.sidus.storage.TrailNode
import org.mozilla.fenix.theme.glass.ClarusGlassSurface
import org.mozilla.fenix.theme.glass.ClarusGlassTokens

private val AccentCopper = Color(0xFFB8754B)
private val DustLavender = Color(0xFFAAA0D2)

/**
 * Horizontal scrubber strip shown at the bottom of the Sidus trail view,
 * above the navigation dock.
 *
 * Displays all trail nodes in chronological order as mini frosted-glass chips.
 * The chip for the active tab's most recent node is highlighted with a Copper
 * accent border and background tint.
 *
 * Tapping a chip scrolls the [TrailMapView] to that node (Phase C).
 *
 * @param nodes         All [TrailNode]s for the current session.
 * @param activeTabId   The currently active tab's ID.
 * @param onChipTapped  Callback when a scrubber chip is tapped (Phase C).
 * @param modifier      Outer modifier.
 */
@Composable
fun SidusScrubber(
    nodes: List<TrailNode>,
    activeTabId: String,
    onChipTapped: (TrailNode) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        // Section label
        Text(
            text = "Session trail",
            color = DustLavender.copy(alpha = 0.7f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.08.sp,
            modifier = Modifier.padding(start = 16.dp, bottom = 6.dp),
        )

        // Frosted glass scrubber pill container
        ClarusGlassSurface(
            shape = RoundedCornerShape(18.dp),
            isDarkTheme = true,
            blurRadius = ClarusGlassTokens.Blur.Subtle,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .height(52.dp),
        ) {
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Only the most recent node for the active tab is truly "now"
                val activeNodeId = remember(nodes, activeTabId) {
                    nodes.filter { it.tabId == activeTabId }.maxByOrNull { it.timestamp }?.nodeId
                }
                nodes.forEach { node ->
                    val isActive = node.nodeId == activeNodeId
                    ScrubberChip(node = node, isActive = isActive, onTapped = { onChipTapped(node) })
                }
            }
        }
    }
}

@Composable
private fun ScrubberChip(
    node: TrailNode,
    isActive: Boolean,
    onTapped: () -> Unit,
) {
    val chipShape = RoundedCornerShape(6.dp)
    AnimatedVisibility(
        visible = true,
        enter = fadeIn(tween(200)) + expandVertically(
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        ),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(width = 44.dp, height = 30.dp)
                .clip(chipShape)
                .border(
                    width = if (isActive) 1.dp else 0.5.dp,
                    color = if (isActive) AccentCopper else Color.White.copy(alpha = 0.12f),
                    shape = chipShape,
                )
                .background(
                    color = if (isActive) AccentCopper.copy(alpha = 0.15f) else Color.Transparent,
                    shape = chipShape,
                )
                .clickable(onClick = onTapped),
        ) {
            Favicon(
                url = node.url,
                size = 18.dp,
                shape = CircleShape,
            )
        }
    }
}
