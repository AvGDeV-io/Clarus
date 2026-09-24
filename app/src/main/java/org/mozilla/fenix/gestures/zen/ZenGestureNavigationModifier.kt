/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.gestures.zen

import android.util.Log
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import kotlin.math.abs

/**
 * State representing an active horizontal edge swipe.
 */
sealed class ZenEdgeSwipeState {
    object Idle : ZenEdgeSwipeState()
    data class Swiping(
        val isLeftEdge: Boolean,
        val progress: Float,
        val isCommitted: Boolean,
    ) : ZenEdgeSwipeState()
}

/**
 * State representing an active downward pull for new tab creation.
 */
sealed class ZenPullDownState {
    object Idle : ZenPullDownState()
    data class Pulling(
        val pullDistance: Float,
        val threshold: Float,
        val isCommitted: Boolean,
    ) : ZenPullDownState()
}

/**
 * Modifier detecting horizontal edge swipes originating within the configured edge zone.
 *
 * Designed to eliminate false-positive triggers inside web content carousels, maps, and sliders:
 * - Touches originating in the page body (outside edgeZoneWidth) are untouched.
 * - Enforces horizontal dominance (|dx| > ratio * |dy|) before consuming touch events.
 *
 * @param config Gesture configuration and thresholds.
 * @param hapticFeedback Optional haptic feedback controller.
 * @param onBack Callback when back gesture is committed.
 * @param onForward Callback when forward gesture is committed.
 * @param onStateChanged Real-time callback for animating Clarus visual indicators.
 */
fun Modifier.zenEdgeNavigationGestures(
    config: ZenGestureConfig,
    hapticFeedback: HapticFeedback? = null,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onStateChanged: (ZenEdgeSwipeState) -> Unit = {},
): Modifier = pointerInput(config) {
    val edgeZonePx = config.edgeZoneWidth.toPx()
    val commitDistancePx = config.edgeSwipeCommitDistance.toPx()

    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val startX = down.position.x
        val startY = down.position.y
        val isLeftEdge = startX <= edgeZonePx
        val isRightEdge = startX >= size.width - edgeZonePx

        // If touch did not originate in either edge zone, pass through to GeckoView / content
        if (!isLeftEdge && !isRightEdge) {
            return@awaitEachGesture
        }

        Log.d(
            "ZenGesture",
            "1. Touch-down detected within edge-swipe zone (Compose): startX=$startX, isLeftEdge=$isLeftEdge, isRightEdge=$isRightEdge, edgeZonePx=$edgeZonePx",
        )

        var totalDx = 0f
        var totalDy = 0f
        var gestureLocked = false
        var hasTriggeredHaptic = false

        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == down.id } ?: break

            if (!change.pressed) {
                // Gesture completed (finger lifted)
                val isCommitted = abs(totalDx) >= commitDistancePx
                if (isCommitted) {
                    if (isLeftEdge && totalDx > 0) {
                        Log.d(
                            "ZenGesture",
                            "2. Swipe recognized as valid back gesture (Compose): totalDx=$totalDx >= commitThreshold=$commitDistancePx",
                        )
                        onBack()
                    } else if (isRightEdge && totalDx < 0) {
                        Log.d(
                            "ZenGesture",
                            "2. Swipe recognized as valid forward gesture (Compose): totalDx=$totalDx >= commitThreshold=$commitDistancePx",
                        )
                        onForward()
                    }
                } else {
                    Log.d(
                        "ZenGesture",
                        "Swipe finished without committing (Compose): totalDx=$totalDx, commitThreshold=$commitDistancePx",
                    )
                }
                onStateChanged(ZenEdgeSwipeState.Idle)
                break
            }

            val dragDelta = change.positionChange()
            totalDx += dragDelta.x
            totalDy += dragDelta.y

            // Directional gating: Check if horizontal movement dominates vertical movement
            val absX = abs(totalDx)
            val absY = abs(totalDy)

            if (!gestureLocked) {
                if (absX > 10f || absY > 10f) {
                    val isHorizontalDominant = absX > absY * config.directionalDominanceRatio
                    val isInwardDirection = (isLeftEdge && totalDx > 0) || (isRightEdge && totalDx < 0)
                    if (isHorizontalDominant && isInwardDirection) {
                        gestureLocked = true
                    } else if (absY > absX) {
                        // User is scrolling vertically, abort edge gesture
                        onStateChanged(ZenEdgeSwipeState.Idle)
                        break
                    }
                }
            }

            if (gestureLocked) {
                change.consume()
                val rawProgress = absX / commitDistancePx
                val isCommitted = absX >= commitDistancePx

                if (isCommitted && !hasTriggeredHaptic) {
                    if (config.hapticsEnabled) {
                        // Threshold crossed: light tick (commit fires on gesture completion)
                        hapticFeedback?.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                    hasTriggeredHaptic = true
                } else if (!isCommitted) {
                    hasTriggeredHaptic = false
                }

                onStateChanged(
                    ZenEdgeSwipeState.Swiping(
                        isLeftEdge = isLeftEdge,
                        progress = rawProgress,
                        isCommitted = isCommitted,
                    ),
                )
            }
        }
    }
}

/**
 * Modifier detecting gestures on the toolbar / navigation bar:
 * - Horizontal swipe: switches tabs directly (left-to-right -> previous tab, right-to-left -> next tab)
 * - Vertical swipe up: opens tab overview / tray
 *
 * @param config Gesture configuration and thresholds.
 * @param hapticFeedback Optional haptic feedback controller.
 * @param onSwipeUp Callback to open tab overview.
 * @param onSwipeLeft Callback for right-to-left swipe (next tab).
 * @param onSwipeRight Callback for left-to-right swipe (previous tab).
 */
fun Modifier.zenToolbarGestures(
    config: ZenGestureConfig,
    hapticFeedback: HapticFeedback? = null,
    onSwipeUp: () -> Unit,
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit,
): Modifier = pointerInput(config) {
    val horizontalThresholdPx = config.toolbarSwipeThreshold.toPx()
    val verticalThresholdPx = config.toolbarSwipeUpThreshold.toPx()

    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var totalDx = 0f
        var totalDy = 0f
        var gestureHandled = false

        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == down.id } ?: break

            if (!change.pressed) {
                break
            }

            val dragDelta = change.positionChange()
            totalDx += dragDelta.x
            totalDy += dragDelta.y

            val absX = abs(totalDx)
            val absY = abs(totalDy)

            if (!gestureHandled) {
                // Check swipe up for tab overview
                if (totalDy < -verticalThresholdPx && absY > absX * config.directionalDominanceRatio) {
                    change.consume()
                    gestureHandled = true
                    if (config.hapticsEnabled) {
                        // Swipe-up commit → tab overview
                        hapticFeedback?.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                    onSwipeUp()
                    break
                }

                // Check horizontal swipe for tab switching
                if (absX > horizontalThresholdPx && absX > absY * config.directionalDominanceRatio) {
                    change.consume()
                    gestureHandled = true
                    if (config.hapticsEnabled) {
                        // Tab-switch settle
                        hapticFeedback?.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                    if (totalDx > 0) {
                        onSwipeRight()
                    } else {
                        onSwipeLeft()
                    }
                    break
                }
            }
        }
    }
}


