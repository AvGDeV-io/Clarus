/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.theme.glass

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Design tokens for the Clarus "Serene Tactile Glass" design system.
 *
 * Implements tactile glassmorphism inspired by Safari liquid-glass and Zen-browser
 * translucency, calibrated for mobile ergonomics and ambient visual depth.
 */
object ClarusGlassTokens {

    /**
     * Blur radii for RenderEffect backdrop filters (API 31+).
     */
    object Blur {
        /** Standard blur for floating omni-bars, navigation docks, and tab cards. */
        val Standard: Dp = 20.dp

        /** Deep blur for private browsing sanctuary and expandable sheets. */
        val Deep: Dp = 28.dp

        /** Heavy blur for modal overlays, theater backdrops, and dialogs. */
        val Heavy: Dp = 32.dp

        /** Minimal subtle blur for compact inline chips. */
        val Subtle: Dp = 12.dp
    }

    /**
     * Calibrated translucency alpha values per theme and state.
     */
    object Alpha {
        object Light {
            /** Resting glass plate opacity when RenderEffect blur is active. */
            const val Resting: Float = 0.78f

            /** Focused or active tab/input glass opacity. */
            const val Active: Float = 0.85f

            /**
             * Graceful fallback opacity for Android versions prior to API 31 (minSdk 26..30)
             * where RenderEffect is unavailable. A slightly higher opacity maintains
             * high contrast and legibility over underlying web content.
             */
            const val Fallback: Float = 0.92f
        }

        object Dark {
            /** Resting smoky obsidian glass plate opacity with active blur. */
            const val Resting: Float = 0.72f

            /** Focused or active tab/input glass opacity in dark mode. */
            const val Active: Float = 0.80f

            /** Fallback opacity for API < 31 in dark mode. */
            const val Fallback: Float = 0.90f
        }

        object Private {
            /** Obsidian sanctuary private browsing surface opacity. */
            const val Resting: Float = 0.82f

            /** Active state opacity in private browsing mode. */
            const val Active: Float = 0.88f

            /** Fallback opacity for API < 31 in private mode. */
            const val Fallback: Float = 0.94f
        }
    }

    /**
     * Color palette matching the Stitch "Serene Tactile Glass" specification.
     */
    object Colors {
        // Base Glass Plates
        val LightBase: Color = Color(0xFFFCFAF6) // Porcelain
        val LightCanvas: Color = Color(0xFFF7F3EC) // Warm Ivory
        val LightSubstrate: Color = Color(0xFFEAE1D4) // Soft Sand

        val DarkBase: Color = Color(0xFF1C1C22) // Smoked Obsidian
        val DarkDeep: Color = Color(0xFF131319) // Deep Obsidian
        val DarkElevated: Color = Color(0xFF282830) // Elevated Obsidian Container

        // Inks & Typography
        val InkEspresso: Color = Color(0xFF24201F) // Primary high-emphasis ink
        val InkCharcoal: Color = Color(0xFF302B2A) // Secondary meta ink
        val InkMuted: Color = Color(0xFF7F7572) // Muted captions / hints

        val DarkTextPrimary: Color = Color(0xFFF8FAFC)
        val DarkTextSecondary: Color = Color(0xFF94A3B8)

        // Restrained Accents
        val AccentCopper: Color = Color(0xFFB8754B) // Soft Copper: Active tabs & key focus
        val AccentLavender: Color = Color(0xFFAAA0D2) // Dusty Lavender: Spatial ambient highlight
        val AccentSaffron: Color = Color(0xFFD5A24A) // Muted Saffron: Bookmarks / Reading mode
        val AccentCoral: Color = Color(0xFFC9786A) // Muted Coral: Critical alerts

        // Private Sanctuary Accents
        val PrivateAmber: Color = Color(0xFFF59E0B) // Radiant Amber: Ephemeral private indicator
        val PrivateViolet: Color = Color(0xFF8B5CF6) // Velvet Violet: Private browsing aura
    }

    /**
     * Hairline specular highlight borders simulating physical lens refraction.
     */
    object Border {
        /** Resting specular stroke width (thin light-catching edge, not a heavy border). */
        val Width: Dp = 0.75.dp

        /** Active / focused highlight stroke width. */
        val ActiveWidth: Dp = 1.25.dp

