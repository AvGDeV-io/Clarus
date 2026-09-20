/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.theme.glass

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Reusable Clarus glassmorphic surface component.
 *
 * Implements a tactile, floating glass plate inspired by Safari liquid-glass and Zen-browser
 * aesthetics.
 *
 * ### Layered Architecture (Blur Fix):
 * The blur effect is applied to a dedicated background plate layer (a sibling [Box] with
 * [Modifier.matchParentSize]) so that the [RenderEffect] only blurs the translucent plate
 * itself. Content (text, icons, buttons) is rendered in a separate [Box] on top of the blur
 * layer, ensuring full crispness and legibility.
 *
 * ### Dual-Path Architecture (minSdk 26 Compatibility):
 * - **API 31+ (Android 12+)**: Leverages hardware-accelerated [RenderEffect] blur via [graphicsLayer]
 *   with calibrated translucent plate alpha (e.g. 78% Light, 72% Dark).
 * - **API 26..30 (Android 8.0 - 11)**: Gracefully falls back to a non-blurred, higher-opacity translucent
 *   surface (e.g. 92% Light, 90% Dark) providing identical elevation, tactile specular borders, and high
 *   legibility over underlying web content without unsupported shader operations or performance degradation.
 *
 * @param modifier Modifier applied to the outer surface container.
 * @param shape Curvature shape of the glass plate (defaults to [ClarusGlassTokens.Shapes.Pill]).
 * @param elevation Depth elevation for shadow casting (defaults to [ClarusGlassTokens.Elevation.Level2]).
 * @param isDarkTheme Whether dark mode styling should be applied.
 * @param isPrivate Whether private browsing sanctuary styling should be applied.
 * @param isActive Whether the surface is in an active / focused state (e.g. active tab, focused omni-bar).
 * @param blurRadius Gaussian blur radius applied on supported Android versions.
 * @param forceFallback If true, forces the API < 31 fallback branch (useful for testing and previews).
 * @param content Composable content placed within the glass surface.
 */
@Composable
fun ClarusGlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = ClarusGlassTokens.Shapes.Pill,
    elevation: Dp = ClarusGlassTokens.Elevation.Level2,
    isDarkTheme: Boolean = isSystemInDarkTheme(),
    isPrivate: Boolean = false,
    isActive: Boolean = false,
    blurRadius: Dp = ClarusGlassTokens.Blur.Standard,
    forceFallback: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val supportsRenderEffect = isBlurSupported() && !forceFallback

    // Resolve base plate color and alpha based on theme, blur support, and active state
    val baseColor: Color = when {
        isPrivate -> ClarusGlassTokens.Colors.DarkDeep
        isDarkTheme -> ClarusGlassTokens.Colors.DarkBase
        else -> ClarusGlassTokens.Colors.LightBase
    }

    val plateAlpha: Float = when {
        !supportsRenderEffect -> {
            // Graceful non-blurred fallback: elevated opacity preserves contrast and legibility
            when {
                isPrivate -> ClarusGlassTokens.Alpha.Private.Fallback
                isDarkTheme -> ClarusGlassTokens.Alpha.Dark.Fallback
                else -> ClarusGlassTokens.Alpha.Light.Fallback
            }
        }
        isActive -> {
            when {
                isPrivate -> ClarusGlassTokens.Alpha.Private.Active
                isDarkTheme -> ClarusGlassTokens.Alpha.Dark.Active
                else -> ClarusGlassTokens.Alpha.Light.Active
            }
        }
        else -> {
            when {
                isPrivate -> ClarusGlassTokens.Alpha.Private.Resting
                isDarkTheme -> ClarusGlassTokens.Alpha.Dark.Resting
                else -> ClarusGlassTokens.Alpha.Light.Resting
            }
        }
    }

    val surfaceColor = baseColor.copy(alpha = plateAlpha)

    // Resolve specular border highlight
    val borderWidth = if (isActive) ClarusGlassTokens.Border.ActiveWidth else ClarusGlassTokens.Border.Width
    val borderBrush: Brush = when {
        isActive && isPrivate -> ClarusGlassTokens.Border.activePrivateBorderBrush()
        isActive -> ClarusGlassTokens.Border.activeBorderBrush(isDark = isDarkTheme)
        isPrivate -> ClarusGlassTokens.Border.restingPrivateBorderBrush()
        isDarkTheme -> ClarusGlassTokens.Border.restingDarkBorderBrush()
        else -> ClarusGlassTokens.Border.restingLightBorderBrush()
    }

    // Resolve shadow colors
    val ambientShadowColor = if (isDarkTheme || isPrivate) {
        ClarusGlassTokens.Elevation.AmbientShadowDark
    } else {
        ClarusGlassTokens.Elevation.AmbientShadowLight
    }

    val spotShadowColor = if (isDarkTheme || isPrivate) {
        ClarusGlassTokens.Elevation.SpotShadowDark
    } else {
        ClarusGlassTokens.Elevation.SpotShadowLight
    }

    // Outer container: shadow + clip applied to the whole composable
    Box(
        modifier = modifier
            .shadow(
                elevation = elevation,
                shape = shape,
                clip = false,
                ambientColor = ambientShadowColor,
                spotColor = spotShadowColor,
            )
            .clip(shape),
    ) {
        // Layer 1: Translucent blur plate (no content children -> only the plate itself is blurred)
        Box(
            modifier = Modifier
                .matchParentSize()
                .then(
                    if (supportsRenderEffect && blurRadius > 0.dp) {
                        Modifier.clarusGlassBlur(blurRadius)
                    } else {
                        Modifier
                    },
                )
                .background(surfaceColor)
                .border(
                    width = borderWidth,
                    brush = borderBrush,
                    shape = shape,
                ),
        )

        // Layer 2: Crisp content rendered on top of the blur plate
        Box(content = content)
    }
}

/**
 * Modifier extension applying RenderEffect blur on API 31+.
 * Extracted and guarded to prevent runtime class verification issues on API < 31.
 */
@VisibleForTesting
internal fun Modifier.clarusGlassBlur(radius: Dp): Modifier = if (isBlurSupported()) {
    this.graphicsLayer {
        val radiusPx = radius.toPx()
        if (radiusPx > 0f) {
            renderEffect = RenderEffect.createBlurEffect(
                radiusPx,
                radiusPx,
                Shader.TileMode.CLAMP,
            ).asComposeRenderEffect()
        }
    }
} else {
    this
}

/**
 * Checks whether the current Android runtime supports [RenderEffect] blur (API 31+).
 */
@VisibleForTesting
internal fun isBlurSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
