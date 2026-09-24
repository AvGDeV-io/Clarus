/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.home.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import org.mozilla.fenix.theme.ClarusHaptics
import org.mozilla.fenix.theme.clarusPressAnimation

/**
 * Minimal custom homepage for Clarus.
 *
 * Replaces the legacy homepage with a clean, centered Clarus emblem responding
 * dynamically to system light/dark mode and the private obsidian sanctuary theme.
 */
@Composable
fun ClarusMinimalHomepage(
    isPrivateMode: Boolean,
    modifier: Modifier = Modifier,
    onLogoClick: (() -> Unit)? = null,
) {
    val isDark = isSystemInDarkTheme() || isPrivateMode
    val view = LocalView.current

    val infiniteTransition = rememberInfiniteTransition(label = "ClarusBreathing")
    val breathScale by infiniteTransition.animateFloat(
        initialValue = 0.985f,
        targetValue = 1.015f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "EmblemBreathScale",
    )
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.65f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "EmblemGlowAlpha",
    )

    // Background color: Deep Obsidian for private mode, transparent for normal
    val bgModifier = if (isPrivateMode) {
        Modifier.background(Color(0xFF0C0B0E))
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .then(bgModifier),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(148.dp)
                .graphicsLayer {
                    scaleX = breathScale
                    scaleY = breathScale
                }
                .clarusPressAnimation(
                    scaleDown = 0.92f,
                    onClick = {
                        ClarusHaptics.performClick(view)
                        onLogoClick?.invoke()
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            ClarusEmblemCanvas(
                isDark = isDark,
                isPrivate = isPrivateMode,
                glowMultiplier = glowAlpha,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun ClarusEmblemCanvas(
    isDark: Boolean,
    isPrivate: Boolean,
    glowMultiplier: Float,
    modifier: Modifier = Modifier,
) {
    // Dynamic theme colors
    val outerColors = remember(isDark, isPrivate) {
        when {
            isPrivate -> listOf(Color(0xFFFBBF24), Color(0xFFF59E0B), Color(0xFFB45309))
            isDark -> listOf(Color(0xFFF59E0B), Color(0xFFE07A5F), Color(0xFFD97706))
            else -> listOf(Color(0xFFD5A24A), Color(0xFFB8754B), Color(0xFFC9786A))
        }
    }

    val innerColors = remember(isDark, isPrivate) {
        when {
            isPrivate -> listOf(Color(0xFFFEF3C7), Color(0xFFFBBF24), Color(0xFFD97706))
            isDark -> listOf(Color(0xFFFBBF24), Color(0xFFF59E0B), Color(0xFFB45309))
            else -> listOf(Color(0xFFE6AAA0D2), Color(0xFFB37B73A8), Color(0xFF8036335F))
        }
    }

    val focalPointColor = remember(isDark, isPrivate) {
        when {
            isPrivate -> Color(0xFFFEF3C7)
            isDark -> Color(0xFFF7F3EC)
            else -> Color(0xFF2E2825)
        }
    }

    val haloCopperColor = remember(isDark, isPrivate) {
        when {
            isPrivate -> Color(0xFFF59E0B).copy(alpha = 0.28f * glowMultiplier)
            isDark -> Color(0xFFB8754B).copy(alpha = 0.20f * glowMultiplier)
            else -> Color(0xFFB8754B).copy(alpha = 0.14f * glowMultiplier)
        }
    }

    val haloVioletColor = remember(isDark, isPrivate) {
        when {
            isPrivate -> Color(0xFFF59E0B).copy(alpha = 0.22f * glowMultiplier)
            isDark -> Color(0xFFAAA0D2).copy(alpha = 0.18f * glowMultiplier)
            else -> Color(0xFFAAA0D2).copy(alpha = 0.12f * glowMultiplier)
        }
    }

    Canvas(modifier = modifier) {
        val s = size.width / 512f

        scale(s, pivot = Offset.Zero) {
            // Layer 1: Copper halo
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(haloCopperColor, Color.Transparent),
                    center = Offset(256f, 256f),
                    radius = 150f,
                ),
                radius = 150f,
                center = Offset(256f, 256f),
            )

            // Layer 2: Lavender/Violet halo
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(haloVioletColor, Color.Transparent),
                    center = Offset(280f, 240f),
                    radius = 120f,
                ),
                radius = 120f,
                center = Offset(280f, 240f),
            )

            // Layer 3: Outer Arc C
            val outerArcPath = Path().apply {
                moveTo(336f, 160f)
                arcTo(
                    rect = androidx.compose.ui.geometry.Rect(
                        left = 256f - 130f,
                        top = 256f - 130f,
                        right = 256f + 130f,
                        bottom = 256f + 130f,
                    ),
                    startAngleDegrees = -52f,
                    sweepAngleDegrees = -256f,
                    forceMoveTo = false,
                )
            }
            drawPath(
                path = outerArcPath,
                brush = Brush.linearGradient(
                    colors = outerColors,
                    start = Offset(150f, 150f),
                    end = Offset(350f, 350f),
                ),
                style = Stroke(
                    width = 32f,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
            )

            // Layer 4: Inner Arc C
            val innerArcPath = Path().apply {
                moveTo(280f, 180f)
                arcTo(
                    rect = androidx.compose.ui.geometry.Rect(
                        left = 280f - 95f,
                        top = 256f - 95f,
                        right = 280f + 95f,
                        bottom = 256f + 95f,
                    ),
                    startAngleDegrees = -53f,
                    sweepAngleDegrees = 106f,
                    forceMoveTo = false,
                )
            }
            drawPath(
                path = innerArcPath,
                brush = Brush.linearGradient(
                    colors = innerColors,
                    start = Offset(280f, 180f),
                    end = Offset(185f, 332f),
                ),
                style = Stroke(
                    width = 22f,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
            )

            // Layer 5: Focal Point Halo
            drawCircle(
                color = focalPointColor.copy(alpha = 0.25f),
                radius = 20f,
                center = Offset(256f, 256f),
            )

            // Layer 6: Central Focal Point
            drawCircle(
                color = focalPointColor,
                radius = 12f,
                center = Offset(256f, 256f),
            )
        }
    }
}
