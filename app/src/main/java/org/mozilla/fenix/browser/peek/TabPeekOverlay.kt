/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.browser.peek

import android.widget.FrameLayout
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.mozilla.fenix.R
import mozilla.components.ui.icons.R as iconsR
import org.mozilla.fenix.theme.FirefoxTheme
import org.mozilla.fenix.theme.Theme
import org.mozilla.fenix.theme.glass.ClarusGlassSurface
import org.mozilla.fenix.theme.glass.ClarusGlassTokens

/**
 * Large floating glass peek card: compact header, live preview, pill actions.
 * Gestures are handled at the engine/overlay touch level so release and drag work cleanly.
 */
@Composable
fun TabPeekOverlay(
    state: TabPeekUiState,
    theme: Theme,
    previewFactory: ((FrameLayout) -> Unit)? = null,
    onAction: (String) -> Unit = {},
    onDismiss: () -> Unit = {},
    onCommit: () -> Unit = {},
    onOpenPrivate: () -> Unit = {},
    onDrag: (upPx: Float, leftPx: Float) -> Unit = { _, _ -> },
    onActionBounds: (id: String, bounds: Rect) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    val peeking = state as? TabPeekUiState.Peeking ?: return
    val isDark = theme == Theme.Dark || theme == Theme.Private
    val isPrivate = theme == Theme.Private

    val scale by animateFloatAsState(
        targetValue = 1f + peeking.commitProgress * 0.06f + peeking.privateProgress * 0.04f,
        tween(100),
        label = "PeekScale",
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.18f))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.Center,
    ) {
        ClarusGlassSurface(
            modifier = Modifier
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = {},
                )
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationY = -peeking.dragUpPx * 0.25f
                    translationX = -peeking.dragLeftPx * 0.25f
                }
                .padding(horizontal = 16.dp, vertical = 28.dp)
                .fillMaxWidth()
                .height(520.dp),
            shape = ClarusGlassTokens.Shapes.Card,
            elevation = ClarusGlassTokens.Elevation.Level3,
            isDarkTheme = isDark,
            isPrivate = isPrivate,
            isActive = peeking.commitProgress > 0.05f || peeking.privateProgress > 0.05f,
            blurRadius = ClarusGlassTokens.Blur.Heavy,
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 12.dp)) {
                // Header: icon + title + URL + close
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(
                                if (isPrivate) Color(0xFFF59E0B).copy(alpha = 0.25f)
                                else Color.White.copy(alpha = 0.12f),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_clarus_private_mode),
                            contentDescription = null,
                            tint = if (isPrivate) Color(0xFFF59E0B) else Color(0xFFF59E0B),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 10.dp),
                    ) {
                        Text(
                            text = peeking.content.title?.takeIf { it.isNotBlank() }
                                ?: peeking.content.url,
                            style = FirefoxTheme.typography.subtitle1,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = peeking.content.url,
                            style = FirefoxTheme.typography.caption,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            painter = painterResource(iconsR.drawable.mozac_ic_cross_24),
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Preview viewport
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black.copy(alpha = 0.25f)),
                ) {
                    if (previewFactory != null) {
                        AndroidView(
                            factory = { ctx -> FrameLayout(ctx).also { previewFactory(it) } },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    if (!peeking.previewReady) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.50f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                androidx.compose.material3.CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = Color(0xFFF59E0B),
                                    strokeWidth = 2.5.dp,
                                )
                                Text(
                                    text = "Loading preview...",
                                    style = FirefoxTheme.typography.caption,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }

                if (peeking.commitProgress >= 1f) {
                    Text(
                        text = "Release to open in new tab",
                        style = FirefoxTheme.typography.caption,
                        color = Color(0xFFF59E0B),
                        modifier = Modifier.padding(top = 8.dp),
                    )
                } else if (peeking.commitProgress > 0.25f && peeking.dragUpPx > peeking.dragLeftPx) {
                    Text(
                        text = "Drag up to open in new tab",
                        style = FirefoxTheme.typography.caption,
                        color = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                if (peeking.privateProgress >= 1f) {
                    Text(
                        text = "Release to open in private tab",
                        style = FirefoxTheme.typography.caption,
                        color = Color(0xFFF59E0B),
                        modifier = Modifier.padding(top = 8.dp),
                    )
                } else if (peeking.privateProgress > 0.25f && peeking.dragLeftPx > peeking.dragUpPx) {
                    Text(
                        text = "Drag left to open in private tab",
                        style = FirefoxTheme.typography.caption,
                        color = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }

                Spacer(Modifier.height(14.dp))

                // Pill actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    peeking.actions.forEach { action ->
                        PeekActionButton(
                            id = action.id,
                            label = action.label,
                            isPrimary = action.isPrimary,
                            isDark = isDark,
                            isPrivate = isPrivate,
                            onClick = { onAction(action.id) },
                            onBoundsChanged = onActionBounds,
                            modifier = Modifier.weight(if (action.isPrimary) 1.35f else 1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PeekActionButton(
    id: String,
    label: String,
    isPrimary: Boolean,
    isDark: Boolean,
    isPrivate: Boolean,
    onClick: () -> Unit,
    onBoundsChanged: ((String, Rect) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    ClarusGlassSurface(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .onGloballyPositioned { coords ->
                onBoundsChanged?.invoke(id, coords.boundsInWindow())
            }
            .clickable(onClick = onClick),
        shape = ClarusGlassTokens.Shapes.Pill,
        elevation = ClarusGlassTokens.Elevation.Level2,
        isDarkTheme = isDark,
        isPrivate = isPrivate,
        isActive = isPrimary,
        blurRadius = ClarusGlassTokens.Blur.Subtle,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = if (isPrimary) {
                        Brush.verticalGradient(
                            listOf(
                                Color(0xFFF59E0B).copy(alpha = 0.92f),
                                Color(0xFFD97706).copy(alpha = 0.88f),
                            ),
                        )
                    } else {
                        Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.10f),
                                Color.White.copy(alpha = 0.06f),
                            ),
                        )
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = FirefoxTheme.typography.button,
                color = if (isPrimary) Color(0xFF1C1410) else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
