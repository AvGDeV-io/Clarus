/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.gestures.zen

import android.content.Context
import android.graphics.PointF
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ZenSwipeGestureListenerTest {

    private lateinit var context: Context
    private var backNavigated = false
    private var forwardNavigated = false
    private var tabOverviewOpened = false
    private var newTabOpened = false
    private var switchedNextTab = false
    private var switchedPreviousTab = false

    private val testActions = object : ZenGestureActions {
        override fun onNavigateBack() {
            backNavigated = true
        }

        override fun onNavigateForward() {
            forwardNavigated = true
        }

        override fun onOpenTabOverview() {
            tabOverviewOpened = true
        }

        override fun onOpenNewTab() {
            newTabOpened = true
        }

        override fun onSwitchToPreviousTab() {
            switchedPreviousTab = true
        }

        override fun onSwitchToNextTab() {
            switchedNextTab = true
        }
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        backNavigated = false
        forwardNavigated = false
        tabOverviewOpened = false
        newTabOpened = false
        switchedNextTab = false
        switchedPreviousTab = false
    }

    @Test
    fun `verify ZenGestureConfig default edgeZoneWidth is 48dp`() {
        val config = ZenGestureConfig()
        assertEquals(48.dp, config.edgeZoneWidth)
    }

    @Test
    fun `touch within widened 48dp edge zone starts back gesture candidate and commits`() {
        val density = context.resources.displayMetrics.density
        val config = ZenGestureConfig(edgeZoneWidth = 48.dp, edgeSwipeCommitDistance = 64.dp)
        val listener = ZenSwipeGestureListener(
            context = context,
            actions = testActions,
            config = config,
        )

        // Touch at 30dp (inside the 48dp widened zone, beyond the old 24dp limit)
        val startX = 30f * density
        val startY = 200f * density

        // Start horizontal swipe to the right
        val started = listener.onSwipeStarted(
            start = PointF(startX, startY),
            next = PointF(startX + 20f * density, startY),
        )
        assertTrue("Gesture starting at 30dp within 48dp edge zone should be captured", started)

        // Drag beyond commit distance (e.g. 70dp)
        listener.onSwipeUpdate(-70f * density, 0f)

        // Finish swipe
        listener.onSwipeFinished(velocityX = 0f, velocityY = 0f)
        assertTrue("Back navigation should have been dispatched upon reaching commit threshold", backNavigated)
    }

    @Test
    fun `touch outside edge zone passes through to page content`() {
        val density = context.resources.displayMetrics.density
        val config = ZenGestureConfig(edgeZoneWidth = 48.dp)
        val listener = ZenSwipeGestureListener(
            context = context,
            actions = testActions,
            config = config,
        )

        // Touch at 60dp (outside the 48dp zone)
        val startX = 60f * density
        val startY = 200f * density

        val started = listener.onSwipeStarted(
            start = PointF(startX, startY),
            next = PointF(startX + 20f * density, startY),
        )
        assertFalse("Touches outside the edge zone should not be captured", started)
    }

    @Test
    fun `swipe in edge zone under commit threshold does not dispatch back navigation`() {
        val density = context.resources.displayMetrics.density
        val config = ZenGestureConfig(edgeZoneWidth = 48.dp, edgeSwipeCommitDistance = 64.dp)
        val listener = ZenSwipeGestureListener(
            context = context,
            actions = testActions,
            config = config,
        )

        val startX = 20f * density
        val startY = 200f * density

        val started = listener.onSwipeStarted(
            start = PointF(startX, startY),
            next = PointF(startX + 10f * density, startY),
        )
        assertTrue(started)

        // Drag only 20dp (under 64dp commit threshold)
        listener.onSwipeUpdate(-20f * density, 0f)
        listener.onSwipeFinished(velocityX = 0f, velocityY = 0f)

        assertFalse("Back navigation should NOT be dispatched if commit threshold is not reached", backNavigated)
    }

    @Test
    fun `touch within right edge zone starts forward gesture and commits`() {
        val density = context.resources.displayMetrics.density
        val screenWidth = context.resources.displayMetrics.widthPixels
        val config = ZenGestureConfig(edgeZoneWidth = 48.dp, edgeSwipeCommitDistance = 64.dp)
        val listener = ZenSwipeGestureListener(
            context = context,
            actions = testActions,
            config = config,
        )

        // Touch at screenWidth - 20dp (inside the right edge zone)
        val startX = screenWidth - (20f * density)
        val startY = 200f * density

        val started = listener.onSwipeStarted(
            start = PointF(startX, startY),
            next = PointF(startX - 20f * density, startY),
        )
        assertTrue("Swipe starting on right edge should be captured", started)

        // Drag left by 70dp (commit forward)
        listener.onSwipeUpdate(70f * density, 0f)
        listener.onSwipeFinished(velocityX = 0f, velocityY = 0f)

        assertTrue("Forward navigation should have been dispatched", forwardNavigated)
    }
}
