/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.components.toolbar.gestures

import android.content.Context
import android.graphics.PointF
import android.graphics.Rect
import android.view.View
import android.view.ViewConfiguration
import androidx.core.graphics.contains
import androidx.core.graphics.toPoint
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.navigation.NavController
import mozilla.telemetry.glean.private.NoExtras
import org.mozilla.fenix.GleanMetrics.Events
import org.mozilla.fenix.NavGraphDirections
import org.mozilla.fenix.R
import org.mozilla.fenix.browser.SwipeGestureListener
import org.mozilla.fenix.browser.browsingmode.BrowsingMode
import org.mozilla.fenix.browser.browsingmode.BrowsingMode.Normal
import org.mozilla.fenix.browser.browsingmode.BrowsingMode.Private
import org.mozilla.fenix.components.AppStore
import org.mozilla.fenix.components.toolbar.ToolbarPosition
import org.mozilla.fenix.components.toolbar.ToolbarPosition.BOTTOM
import org.mozilla.fenix.components.toolbar.ToolbarPosition.TOP
import org.mozilla.fenix.ext.getRectWithScreenLocation
import org.mozilla.fenix.ext.nav
import org.mozilla.fenix.tabstray.redux.state.Page
import kotlin.math.abs

private const val TOOLBAR_HEIGHT_MAXIMUM_SWIPE_FACTOR = 0.8f

/**
 * Toolbars (address bar + navigation bar) specific gesture handler that will
 * show the tabs tray for the appropriate swip up/down gesture.
 *
 * Supports both View-based toolbars (browser fragment) and Compose toolbars that expose
 * on-screen bounds (homepage).
 */
