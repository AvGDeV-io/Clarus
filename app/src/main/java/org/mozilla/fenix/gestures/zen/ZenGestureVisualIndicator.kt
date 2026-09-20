/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.gestures.zen

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.mozilla.fenix.theme.glass.ClarusGlassTokens
import kotlin.math.roundToInt

/**
 * Visual indicator displayed along the screen edge during a horizontal back/forward edge swipe.
 *
 * Implements ClarusGlass translucency with tactile spring scaling to provide immediate feedback
 * before the gesture commits.
 *
 * @param isLeftEdge True if the swipe originates from the left edge (Navigate Back), false for right edge (Forward).
 * @param progress Normalized gesture progress from 0f (start) to 1f (threshold reached).
 * @param isCommitted True if the user has dragged past the commit threshold.
 * @param modifier Optional modifier for placement.
 */
@Composable
fun ZenEdgeSwipeIndicator(
    isLeftEdge: Boolean,
    progress: Float,
    isCommitted: Boolean,
    modifier: Modifier = Modifier,
) {
    if (progress <= 0.05f) return

    val isDark = isSystemInDarkTheme()
    val animatedProgress by animateFloatAsState(targetValue = progress.coerceIn(0f, 1.2f))
    val baseScale = (0.7f + (animatedProgress * 0.3f)).coerceIn(0.7f, 1.15f)
    val indicatorAlpha = (animatedProgress * 1.5f).coerceIn(0f, 1f)

    val surfaceColor = if (isDark) {
        if (isCommitted) ClarusGlassTokens.Colors.AccentCopper.copy(alpha = 0.35f)
        else ClarusGlassTokens.Colors.DarkBase.copy(alpha = 0.85f)
    } else {
        if (isCommitted) ClarusGlassTokens.Colors.AccentCopper.copy(alpha = 0.25f)
        else ClarusGlassTokens.Colors.LightBase.copy(alpha = 0.90f)
    }

    val iconColor = if (isDark) {
        if (isCommitted) ClarusGlassTokens.Colors.AccentCopper
        else ClarusGlassTokens.Colors.DarkTextPrimary
    } else {
        if (isCommitted) ClarusGlassTokens.Colors.AccentCopper
        else ClarusGlassTokens.Colors.InkEspresso
    }

    val borderColor = if (isDark) {
        if (isCommitted) ClarusGlassTokens.Colors.AccentCopper.copy(alpha = 0.6f)
        else Color.White.copy(alpha = 0.22f)
    } else {
        if (isCommitted) ClarusGlassTokens.Colors.AccentCopper.copy(alpha = 0.5f)
        else Color.White.copy(alpha = 0.65f)
    }

    val horizontalOffset = if (isLeftEdge) {
        (animatedProgress * 24).dp
    } else {
        (-animatedProgress * 24).dp
    }

    Box(
        modifier = modifier
            .offset { IntOffset(x = horizontalOffset.roundToPx(), y = 0) }
            .alpha(indicatorAlpha)
            .scale(baseScale)
            .size(44.dp)
            .clip(CircleShape)
            .background(surfaceColor)
            .border(1.dp, borderColor, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(18.dp)) {
            val strokeWidth = 2.5.dp.toPx()
            val path = Path()

            if (isLeftEdge) {
                // Left arrow pointing back: <-
                path.moveTo(size.width * 0.65f, size.height * 0.2f)
                path.lineTo(size.width * 0.35f, size.height * 0.5f)
                path.lineTo(size.width * 0.65f, size.height * 0.8f)
            } else {
                // Right arrow pointing forward: ->
                path.moveTo(size.width * 0.35f, size.height * 0.2f)
                path.lineTo(size.width * 0.65f, size.height * 0.5f)
                path.lineTo(size.width * 0.35f, size.height * 0.8f)
            }

            drawPath(
                path = path,
                color = iconColor,
                style = Stroke(
                    width = strokeWidth,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
            )
        }
    }
}

/**
 * Visual pill indicator shown when dragging down from scroll-top to open a new tab.
 *
 * @param pullDistance Current downward pull distance in pixels.
 * @param threshold Commit threshold distance in pixels.
 * @param isCommitted Whether current pull exceeds the threshold.
 * @param modifier Optional modifier.
 */
@Composable
fun ZenNewTabPullIndicator(
    pullDistance: Float,
    threshold: Float,
    isCommitted: Boolean,
    modifier: Modifier = Modifier,
) {
    if (pullDistance <= 15f) return

    val isDark = isSystemInDarkTheme()
    val progress = (pullDistance / threshold).coerceIn(0f, 1.2f)
    val indicatorAlpha = (progress * 1.5f).coerceIn(0f, 1f)

    val surfaceColor = if (isDark) {
        if (isCommitted) ClarusGlassTokens.Colors.AccentCopper.copy(alpha = 0.35f)
        else ClarusGlassTokens.Colors.DarkBase.copy(alpha = 0.85f)
    } else {
        if (isCommitted) ClarusGlassTokens.Colors.AccentCopper.copy(alpha = 0.25f)
        else ClarusGlassTokens.Colors.LightBase.copy(alpha = 0.90f)
    }

    val contentColor = if (isDark) {
        if (isCommitted) ClarusGlassTokens.Colors.AccentCopper
        else ClarusGlassTokens.Colors.DarkTextPrimary
    } else {
        if (isCommitted) ClarusGlassTokens.Colors.AccentCopper
        else ClarusGlassTokens.Colors.InkEspresso
    }

    val borderColor = if (isDark) {
        if (isCommitted) ClarusGlassTokens.Colors.AccentCopper.copy(alpha = 0.6f)
        else Color.White.copy(alpha = 0.22f)
    } else {
        if (isCommitted) ClarusGlassTokens.Colors.AccentCopper.copy(alpha = 0.5f)
        else Color.White.copy(alpha = 0.65f)
    }

    val topOffset = (pullDistance * 0.35f).coerceAtMost(80f).dp

    Box(
        modifier = modifier
            .offset { IntOffset(x = 0, y = topOffset.roundToPx()) }
            .alpha(indicatorAlpha)
            .clip(RoundedCornerShape(20.dp))
            .background(surfaceColor)
            .border(1.dp, borderColor, RoundedCornerShape(20.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Plus icon: +
            Canvas(modifier = Modifier.size(12.dp)) {
                val strokeWidth = 2.dp.toPx()
                // Horizontal line
                drawLine(
                    color = contentColor,
                    start = Offset(0f, size.height / 2f),
                    end = Offset(size.width, size.height / 2f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
                // Vertical line
                drawLine(
                    color = contentColor,
                    start = Offset(size.width / 2f, 0f),
                    end = Offset(size.width / 2f, size.height),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = if (isCommitted) "Release for New Tab" else "Pull for New Tab",
                color = contentColor,
                fontSize = 12.sp,
                fontWeight = if (isCommitted) FontWeight.SemiBold else FontWeight.Normal,
            )
        }
    }
}
