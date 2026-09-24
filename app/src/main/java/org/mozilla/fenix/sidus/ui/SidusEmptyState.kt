/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.sidus.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.mozilla.fenix.theme.glass.ClarusGlassSurface
import org.mozilla.fenix.theme.glass.ClarusGlassTokens

private val AccentCopper = Color(0xFFB8754B)
private val DustLavender = Color(0xFFAAA0D2)
private val Obsidian = Color(0xFF131319)

/**
 * Sidus empty state — shown when no trail nodes exist for the current session.
 *
 * Displays ghost node outlines (dashed borders, very low opacity) arranged in
 * a faint tree shape to hint at what the filled trail will look like. The ghost
 * nodes breathe gently via an infinite alpha animation.
 *
 * Below the ghost graph: icon, headline, supporting text, and a CTA button.
 *
 * @param onOpenNewTab  Called when the user taps "Open new tab". Phase C wires this.
 * @param modifier      Outer modifier.
 */
@Composable
fun SidusEmptyState(
    onOpenNewTab: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "ghost_breathe")
    val ghostAlpha by infiniteTransition.animateFloat(
        initialValue = 0.10f,
        targetValue = 0.22f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "ghost_alpha",
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Obsidian),
        contentAlignment = Alignment.Center,
    ) {
        // Ghost node canvas — background decorative layer
        GhostNodeCanvas(
            alpha = ghostAlpha,
            modifier = Modifier.fillMaxSize(),
        )

        // Central content
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 40.dp),
        ) {
            // Constellation glyph icon (star cluster)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(72.dp)
                    .background(
                        color = DustLavender.copy(alpha = 0.08f),
                        shape = CircleShape,
                    )
                    .border(1.dp, DustLavender.copy(alpha = 0.25f), CircleShape),
            ) {
                Text(
                    text = "✦",
                    color = DustLavender.copy(alpha = 0.5f),
                    fontSize = 32.sp,
                )
            }

            Spacer(Modifier.height(20.dp))

            Text(
                text = "No trails yet",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.3).sp,
            )

            Spacer(Modifier.height(10.dp))

            Text(
                text = "Start browsing and your navigation path will appear here as an interactive graph.",
                color = DustLavender,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp,
                modifier = Modifier.width(240.dp),
            )

            Spacer(Modifier.height(28.dp))

            // CTA: Open new tab
            ClarusGlassSurface(
                shape = RoundedCornerShape(percent = 50),
                isDarkTheme = true,
                modifier = Modifier
                    .border(
                        width = 1.dp,
                        color = AccentCopper.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(percent = 50),
                    )
                    .clip(RoundedCornerShape(percent = 50))
                    .clickable(onClick = onOpenNewTab),
            ) {
                Box(modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
                    Text(
                        text = "+ Open new tab",
                        color = AccentCopper,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

/**
 * Draws 4 ghost node outlines + faint Bezier curves on a [Canvas],
 * representing the placeholder trail structure.
 */
@Composable
private fun GhostNodeCanvas(alpha: Float, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.graphicsLayer { this.alpha = alpha }) {
        val w = size.width
        val h = size.height

        val ghostColor = Color(0xFFAAA0D2)
        val dashEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))
        val stroke = Stroke(width = 1.5f, pathEffect = dashEffect)

        // Ghost node rects
        val nodeW = 110.dp.toPx()
        val nodeH = 70.dp.toPx()

        data class GhostRect(val cx: Float, val cy: Float)

        val ghosts = listOf(
            GhostRect(w * 0.5f, h * 0.22f),
            GhostRect(w * 0.3f, h * 0.38f),
            GhostRect(w * 0.7f, h * 0.38f),
            GhostRect(w * 0.2f, h * 0.55f),
        )

        ghosts.forEach { g ->
            drawRoundRect(
                color = ghostColor,
                topLeft = Offset(g.cx - nodeW / 2f, g.cy - nodeH / 2f),
                size = Size(nodeW, nodeH),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(18.dp.toPx()),
                style = stroke,
            )
        }

        // Faint connector curves
        fun bezier(start: GhostRect, end: GhostRect) {
            val path = Path().apply {
                moveTo(start.cx, start.cy + nodeH / 2f)
                val cy = (start.cy + end.cy) / 2f
                cubicTo(start.cx, cy, end.cx, cy, end.cx, end.cy - nodeH / 2f)
            }
            drawPath(path, ghostColor, style = Stroke(width = 1.2f))
        }

        bezier(ghosts[0], ghosts[1])
        bezier(ghosts[0], ghosts[2])
        bezier(ghosts[1], ghosts[3])
    }
}
