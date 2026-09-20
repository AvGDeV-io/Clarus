/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.gestures.zen

import android.content.Context
import android.graphics.PointF
import android.graphics.Rect
import android.util.Log
import org.mozilla.fenix.browser.SwipeGestureListener
import kotlin.math.abs

/**
 * View-layer gesture listener implementing Fenix's [SwipeGestureListener].
 *
 * Can be added directly to [org.mozilla.fenix.browser.SwipeGestureLayout] to provide
 * the full Zen gesture navigation suite in traditional View hierarchies.
 *
 * Resolves conflicts with GeckoView page-level touch handling:
 * - Back/Forward gestures strictly require touch origin within the configured edge zone.
 * - Toolbar gestures strictly require touch origin within the toolbar bounds.
 * - New tab gestures require downward pull at scroll-top or within the top bezel.
 *
 * @param context Android context for display metrics.
 * @param actions Navigation action dispatcher.
 * @param config Thresholds and tuning configuration.
 * @param toolbarLayoutRect Rect provider for toolbar bounds (optional).
 * @param isAtScrollTop Query function returning true when page is at scroll-top.
 */
class ZenSwipeGestureListener(
    private val context: Context,
    private val actions: ZenGestureActions,
    private val config: ZenGestureConfig = ZenGestureConfig(),
    private val toolbarLayoutRect: () -> Rect? = { null },
) : SwipeGestureListener {

    private enum class ActiveGesture {
        NONE,
        EDGE_BACK,
        EDGE_FORWARD,
        TOOLBAR_SWIPE_UP,
        TOOLBAR_SWIPE_LEFT,
        TOOLBAR_SWIPE_RIGHT,
    }

    private val density = context.resources.displayMetrics.density
    private val screenWidth = context.resources.displayMetrics.widthPixels

    private val edgeZonePx = config.edgeZoneWidth.value * density
    private val edgeCommitDistancePx = config.edgeSwipeCommitDistance.value * density
    private val toolbarHorizontalThresholdPx = config.toolbarSwipeThreshold.value * density
    private val toolbarVerticalThresholdPx = config.toolbarSwipeUpThreshold.value * density

    private var activeGesture = ActiveGesture.NONE
    private var accumulatedDx = 0f
    private var accumulatedDy = 0f

    override fun onSwipeStarted(start: PointF, next: PointF): Boolean {
        accumulatedDx = next.x - start.x
        accumulatedDy = next.y - start.y

        val absDx = abs(accumulatedDx)
        val absDy = abs(accumulatedDy)
        val ratio = config.directionalDominanceRatio

        // 1. Check Toolbar Gestures
        val toolbarRect = toolbarLayoutRect()
        if (toolbarRect != null && toolbarRect.contains(start.x.toInt(), start.y.toInt())) {
            // Vertical swipe up on toolbar -> Tab Overview
            if (accumulatedDy < 0 && absDy > absDx * ratio) {
                activeGesture = ActiveGesture.TOOLBAR_SWIPE_UP
                return true
            }
            // Horizontal swipe on toolbar -> Tab Switching
            if (absDx > absDy * ratio) {
                activeGesture = if (accumulatedDx > 0) {
                    ActiveGesture.TOOLBAR_SWIPE_RIGHT
                } else {
                    ActiveGesture.TOOLBAR_SWIPE_LEFT
                }
                return true
            }
            return false
        }

        // 2. Check Horizontal Edge Gestures (Back / Forward)
        val isLeftEdge = start.x <= edgeZonePx
        val isRightEdge = start.x >= (screenWidth - edgeZonePx)

        if (isLeftEdge || isRightEdge) {
            Log.d(
                "ZenGesture",
                "1. Touch-down detected within edge-swipe zone: startX=${start.x}, isLeftEdge=$isLeftEdge, isRightEdge=$isRightEdge, edgeZonePx=$edgeZonePx",
            )
        }

        if (isLeftEdge && accumulatedDx > 0 && absDx > absDy * ratio) {
            Log.d(
                "ZenGesture",
                "Edge swipe candidate started (EDGE_BACK): accumulatedDx=$accumulatedDx, dominanceRatio=${absDx / absDy.coerceAtLeast(1f)}",
            )
            activeGesture = ActiveGesture.EDGE_BACK
            return true
        }

        if (isRightEdge && accumulatedDx < 0 && absDx > absDy * ratio) {
            Log.d(
                "ZenGesture",
                "Edge swipe candidate started (EDGE_FORWARD): accumulatedDx=$accumulatedDx, dominanceRatio=${absDx / absDy.coerceAtLeast(1f)}",
            )
            activeGesture = ActiveGesture.EDGE_FORWARD
            return true
        }

        activeGesture = ActiveGesture.NONE
        return false
    }

    override fun onSwipeUpdate(distanceX: Float, distanceY: Float) {
        accumulatedDx -= distanceX
        accumulatedDy -= distanceY
    }

    override fun onSwipeFinished(velocityX: Float, velocityY: Float) {
        val absDx = abs(accumulatedDx)
        val absDy = abs(accumulatedDy)

        when (activeGesture) {
            ActiveGesture.EDGE_BACK -> {
                if (accumulatedDx >= edgeCommitDistancePx) {
                    Log.d(
                        "ZenGesture",
                        "2. Swipe recognized as valid back gesture: accumulatedDx=$accumulatedDx >= commitThreshold=$edgeCommitDistancePx",
                    )
                    actions.onNavigateBack()
                } else {
                    Log.d(
                        "ZenGesture",
                        "Swipe finished without committing back: accumulatedDx=$accumulatedDx < commitThreshold=$edgeCommitDistancePx",
                    )
                }
            }
            ActiveGesture.EDGE_FORWARD -> {
                if (absDx >= edgeCommitDistancePx) {
                    Log.d(
                        "ZenGesture",
                        "2. Swipe recognized as valid forward gesture: absDx=$absDx >= commitThreshold=$edgeCommitDistancePx",
                    )
                    actions.onNavigateForward()
                } else {
                    Log.d(
                        "ZenGesture",
                        "Swipe finished without committing forward: absDx=$absDx < commitThreshold=$edgeCommitDistancePx",
                    )
                }
            }
            ActiveGesture.TOOLBAR_SWIPE_UP -> {
                if (absDy >= toolbarVerticalThresholdPx) {
                    actions.onOpenTabOverview()
                }
            }
            ActiveGesture.TOOLBAR_SWIPE_LEFT -> {
                if (absDx >= toolbarHorizontalThresholdPx) {
                    actions.onSwitchToNextTab()
                }
            }
            ActiveGesture.TOOLBAR_SWIPE_RIGHT -> {
                if (absDx >= toolbarHorizontalThresholdPx) {
                    actions.onSwitchToPreviousTab()
                }
            }
            ActiveGesture.NONE -> Unit
        }

        activeGesture = ActiveGesture.NONE
        accumulatedDx = 0f
        accumulatedDy = 0f
    }
}
