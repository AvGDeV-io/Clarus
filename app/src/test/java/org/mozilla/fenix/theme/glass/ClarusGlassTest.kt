/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.theme.glass

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class ClarusGlassTest {

    @Test
    fun `verify ClarusGlassTokens blur values match Stitch specification`() {
        assertEquals(20.dp, ClarusGlassTokens.Blur.Standard)
        assertEquals(28.dp, ClarusGlassTokens.Blur.Deep)
        assertEquals(32.dp, ClarusGlassTokens.Blur.Heavy)
        assertEquals(12.dp, ClarusGlassTokens.Blur.Subtle)
    }

    @Test
    fun `verify ClarusGlassTokens alpha values per light and dark theme`() {
        // Light theme
        assertEquals(0.78f, ClarusGlassTokens.Alpha.Light.Resting, 0.001f)
        assertEquals(0.85f, ClarusGlassTokens.Alpha.Light.Active, 0.001f)
        assertEquals(0.92f, ClarusGlassTokens.Alpha.Light.Fallback, 0.001f)

        // Dark theme
        assertEquals(0.72f, ClarusGlassTokens.Alpha.Dark.Resting, 0.001f)
        assertEquals(0.80f, ClarusGlassTokens.Alpha.Dark.Active, 0.001f)
        assertEquals(0.90f, ClarusGlassTokens.Alpha.Dark.Fallback, 0.001f)

        // Private theme
        assertEquals(0.82f, ClarusGlassTokens.Alpha.Private.Resting, 0.001f)
        assertEquals(0.88f, ClarusGlassTokens.Alpha.Private.Active, 0.001f)
        assertEquals(0.94f, ClarusGlassTokens.Alpha.Private.Fallback, 0.001f)
    }

    @Test
    fun `verify border highlights and elevations`() {
        assertEquals(0.75.dp, ClarusGlassTokens.Border.Width)
        assertEquals(1.25.dp, ClarusGlassTokens.Border.ActiveWidth)
        assertNotNull(ClarusGlassTokens.Border.restingLightBorderBrush())
        assertNotNull(ClarusGlassTokens.Border.restingDarkBorderBrush())
        assertNotNull(ClarusGlassTokens.Border.activeBorderBrush(isDark = false))
        assertNotNull(ClarusGlassTokens.Border.activeBorderBrush(isDark = true))
        assertNotNull(ClarusGlassTokens.Border.activePrivateBorderBrush())

        assertEquals(2.dp, ClarusGlassTokens.Elevation.Level1)
        assertEquals(8.dp, ClarusGlassTokens.Elevation.Level2)
        assertEquals(16.dp, ClarusGlassTokens.Elevation.Level3)
    }

    @Test
    @Config(sdk = [28])
    fun `on API 28 minSdk runtime isBlurSupported returns false`() {
        assertFalse(isBlurSupported())
    }

    @Test
    @Config(sdk = [31])
    fun `on API 31 runtime isBlurSupported returns true`() {
        assertTrue(isBlurSupported())
    }
}
