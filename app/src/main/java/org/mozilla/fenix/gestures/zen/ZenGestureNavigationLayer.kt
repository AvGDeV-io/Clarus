/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.gestures.zen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.mozilla.fenix.theme.glass.ClarusGlassTokens

/**
 * Dedicated Compose wrapper providing Zen-browser-inspired gesture navigation.
 *
 * Translates edge swipes (Back / Forward), deep pulldowns (New Tab), and toolbar
 * swipes (Tab Tray & Direct Tab Switching) into calls to existing navigation actions.
 *
 * Overlays ClarusGlass visual feedback cues along the screen edges and top bezel
 * without obstructing underlying web content or GeckoView touch event processing.
 *
 * @param actions Navigation callbacks dispatched when gestures are committed.
 * @param modifier Root layout modifier.
 * @param config Thresholds and tuning parameters.
 * @param content The screen content being wrapped (e.g. GeckoView web surface or browser layout).
 */
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.launch

@Composable
fun ZenGestureNavigationLayer(
    actions: ZenGestureActions,
    modifier: Modifier = Modifier,
    config: ZenGestureConfig = remember { ZenGestureConfig() },
    content: @Composable () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val isDark = isSystemInDarkTheme()

    var edgeSwipeState by remember { mutableStateOf<ZenEdgeSwipeState>(ZenEdgeSwipeState.Idle) }
    var pageTransitionDirection by remember { mutableStateOf<Int?>(null) } // -1 = back, 1 = forward
    val transitionAnim = remember { Animatable(0f) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .zenEdgeNavigationGestures(
                config = config,
                hapticFeedback = haptic,
                onBack = {
                    actions.onNavigateBack()
                    scope.launch {
                        pageTransitionDirection = -1
                        transitionAnim.snapTo(0f)
                        transitionAnim.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
                        )
                        pageTransitionDirection = null
                    }
                },
                onForward = {
                    actions.onNavigateForward()
                    scope.launch {
                        pageTransitionDirection = 1
                        transitionAnim.snapTo(0f)
                        transitionAnim.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
                        )
                        pageTransitionDirection = null
                    }
                },
                onStateChanged = { state -> edgeSwipeState = state },
            ),
    ) {
        // Main browser content / surface
        content()

        // Smooth page transition scrim on edge navigation commit
        val direction = pageTransitionDirection
        if (direction != null) {
            val progress = transitionAnim.value
            val curtainColor = if (isDark) ClarusGlassTokens.Colors.DarkBase else ClarusGlassTokens.Colors.LightBase
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = if (direction == -1) {
                            (progress - 1f) * size.width
                        } else {
                            (1f - progress) * size.width
                        }
                        alpha = (1f - (progress * 0.75f)).coerceIn(0f, 1f)
                    }
                    .background(curtainColor.copy(alpha = 0.92f)),
            )
        }

        // Visual feedback overlays
        if (config.visualFeedbackEnabled) {
            when (val state = edgeSwipeState) {
                is ZenEdgeSwipeState.Swiping -> {
                    val alignment = if (state.isLeftEdge) Alignment.CenterStart else Alignment.CenterEnd
                    ZenEdgeSwipeIndicator(
                        isLeftEdge = state.isLeftEdge,
                        progress = state.progress,
                        isCommitted = state.isCommitted,
                        modifier = Modifier
                            .align(alignment)
                            .padding(
                                start = if (state.isLeftEdge) 8.dp else 0.dp,
                                end = if (!state.isLeftEdge) 8.dp else 0.dp,
                            ),
                    )
                }
                ZenEdgeSwipeState.Idle -> Unit
            }
        }
    }
}

/**
 * Preview demonstrating ZenGestureNavigationLayer with mock action callbacks.
 */
@Composable
fun ZenGestureNavigationLayerPreview() {
    var statusText by remember { mutableStateOf("Swipe left/right edge or pull down") }

    val mockActions = remember {
        object : ZenGestureActions {
            override fun onNavigateBack() {
                statusText = "Action: Navigate Back"
            }

            override fun onNavigateForward() {
                statusText = "Action: Navigate Forward"
            }

            override fun onOpenTabOverview() {
                statusText = "Action: Open Tab Overview"
            }

            override fun onOpenNewTab() {
                statusText = "Action: Open New Tab"
            }

            override fun onSwitchToPreviousTab() {
                statusText = "Action: Switch to Previous Tab"
            }

            override fun onSwitchToNextTab() {
                statusText = "Action: Switch to Next Tab"
            }
        }
    }

    ZenGestureNavigationLayer(
        actions = mockActions,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ClarusGlassTokens.Colors.DarkBase),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = statusText,
                color = ClarusGlassTokens.Colors.DarkTextPrimary,
                fontSize = 16.sp,
            )
        }
    }
}
