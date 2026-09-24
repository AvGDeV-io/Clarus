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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.mozilla.fenix.compose.Favicon
import org.mozilla.fenix.sidus.storage.TrailNode
import org.mozilla.fenix.theme.glass.ClarusGlassSurface
import org.mozilla.fenix.theme.glass.ClarusGlassTokens

// Soft Copper: #B8754B
private val AccentCopper = Color(0xFFB8754B)
private val DustLavender = Color(0xFFAAA0D2)

/**
 * A single trail node card in the Sidus graph.
 *
 * Renders as a frosted glass card ([ClarusGlassSurface]) matching the Clarus design
 * language: Smoked Obsidian base, hairline specular top border, rounded corners.
 *
 * The [isActive] node receives a pulsing Soft Copper (#B8754B) glowing border and
 * an "Active" badge pill to distinguish it visually from its siblings.
 *
 * @param node     The [TrailNode] to display.
 * @param isActive Whether this node represents the currently active tab page.
 * @param onTapped Callback for tap interaction (Phase C).
 * @param modifier Outer modifier.
 */
@Composable
fun SidusNodeCard(
    node: TrailNode,
    isActive: Boolean,
    onTapped: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(20.dp)

    // Pulsing glow animation for the active node
    val infiniteTransition = rememberInfiniteTransition(label = "active_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse_alpha",
    )

    val borderModifier = if (isActive) {
        Modifier.border(
            width = 1.5.dp,
            color = AccentCopper.copy(alpha = pulseAlpha),
            shape = shape,
        )
    } else {
        Modifier
    }

    ClarusGlassSurface(
        modifier = modifier
            .width(130.dp)
            .height(85.dp)
            .then(borderModifier)
            .clip(shape)
            .clickable(onClick = onTapped),
        shape = shape,
        isDarkTheme = true,
        isActive = isActive,
        blurRadius = ClarusGlassTokens.Blur.Subtle,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            // Domain row: favicon badge + domain text
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FaviconBadge(url = node.url)
                Text(
                    text = extractDomain(node.url),
                    color = DustLavender,
                    fontSize = 9.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    letterSpacing = 0.04.sp,
                    modifier = Modifier.weight(1f),
                )
                if (isActive) {
                    ActiveBadge()
                }
            }

            Spacer(Modifier.height(5.dp))

            // Page title
            Text(
                text = node.title.ifBlank { node.url },
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 14.sp,
            )
        }
    }
}

/**
 * Circular favicon badge backed by [Favicon], which delegates to [BrowserIcons] for
 * real icon loading and gracefully falls back to a placeholder while the icon loads.
 */
@Composable
private fun FaviconBadge(url: String) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(14.dp)
            .clip(CircleShape)
            .border(0.5.dp, Color.White.copy(alpha = 0.25f), CircleShape),
    ) {
        Favicon(
            url = url,
            size = 14.dp,
            shape = CircleShape,
        )
    }
}

@Composable
private fun ActiveBadge() {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .border(0.5.dp, AccentCopper.copy(alpha = 0.8f), RoundedCornerShape(4.dp))
            .padding(horizontal = 4.dp, vertical = 1.dp),
    ) {
        Text(
            text = "NOW",
            color = AccentCopper,
            fontSize = 6.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.08.sp,
        )
    }
}

private fun extractDomain(url: String): String {
    return try {
        val host = java.net.URI(url).host ?: url
        host.removePrefix("www.")
    } catch (_: Exception) {
        url.substringAfter("://").substringBefore("/").removePrefix("www.")
    }
}
