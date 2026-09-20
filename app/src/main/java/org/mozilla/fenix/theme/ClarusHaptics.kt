/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.theme

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import kotlinx.coroutines.launch

/**
 * Unified haptic feedback and tactile micro-interaction controller for Clarus.
 */
object ClarusHaptics {

    /**
     * Subtle key-press tick for minor interactions (tab switch, slider change, scroll clicks).
     */
    fun performTick(view: View?) {
        if (view == null) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_PRESS)
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
    }

    /**
     * Reassuring tactile click for primary button presses, switch toggles, and popup actions.
     */
    fun performClick(view: View?) {
        if (view == null) return
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    /**
     * Positive confirmation pulse for incognito mode toggles, gesture commitments, and key actions.
     */
    fun performCommit(view: View?) {
        if (view == null) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
    }
}

/**
 * Compose modifier that applies a subtle, tactile spring press animation (scales down to 0.94f)
 * combined with reassuring haptic feedback on click.
 */
fun Modifier.clarusPressAnimation(
    scaleDown: Float = 0.94f,
    onClick: (() -> Unit)? = null,
): Modifier = composed {
    val scale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    val view = LocalView.current

    val pressModifier = Modifier
        .graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
        }
        .pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                scope.launch {
                    scale.animateTo(
                        targetValue = scaleDown,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow,
                        ),
                    )
                }

                val up = waitForUpOrCancellation()
                scope.launch {
                    scale.animateTo(
                        targetValue = 1f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium,
                        ),
                    )
                }
            }
        }

    if (onClick != null) {
        pressModifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
        ) {
            ClarusHaptics.performClick(view)
            onClick()
        }
    } else {
        pressModifier
    }
}

private suspend fun androidx.compose.ui.input.pointer.AwaitPointerEventScope.waitForUpOrCancellation(): Boolean {
    while (true) {
        val event = awaitPointerEvent()
        if (event.changes.all { !it.pressed }) {
            return true
        }
    }
}
