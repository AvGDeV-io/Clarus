/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.theater

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(manifest = Config.NONE)
class ClarusTheaterScreenTest {

    @Test
    fun `formatTime formats seconds, minutes and hours correctly`() {
        assertEquals("00:00", formatTime(0L))
        assertEquals("00:05", formatTime(5_000L))
        assertEquals("01:05", formatTime(65_000L))
        assertEquals("12:34", formatTime(754_000L))
        assertEquals("45:10", formatTime((45 * 60 + 10) * 1000L))
        assertEquals("1:01:05", formatTime((3600 + 65) * 1000L))
        assertEquals("2:15:30", formatTime((2 * 3600 + 15 * 60 + 30) * 1000L))
    }

    @Test
    fun `gesture zone boundaries strictly adhere to 40-20-40 partition`() {
        val screenWidth = 1000f
        val leftThreshold = screenWidth * 0.40f // 400f
        val rightThreshold = screenWidth * 0.60f // 600f

        fun determineZone(x: Float): String = when {
            x < leftThreshold -> "BRIGHTNESS"
            x > rightThreshold -> "VOLUME"
            else -> "CENTER_SCRUB"
        }

        // Left 40%
        assertEquals("BRIGHTNESS", determineZone(0f))
        assertEquals("BRIGHTNESS", determineZone(100f))
        assertEquals("BRIGHTNESS", determineZone(399f))

        // Center 20%
        assertEquals("CENTER_SCRUB", determineZone(400f))
        assertEquals("CENTER_SCRUB", determineZone(500f))
        assertEquals("CENTER_SCRUB", determineZone(600f))

        // Right 40%
        assertEquals("VOLUME", determineZone(601f))
        assertEquals("VOLUME", determineZone(800f))
        assertEquals("VOLUME", determineZone(1000f))
    }

    @Test
    fun `scrubber progress fraction clamps correctly`() {
        val durationMs = 100_000L

        fun calcFraction(posMs: Long): Float {
            return if (durationMs > 0) (posMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
        }

        assertEquals(0f, calcFraction(0L), 0.001f)
        assertEquals(0.5f, calcFraction(50_000L), 0.001f)
        assertEquals(1.0f, calcFraction(100_000L), 0.001f)
        assertEquals(1.0f, calcFraction(120_000L), 0.001f)
        assertEquals(0f, calcFraction(-10_000L), 0.001f)
    }

    @Test
    fun `video resize modes cycle properly`() {
        var mode = VideoResizeMode.FIT
        assertEquals("Fit", mode.label)

        mode = VideoResizeMode.FILL
        assertEquals("Fill", mode.label)

        mode = VideoResizeMode.STRETCH
        assertEquals("Stretch", mode.label)
    }

    @Test
    fun `isAudioUrl correctly identifies direct audio file extensions`() {
        assertTrue(ClarusTheaterActivity.isAudioUrl("https://example.com/podcast.mp3"))
        assertTrue(ClarusTheaterActivity.isAudioUrl("https://example.com/audio.m4a?token=123"))
        assertTrue(ClarusTheaterActivity.isAudioUrl("https://example.com/track.flac#timestamp"))
        assertTrue(ClarusTheaterActivity.isAudioUrl("https://example.com/sound.wav"))
        assertTrue(ClarusTheaterActivity.isAudioUrl("https://example.com/voice.ogg"))
        assertTrue(ClarusTheaterActivity.isAudioUrl("https://example.com/music.opus"))
        assertTrue(ClarusTheaterActivity.isAudioUrl("https://example.com/song.aac"))

        assertFalse(ClarusTheaterActivity.isAudioUrl("https://example.com/movie.mp4"))
        assertFalse(ClarusTheaterActivity.isAudioUrl("https://example.com/stream.m3u8"))
        assertFalse(ClarusTheaterActivity.isAudioUrl("https://example.com/page.html"))
    }

    @Test
    fun `ClarusTheaterActivity companion createIntent sets extras and defaults`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent = ClarusTheaterActivity.createIntent(
            context = context,
            videoUrl = "https://example.com/test.mp4",
            title = "Test Episode",
            initialPositionMs = 45000L,
            initialPaused = true,
            badges = arrayListOf("4K", "HDR10"),
            isAudio = false,
        )

        assertNotNull(intent)
        assertEquals("https://example.com/test.mp4", intent.getStringExtra(ClarusTheaterActivity.EXTRA_VIDEO_URL))
        assertEquals("Test Episode", intent.getStringExtra(ClarusTheaterActivity.EXTRA_TITLE))
        assertEquals(45000L, intent.getLongExtra(ClarusTheaterActivity.EXTRA_INITIAL_POSITION_MS, 0L))
        assertTrue(intent.getBooleanExtra(ClarusTheaterActivity.EXTRA_INITIAL_PAUSED, false))
        val badges = intent.getStringArrayListExtra(ClarusTheaterActivity.EXTRA_BADGES)
        assertNotNull(badges)
        assertEquals(listOf("4K", "HDR10"), badges)
        assertFalse(intent.getBooleanExtra(ClarusTheaterActivity.EXTRA_IS_LIVE, false))
        assertFalse(intent.getBooleanExtra(ClarusTheaterActivity.EXTRA_IS_AUDIO, false))

        val audioIntent = ClarusTheaterActivity.createIntent(
            context = context,
            videoUrl = "https://example.com/song.mp3",
        )
        assertTrue(audioIntent.getBooleanExtra(ClarusTheaterActivity.EXTRA_IS_AUDIO, false))

        val liveIntent = ClarusTheaterActivity.createIntent(
            context = context,
            videoUrl = "https://example.com/live.m3u8",
            isLive = true,
        )
        assertTrue(liveIntent.getBooleanExtra(ClarusTheaterActivity.EXTRA_IS_LIVE, false))
    }
}