        /**
         * Top-down directional light-catching stroke for light mode.
         * Simulates natural downward lighting: 65% white highlight on top, subtle 8% dark shadow at bottom.
         */
        fun restingLightBorderBrush(): Brush = Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.65f),
                Color.White.copy(alpha = 0.25f),
                Colors.InkEspresso.copy(alpha = 0.08f),
            ),
        )

        /**
         * Subtle specular stroke for dark obsidian mode.
         */
        fun restingDarkBorderBrush(): Brush = Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.22f),
                Color.White.copy(alpha = 0.10f),
                Color.White.copy(alpha = 0.04f),
            ),
        )

        /**
         * Subtle amber & violet specular highlight for private obsidian mode.
         */
        fun restingPrivateBorderBrush(): Brush = Brush.verticalGradient(
            colors = listOf(
                Colors.PrivateAmber.copy(alpha = 0.40f),
                Colors.PrivateViolet.copy(alpha = 0.22f),
                Colors.PrivateAmber.copy(alpha = 0.10f),
            ),
        )

        /**
         * Active / focused rim highlight brush featuring Soft Copper (#B8754B).
         */
        fun activeBorderBrush(isDark: Boolean = false): Brush {
            val topAlpha = if (isDark) 0.95f else 0.85f
            val bottomAlpha = if (isDark) 0.45f else 0.35f
            return Brush.verticalGradient(
                colors = listOf(
                    Colors.AccentCopper.copy(alpha = topAlpha),
                    Colors.AccentCopper.copy(alpha = bottomAlpha),
                ),
            )
        }

        /**
         * Private browsing active highlight brush featuring Radiant Amber (#F59E0B).
         */
        fun activePrivateBorderBrush(): Brush = Brush.verticalGradient(
            colors = listOf(
                Colors.PrivateAmber.copy(alpha = 0.90f),
                Colors.PrivateAmber.copy(alpha = 0.40f),
            ),
        )

        /** Subtle ambient rim glow color when active. */
        val ActiveGlowLight: Color = Colors.AccentCopper.copy(alpha = 0.20f)
        val ActiveGlowDark: Color = Colors.AccentCopper.copy(alpha = 0.30f)
    }

    /**
     * Multi-level elevation and drop shadow system.
     */
    object Elevation {
        /** Level 1: Resting chips, category badges, inline cards. */
        val Level1: Dp = 2.dp

        /** Level 2: Floating omni-bar, bottom navigation dock, action sheets. */
        val Level2: Dp = 8.dp

        /** Level 3: Modal sheets, context menus, media HUD overlays. */
        val Level3: Dp = 16.dp

        // Ambient and spot shadow colors
        val AmbientShadowLight: Color = Colors.InkEspresso.copy(alpha = 0.08f)
        val SpotShadowLight: Color = Colors.InkEspresso.copy(alpha = 0.04f)

        val AmbientShadowDark: Color = Color.Black.copy(alpha = 0.45f)
        val SpotShadowDark: Color = Color.Black.copy(alpha = 0.25f)
    }

    /**
     * Curvature and squircle geometry.
     */
    object Shapes {
        /** Continuous organic pill geometry for floating docks and action capsules. */
        val Pill: Shape = RoundedCornerShape(percent = 50)

        /** Rounded cards and tab tiles matching Android hardware device radials. */
        val Card: Shape = RoundedCornerShape(20.dp)

        /** Generous containers and bottom sheets. */
        val Container: Shape = RoundedCornerShape(24.dp)

        /** Bottom sheet modal surfaces anchored to bottom viewport. */
        val BottomSheet: Shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    }

    /**
     * Tactile specular glass switch / toggle design tokens.
     */
    object Switch {
        val CheckedThumb: Color = Colors.LightBase
        val CheckedTrack: Color = Colors.AccentCopper
        val CheckedBorder: Color = Colors.PrivateAmber
        val UncheckedThumb: Color = Colors.LightBase
        val UncheckedTrackLight: Color = Colors.InkEspresso.copy(alpha = 0.08f)
        val UncheckedTrackDark: Color = Color.White.copy(alpha = 0.12f)
        val UncheckedBorderLight: Color = Colors.InkEspresso.copy(alpha = 0.20f)
        val UncheckedBorderDark: Color = Color.White.copy(alpha = 0.28f)
    }
}