class ToolbarVerticalGesturesHandler(
    private val appStore: AppStore,
    private val context: Context,
    private val toolbarBounds: () -> Rect?,
    private val navBarBounds: () -> Rect?,
    private val toolbarPosition: ToolbarPosition,
    private val navController: NavController,
    private val getCurrentBrowsingMode: (() -> BrowsingMode)? = null,
    private val insetsAnchor: () -> View? = { null },
) : SwipeGestureListener {

    /**
     * View-based constructor used by the browser fragment, where toolbar/navbar are Android views.
     *
     * @param appStore The [AppStore] containing the application state.
     * @param toolbarLayout The address bar layout.
     * @param navBarLayout The navigation bar layout.
     * @param toolbarPosition Where the address bar is shown on the screen.
     * @param navController [NavController] used for navigation to the tabs tray.
     */
    constructor(
        appStore: AppStore,
        toolbarLayout: View,
        navBarLayout: View?,
        toolbarPosition: ToolbarPosition,
        navController: NavController,
        getCurrentBrowsingMode: (() -> BrowsingMode)? = null,
    ) : this(
        appStore = appStore,
        context = toolbarLayout.context,
        toolbarBounds = { toolbarLayout.getRectWithScreenLocation() },
        navBarBounds = { navBarLayout?.getRectWithScreenLocation() },
        toolbarPosition = toolbarPosition,
        navController = navController,
        getCurrentBrowsingMode = getCurrentBrowsingMode,
        insetsAnchor = { toolbarLayout },
    )

    private val scaledTouchSlop = ViewConfiguration.get(context).scaledTouchSlop * 2
    private var currentSwipeXDistance = 0f
    private var currentSwipeYDistance = 0f
    private var startTouchPoint = PointF(0f, 0f)

    override fun onSwipeStarted(
        start: PointF,
        next: PointF,
    ): Boolean {
        startTouchPoint = start
        currentSwipeXDistance = next.x - start.x
        currentSwipeYDistance = next.y - start.y

        return maybeShowTabsOnSwipe()
    }

    override fun onSwipeUpdate(distanceX: Float, distanceY: Float) {
        currentSwipeXDistance -= distanceX
        currentSwipeYDistance -= distanceY

        maybeShowTabsOnSwipe()
    }

    override fun onSwipeFinished(velocityX: Float, velocityY: Float) {
        // no-op
    }

    /**
     * Navigates to the tabs tray if a valid swipe gesture was made.
     *
     * @return true if the gesture was valid and can continue else false.
     */
    private fun maybeShowTabsOnSwipe(): Boolean {
        val currentDestinationId = navController.currentDestination?.id
        // Avoid negative side effects of the race between navigation and swipe callbacks
        val isCurrentDestinationValid =
            currentDestinationId == R.id.browserFragment || currentDestinationId == R.id.homeFragment

        @Suppress("ComplexCondition")
        if (!isCurrentDestinationValid ||
            appStore.state.searchState.isSearchActive ||
            startTouchPoint.isInSystemGestureInset() ||
            !startTouchPoint.isSwipeValid(currentSwipeXDistance, currentSwipeYDistance)
        ) {
            return false
        }

        if (isSwipeValid()) {
            Events.toolbarTabstraySwipe.record(NoExtras())

            val currentMode = getCurrentBrowsingMode?.invoke() ?: appStore.state.mode
            navController.nav(
                navController.currentDestination?.id,
                NavGraphDirections.actionGlobalTabManagementFragment(
                    page = when (currentMode) {
                        Normal -> Page.NormalTabs
                        Private -> Page.PrivateTabs
                    },
                ),
            )
            return false
        } else {
            return true
        }
    }

    /**
     * Check if a vertical swipe with the minimum accepted distance happened.
     */
    private fun isSwipeValid(): Boolean {
        val targetHeight = getTargetHeight()
        if (targetHeight <= 0) return false

        // Ensure that the minimum swipe distance is still smaller than the toolbar height.
        val maximumToolbarSwipeNeeded = targetHeight / TOOLBAR_HEIGHT_MAXIMUM_SWIPE_FACTOR
        val minimumSwipeDistance = scaledTouchSlop.coerceAtMost(maximumToolbarSwipeNeeded.toInt())

        return abs(currentSwipeYDistance) >= minimumSwipeDistance &&
            abs(currentSwipeXDistance) < minimumSwipeDistance &&
            startTouchPoint.isSwipeValid(currentSwipeXDistance, currentSwipeYDistance)
    }

    /**
     * Check if the swipe originated from the toolbar or navigation bar.
     */
    private fun PointF.isSwipeValid(distanceX: Float, distanceY: Float): Boolean {
        val isHorizontalSwipe = abs(distanceX) > abs(distanceY)
        if (isHorizontalSwipe) return false

        val isSwipeUpOverNavbar = distanceY.isSwipeUp && isInTarget(navBarBounds())
        if (isSwipeUpOverNavbar) return true

        val isToolbarSwipeDirectionValid = when (toolbarPosition) {
            TOP -> distanceY.isSwipeDown
            BOTTOM -> distanceY.isSwipeUp
        }
        return isToolbarSwipeDirectionValid && isInTarget(toolbarBounds())
    }

    /**
     * Check if the swipe started inside the bottom system gesture inset - the screen-edge region
     * the OS reserves for the "swipe up to go home/background the app" gesture when gesture-based
     * navigation is used.
     * A bottom toolbar/navbar overlaps this region, so swipes originating there
     * must be ignored to avoid mistaking the system gesture for a tabs tray swipe.
     */
    private fun PointF.isInSystemGestureInset(): Boolean {
        val anchor = insetsAnchor() ?: return false
        val bottomInsets = ViewCompat.getRootWindowInsets(anchor)
            ?.getInsets(WindowInsetsCompat.Type.systemGestures())
            ?.bottom ?: 0

        if (bottomInsets <= 0) return false

        val rootView = anchor.rootView
        val screenBottom = IntArray(2).apply { rootView.getLocationOnScreen(this) }[1] + rootView.height
        return y >= screenBottom - bottomInsets
    }

    private fun getTargetHeight(): Int {
        val navBar = navBarBounds()
        return when ((navBar?.height() ?: 0) > 0) {
            true -> navBar!!.height()
            else -> toolbarBounds()?.height() ?: 0
        }
    }

    private fun PointF.isInTarget(target: Rect?) =
        target?.contains(toPoint()) == true

    private val Float.isSwipeUp
        get() = this < 0f

    private val Float.isSwipeDown
        get() = this > 0f
}
