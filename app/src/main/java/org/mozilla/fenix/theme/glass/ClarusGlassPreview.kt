/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.theme.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.mozilla.fenix.theme.FirefoxTheme
import org.mozilla.fenix.theme.Theme

/**
 * Sample browser component mock for previews: Floating Omni-Bar.
 */
@Composable
private fun SampleOmniBar(
    domain: String = "clarus.design",
    isSecure: Boolean = true,
    isActive: Boolean = false,
    isDarkTheme: Boolean = false,
    forceFallback: Boolean = false,
) {
    val textColor = if (isDarkTheme) ClarusGlassTokens.Colors.DarkTextPrimary else ClarusGlassTokens.Colors.InkEspresso
    val metaColor = if (isDarkTheme) ClarusGlassTokens.Colors.DarkTextSecondary else ClarusGlassTokens.Colors.InkMuted
    val dotColor = if (isSecure) Color(0xFF10B981) else ClarusGlassTokens.Colors.AccentCoral

    ClarusGlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp),
        shape = ClarusGlassTokens.Shapes.Pill,
        elevation = ClarusGlassTokens.Elevation.Level2,
        isDarkTheme = isDarkTheme,
        isActive = isActive,
        forceFallback = forceFallback,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // Leading security indicator
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(dotColor),
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = domain,
                    color = textColor,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            // Trailing status badge
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(
                        if (isActive) {
                            ClarusGlassTokens.Colors.AccentCopper.copy(alpha = 0.20f)
                        } else {
                            if (isDarkTheme) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f)
                        },
                    )
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(
                    text = if (isActive) "ACTIVE" else "REST",
                    color = if (isActive) ClarusGlassTokens.Colors.AccentCopper else metaColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                )
            }
        }
    }
}

/**
 * Sample browser component mock for previews: Active Tab Card.
 */
@Composable
private fun SampleTabCard(
    title: String = "Nordic Architecture & Glass",
    domain: String = "clarus.design/focus",
    isActive: Boolean = true,
    isDarkTheme: Boolean = false,
    forceFallback: Boolean = false,
) {
    val textColor = if (isDarkTheme) ClarusGlassTokens.Colors.DarkTextPrimary else ClarusGlassTokens.Colors.InkEspresso
    val metaColor = if (isDarkTheme) ClarusGlassTokens.Colors.DarkTextSecondary else ClarusGlassTokens.Colors.InkMuted

    ClarusGlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp),
        shape = ClarusGlassTokens.Shapes.Card,
        elevation = if (isActive) ClarusGlassTokens.Elevation.Level2 else ClarusGlassTokens.Elevation.Level1,
        isDarkTheme = isDarkTheme,
        isActive = isActive,
        forceFallback = forceFallback,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    color = textColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                if (isActive) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(ClarusGlassTokens.Colors.AccentCopper),
                    )
                }
            }

            Text(
                text = domain,
                color = metaColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal,
            )
        }
    }
}

/**
 * Preview: Light Theme at REST (Omni-bar & Tab).
 */
@Preview(name = "Light Theme - Resting", showBackground = true, widthDp = 380, heightDp = 400)
@Composable
fun ClarusGlassLightRestingPreview() {
    FirefoxTheme(Theme.Light) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            ClarusGlassTokens.Colors.LightCanvas,
                            ClarusGlassTokens.Colors.AccentLavender.copy(alpha = 0.25f),
                            ClarusGlassTokens.Colors.LightSubstrate,
                        ),
                    ),
                )
                .padding(20.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = "Serene Tactile Glass (Resting)",
                    color = ClarusGlassTokens.Colors.InkEspresso,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
                SampleOmniBar(isActive = false, isDarkTheme = false)
                SampleTabCard(isActive = false, isDarkTheme = false)
            }
        }
    }
}

/**
 * Preview: Light Theme in ACTIVE/HIGHLIGHTED state (Soft Copper rim highlight & active badge).
 */
@Preview(name = "Light Theme - Active Highlight", showBackground = true, widthDp = 380, heightDp = 400)
@Composable
fun ClarusGlassLightActivePreview() {
    FirefoxTheme(Theme.Light) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            ClarusGlassTokens.Colors.LightCanvas,
                            ClarusGlassTokens.Colors.AccentLavender.copy(alpha = 0.30f),
                            ClarusGlassTokens.Colors.AccentSaffron.copy(alpha = 0.15f),
                        ),
                    ),
                )
                .padding(20.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = "Serene Tactile Glass (Active Highlight)",
                    color = ClarusGlassTokens.Colors.InkEspresso,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
                SampleOmniBar(isActive = true, isDarkTheme = false)
                SampleTabCard(isActive = true, isDarkTheme = false)
            }
        }
    }
}

/**
 * Preview: Dark Obsidian Theme at REST.
 */
@Preview(name = "Dark Theme - Resting", showBackground = true, widthDp = 380, heightDp = 400)
@Composable
fun ClarusGlassDarkRestingPreview() {
    FirefoxTheme(Theme.Dark) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            ClarusGlassTokens.Colors.DarkDeep,
                            Color(0xFF24202A),
                            ClarusGlassTokens.Colors.DarkBase,
                        ),
                    ),
                )
                .padding(20.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = "Obsidian Glass (Resting)",
                    color = ClarusGlassTokens.Colors.DarkTextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
                SampleOmniBar(isActive = false, isDarkTheme = true)
                SampleTabCard(isActive = false, isDarkTheme = true)
            }
        }
    }
}

/**
 * Preview: Dark Obsidian Theme in ACTIVE/HIGHLIGHTED state.
 */
@Preview(name = "Dark Theme - Active Highlight", showBackground = true, widthDp = 380, heightDp = 400)
@Composable
fun ClarusGlassDarkActivePreview() {
    FirefoxTheme(Theme.Dark) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            ClarusGlassTokens.Colors.DarkDeep,
                            Color(0xFF36335F).copy(alpha = 0.40f),
                            ClarusGlassTokens.Colors.DarkBase,
                        ),
                    ),
                )
                .padding(20.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = "Obsidian Glass (Active Highlight)",
                    color = ClarusGlassTokens.Colors.DarkTextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
                SampleOmniBar(isActive = true, isDarkTheme = true)
                SampleTabCard(isActive = true, isDarkTheme = true)
            }
        }
    }
}

/**
 * Preview: API < 31 Graceful Fallback Path (Non-blurred translucent surface).
 *
 * Explicitly exercises the fallback branch (forceFallback = true) to validate that
 * devices running Android 8.0 - 11 (minSdk 26..30) compile and render with high legibility
 * without crashes or shader failures.
 */
@Preview(name = "API < 31 Fallback (Non-blurred)", showBackground = true, widthDp = 380, heightDp = 460)
@Composable
fun ClarusGlassFallbackPreview() {
    FirefoxTheme(Theme.Light) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            ClarusGlassTokens.Colors.LightCanvas,
                            Color(0xFFDCD6CE),
                        ),
                    ),
                )
                .padding(20.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    text = "API < 31 Fallback (minSdk 26..30)",
                    color = ClarusGlassTokens.Colors.InkEspresso,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "High-opacity translucent surface without RenderEffect blur",
                    color = ClarusGlassTokens.Colors.InkMuted,
                    fontSize = 12.sp,
                )

                // Light fallback resting & active
                SampleOmniBar(isActive = false, isDarkTheme = false, forceFallback = true)
                SampleOmniBar(isActive = true, isDarkTheme = false, forceFallback = true)

                // Dark fallback
                SampleOmniBar(isActive = false, isDarkTheme = true, forceFallback = true)
            }
        }
    }
}
