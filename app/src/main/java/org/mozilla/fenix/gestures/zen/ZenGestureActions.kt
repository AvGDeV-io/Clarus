/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.gestures.zen

/**
 * Interface representing navigation callbacks triggered by Zen gestures.
 *
 * Implementations translate detected touch gestures into calls to the EXISTING
 * navigation actions/dispatchers already used by the back/forward buttons,
 * tab switcher, and toolbar — without duplicating any navigation logic.
 */
interface ZenGestureActions {

    /**
     * Triggered by an inward horizontal swipe from the left edge of the screen.
     * Navigates back in browser history (or pops current navigation stack).
     */
    fun onNavigateBack()

    /**
     * Triggered by an inward horizontal swipe from the right edge of the screen.
     * Navigates forward in browser history.
     */
    fun onNavigateForward()

    /**
     * Triggered by a vertical swipe up on the toolbar or bottom navigation bar.
     * Opens the tab overview / tabs tray.
     */
    fun onOpenTabOverview()

    /**
     * Triggered by a deliberate downward pull from the top of the page when already at scroll-top.
     * Opens a new tab or navigates to the home/search screen.
     */
    fun onOpenNewTab()

    /**
     * Triggered by a left-to-right horizontal swipe on the toolbar itself.
     * Directly switches to the previous open tab in the active browsing mode.
     */
    fun onSwitchToPreviousTab()

    /**
     * Triggered by a right-to-left horizontal swipe on the toolbar itself.
     * Directly switches to the next open tab in the active browsing mode.
     */
    fun onSwitchToNextTab()

    companion object {
        /**
         * No-op implementation for previewing or testing without active navigation components.
         */
        val NoOp = object : ZenGestureActions {
            override fun onNavigateBack() = Unit
            override fun onNavigateForward() = Unit
            override fun onOpenTabOverview() = Unit
            override fun onOpenNewTab() = Unit
            override fun onSwitchToPreviousTab() = Unit
            override fun onSwitchToNextTab() = Unit
        }
    }
}
