/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.theme.glass

import android.graphics.Matrix
import android.graphics.SweepGradient
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.toArgb

/**
 * Rotating sweep gradient shader brush used for Clarus chromatic rings and loading strips.
 */
class ClarusSweepGradientBrush(
    private val colors: List<Color>,
    private val rotationAngle: Float,
) : ShaderBrush() {
    override fun createShader(size: Size): Shader {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val colorInts = IntArray(colors.size) { colors[it].toArgb() }
        val shader = SweepGradient(cx, cy, colorInts, null)
        val matrix = Matrix()
        matrix.postRotate(rotationAngle, cx, cy)
        shader.setLocalMatrix(matrix)
        return shader
    }
}

/** Full-spectrum chromatic palette for the active-tab RGB glow ring. */
val ClarusRgbGlowColors: List<Color> = listOf(
    Color(0xFFFF3B30),
    Color(0xFFFF9500),
    Color(0xFFFFCC00),
    Color(0xFF34C759),
    Color(0xFF00C7BE),
    Color(0xFF0A84FF),
    Color(0xFFAF52DE),
    Color(0xFFFF2D55),
    Color(0xFFFF3B30),
)

fun clarusRgbGlowBrush(rotationAngle: Float): Brush =
    ClarusSweepGradientBrush(colors = ClarusRgbGlowColors, rotationAngle = rotationAngle)
