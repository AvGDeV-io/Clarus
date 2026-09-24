/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.home.toolbar

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import org.mozilla.fenix.R

/**
 * Returns the wallpaper and browsing mode derived colors for home content.
 *
 * Surface fills are forced transparent so [org.mozilla.fenix.theme.glass.ClarusGlassSurface]
 * owns the floating plate; only content colors (onSurface, outlines) are themed here.
 *
 * @param isPrivateMode Whether private browsing is enabled.
 * @param shouldUseEdgeToEdgeColors Whether the edge-to-edge wallpaper colors should be used.
 */
@Composable
fun homepageToolbarColors(
    isPrivateMode: Boolean,
    shouldUseEdgeToEdgeColors: Boolean,
): ColorScheme {
    val colors = MaterialTheme.colorScheme

    return when {
        isPrivateMode -> colors.copy(
            surface = Color.Transparent,
            surfaceContainerHighest = Color.Transparent,
            onSurface = Color(0xFFFEF3C7),
            onSurfaceVariant = Color(0xFFF59E0B),
            outline = Color(0x40F59E0B),
            outlineVariant = Color(0xFFF59E0B),
        )

        shouldUseEdgeToEdgeColors -> colors.copy(
            surface = Color.Transparent,
            surfaceContainerHighest = Color.Transparent,
            outlineVariant = colorResource(R.color.homepage_tab_edge_to_edge_toolbar_outline),
        )

        else -> colors.copy(
            surface = Color.Transparent,
            surfaceContainerHighest = Color.Transparent,
        )
    }
}

/**
 * Returns the background color for the clipboard suggestion bar.
 *
 * Toolbar chrome surfaces are transparent (ClarusGlassSurface owns the plate), so the clipboard
 * bar always paints its own opaque fill to stay legible over wallpaper or page content.
 *
 * @param shouldUseEdgeToEdgeColors Whether the edge-to-edge wallpaper colors should be used.
 * @param isPrivateMode Whether private browsing is enabled.
 * @return The [Color] to be used for the clipboard bar background.
 */
@Composable
@ReadOnlyComposable
fun edgeToEdgeClipboardBarBackground(
    shouldUseEdgeToEdgeColors: Boolean,
    isPrivateMode: Boolean,
): Color =
    if (isPrivateMode) {
        Color(0xFF16141D).copy(alpha = 0.90f)
    } else {
        colorResource(R.color.fx_mobile_surface)
    }
