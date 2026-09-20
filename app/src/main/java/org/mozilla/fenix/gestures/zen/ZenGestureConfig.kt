/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.gestures.zen

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Configuration options and tuning thresholds for the Zen-browser-inspired gesture navigation layer.
 *
 * All parameters are tuned to prevent conflicts with GeckoView page-level touch handling
 * (such as horizontal carousels, panning maps, and pull-to-refresh).
 *
 * @property edgeZoneWidth Width of the left and right touch zones for back/forward edge swipes.
 *                         Touches originating outside this boundary are passed through to page content.
 * @property edgeSwipeCommitDistance Minimum drag distance along the horizontal axis required to commit
 *                                   a back or forward action.
 * @property toolbarSwipeThreshold Minimum horizontal displacement on the toolbar to trigger a direct tab switch.
 * @property toolbarSwipeUpThreshold Minimum upward vertical displacement on the toolbar to open tab overview.
 * @property newTabOverscrollThreshold Downward pull distance at scroll-top required to open a new tab,
 *                                    calibrated deeper than standard pull-to-refresh (~60-80dp).
 * @property topBezelZoneHeight Height of the top bezel touch region where a pull down can initiate a new tab.
 * @property directionalDominanceRatio Ratio by which the primary gesture axis displacement must exceed the
 *                                     cross axis displacement (e.g. |dx| > ratio * |dy|) to lock into a gesture.
 * @property hapticsEnabled Whether tactile haptic feedback is triggered when a gesture crosses its commit threshold.
 * @property visualFeedbackEnabled Whether ClarusGlass translucent visual indicators (pill/chevrons) are shown.
 */
@Immutable
data class ZenGestureConfig(
    val edgeZoneWidth: Dp = 48.dp,
    val edgeSwipeCommitDistance: Dp = 64.dp,
    val toolbarSwipeThreshold: Dp = 40.dp,
    val toolbarSwipeUpThreshold: Dp = 48.dp,
    val newTabOverscrollThreshold: Dp = 140.dp,
    val topBezelZoneHeight: Dp = 48.dp,
    val directionalDominanceRatio: Float = 1.3f,
    val hapticsEnabled: Boolean = true,
    val visualFeedbackEnabled: Boolean = true,
)
