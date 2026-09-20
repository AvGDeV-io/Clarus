/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.theater

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import mozilla.components.concept.engine.webextension.Port
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ClarusTheaterFeatureTest {

    private lateinit var context: Context
    private lateinit var feature: ClarusTheaterFeature

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        feature = ClarusTheaterFeature(context)
    }

    @Test
    fun `direct MP4 stream payload is detected and marked supported`() {
        var receivedPayload: ClarusVideoPayload? = null
        feature.onFullscreenRequestedListener = { receivedPayload = it }

        val json = JSONObject().apply {
            put("action", "FULLSCREEN_ENTER")
            put("isSupported", true)
            put("reason", "DIRECT_HTTP_STREAM")
            put("trigger", "FULLSCREEN_API")
            put("src", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4")
            put("currentTime", 12.5)
            put("duration", 596.0)
            put("paused", false)
            put("muted", false)
            put("volume", 1.0)
            put("videoWidth", 1920)
            put("videoHeight", 1080)
            put("title", "Big Buck Bunny")
            put("poster", "https://example.com/poster.jpg")
            put("qualityBadge", "1080p FHD")
            put("pageUrl", "https://example.com/video")
        }

        feature.onMessage(json, null)

        assertNotNull(receivedPayload)
        assertTrue(receivedPayload!!.isSupported)
        assertEquals("DIRECT_HTTP_STREAM", receivedPayload!!.reason)
        assertEquals("Big Buck Bunny", receivedPayload!!.title)
        assertEquals("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4", receivedPayload!!.src)
        assertEquals(12.5, receivedPayload!!.currentTime, 0.001)
        assertEquals(596.0, receivedPayload!!.duration, 0.001)
        assertFalse(receivedPayload!!.paused)
        assertEquals(1920, receivedPayload!!.videoWidth)
        assertEquals(1080, receivedPayload!!.videoHeight)
        assertEquals("1080p FHD", receivedPayload!!.qualityBadge)
    }

    @Test
    fun `MSE blob stream is detected and marked unsupported for fallback`() {
        var receivedPayload: ClarusVideoPayload? = null
        feature.onFullscreenRequestedListener = { receivedPayload = it }

        val json = JSONObject().apply {
            put("action", "FULLSCREEN_ENTER")
            put("isSupported", false)
            put("reason", "MSE_BLOB_STREAM")
            put("trigger", "FULLSCREEN_API")
            put("src", "blob:https://m.youtube.com/a1b2c3d4-e5f6")
            put("currentTime", 30.0)
            put("duration", 180.0)
            put("paused", false)
            put("muted", false)
            put("volume", 1.0)
            put("videoWidth", 1280)
            put("videoHeight", 720)
            put("title", "Sample YouTube Video")
            put("qualityBadge", "720p HD")
            put("pageUrl", "https://m.youtube.com/watch?v=12345")
        }

        feature.onMessage(json, null)

        assertNotNull(receivedPayload)
        assertFalse(receivedPayload!!.isSupported)
        assertEquals("MSE_BLOB_STREAM", receivedPayload!!.reason)
        assertTrue(receivedPayload!!.src.startsWith("blob:"))
    }

    @Test
    fun `DRM encrypted stream is detected and marked unsupported for fallback`() {
        var receivedPayload: ClarusVideoPayload? = null
        feature.onFullscreenRequestedListener = { receivedPayload = it }

        val json = JSONObject().apply {
            put("action", "FULLSCREEN_ENTER")
            put("isSupported", false)
            put("reason", "DRM_EME_ENCRYPTED")
            put("trigger", "FULLSCREEN_API")
            put("src", "https://example.com/stream.mpd")
            put("title", "Protected DRM Video")
        }

        feature.onMessage(json, null)

        assertNotNull(receivedPayload)
        assertFalse(receivedPayload!!.isSupported)
        assertEquals("DRM_EME_ENCRYPTED", receivedPayload!!.reason)
    }

    @Test
    fun `4K UHD video extracts 4K quality badge`() {
        val json = JSONObject().apply {
            put("action", "FULLSCREEN_ENTER")
            put("isSupported", true)
            put("reason", "DIRECT_HTTP_STREAM")
            put("src", "https://example.com/4k_video.mp4")
            put("videoWidth", 3840)
            put("videoHeight", 2160)
            put("qualityBadge", "4K")
            put("title", "Fjord Ep. 04 · Aurora Nocturne")
        }

        feature.onMessage(json, null)

        val payload = feature.currentPayload.value
        assertNotNull(payload)
        assertEquals("4K", payload!!.qualityBadge)
        assertEquals("Fjord Ep. 04 · Aurora Nocturne", payload.title)
    }

    @Test
    fun `fullscreen exit action clears current payload and notifies listener`() {
        // First set payload
        val enterJson = JSONObject().apply {
            put("action", "FULLSCREEN_ENTER")
            put("isSupported", true)
            put("src", "https://example.com/sample.mp4")
        }
        feature.onMessage(enterJson, null)
        assertNotNull(feature.currentPayload.value)

        // Then exit
        var exitNotified = false
        feature.onFullscreenExitedListener = { exitNotified = true }

        val exitJson = JSONObject().apply {
            put("action", "FULLSCREEN_EXIT")
        }
        feature.onMessage(exitJson, null)

        assertNull(feature.currentPayload.value)
        assertTrue(exitNotified)
    }

    @Test
    fun `seekAndSync posts correct SEEK_AND_SYNC message to connected port`() {
        var postedMessage: JSONObject? = null
        val testPort = object : Port(null) {
            override fun name(): String = "ClarusTheater"
            override fun senderUrl(): String = "https://example.com"
            override fun postMessage(message: JSONObject) {
                postedMessage = message
            }
            override fun disconnect() {}
        }

        feature.onPortConnected(testPort)
        feature.seekAndSync(42.5, paused = false)

        assertNotNull(postedMessage)
        assertEquals("SEEK_AND_SYNC", postedMessage!!.getString("action"))
        assertEquals(42.5, postedMessage!!.getDouble("currentTime"), 0.001)
        assertFalse(postedMessage!!.getBoolean("paused"))
    }

    @Test
    fun `exitWebFullscreen posts EXIT_FULLSCREEN message to connected port`() {
        var postedMessage: JSONObject? = null
        val testPort = object : Port(null) {
            override fun name(): String = "ClarusTheater"
            override fun senderUrl(): String = "https://example.com"
            override fun postMessage(message: JSONObject) {
                postedMessage = message
            }
            override fun disconnect() {}
        }

        feature.onPortConnected(testPort)
        feature.exitWebFullscreen()

        assertNotNull(postedMessage)
        assertEquals("EXIT_FULLSCREEN", postedMessage!!.getString("action"))
    }

    @Test
    fun `pauseWebVideo posts PAUSE_WEB_VIDEO message to connected port`() {
        var postedMessage: JSONObject? = null
        val testPort = object : Port(null) {
            override fun name(): String = "ClarusTheater"
            override fun senderUrl(): String = "https://example.com"
            override fun postMessage(message: JSONObject) {
                postedMessage = message
            }
            override fun disconnect() {}
        }

        feature.onPortConnected(testPort)
        feature.pauseWebVideo()

        assertNotNull(postedMessage)
        assertEquals("PAUSE_WEB_VIDEO", postedMessage!!.getString("action"))
    }

    @Test
    fun `Safari-style handoff simulation passes return position and pause state`() {
        var syncMessage: JSONObject? = null
        val testPort = object : Port(null) {
            override fun name(): String = "ClarusTheater"
            override fun senderUrl(): String = "https://example.com"
            override fun postMessage(message: JSONObject) {
                if (message.optString("action") == "SEEK_AND_SYNC") {
                    syncMessage = message
                }
            }
            override fun disconnect() {}
        }

        feature.onPortConnected(testPort)

        // Web video initially at 10.0s, user watches in Clarus Theater until 75.4s and pauses
        val returnedPositionMs = 75400L
        val returnedPaused = true
        val returnPosSec = returnedPositionMs / 1000.0

        feature.seekAndSync(returnPosSec, returnedPaused)

        assertNotNull(syncMessage)
        assertEquals("SEEK_AND_SYNC", syncMessage!!.getString("action"))
        assertEquals(75.4, syncMessage!!.getDouble("currentTime"), 0.001)
        assertTrue(syncMessage!!.getBoolean("paused"))
    }

    @Test
    fun `direct HLS manifest is marked supported and detected as live stream`() {
        val hlsJson = JSONObject().apply {
            put("action", "FULLSCREEN_ENTER")
            put("isSupported", true)
            put("reason", "DIRECT_HLS_MANIFEST")
            put("trigger", "FULLSCREEN_API")
            put("src", "https://example.com/live/master.m3u8")
            put("duration", Double.POSITIVE_INFINITY)
            put("isLive", true)
            put("title", "Live Broadcast Stream")
        }

        feature.onMessage(hlsJson, null)

        val payload = feature.currentPayload.value
        assertNotNull(payload)
        assertTrue(payload!!.isSupported)
        assertEquals("DIRECT_HLS_MANIFEST", payload.reason)
        assertTrue(payload.isLive)
        assertEquals("https://example.com/live/master.m3u8", payload.src)
    }

    @Test
    fun `custom JS player wrapper triggers element prototype request successfully`() {
        var dispatchedPayload: ClarusVideoPayload? = null
        feature.onFullscreenRequestedListener = { dispatchedPayload = it }

        val customWrapperJson = JSONObject().apply {
            put("action", "FULLSCREEN_ENTER")
            put("isSupported", true)
            put("reason", "DIRECT_HTTP_STREAM")
            put("trigger", "ELEMENT_PROTOTYPE_REQUEST")
            put("src", "https://example.com/videojs_stream.mp4")
            put("currentTime", 5.0)
            put("duration", 120.0)
            put("title", "VideoJS Custom Player")
            put("videoWidth", 1920)
            put("videoHeight", 1080)
            put("qualityBadge", "1080p FHD")
        }

        feature.onMessage(customWrapperJson, null)

        assertNotNull(dispatchedPayload)
        assertEquals("ELEMENT_PROTOTYPE_REQUEST", dispatchedPayload!!.trigger)
        assertEquals("VideoJS Custom Player", dispatchedPayload!!.title)
        assertTrue(dispatchedPayload!!.isSupported)
    }

    @Test
    fun `direct audio WAV link click payload is detected and marked supported`() {
        var receivedPayload: ClarusVideoPayload? = null
        feature.onFullscreenRequestedListener = { receivedPayload = it }

        val json = JSONObject().apply {
            put("action", "FULLSCREEN_ENTER")
            put("isSupported", true)
            put("reason", "DIRECT_AUDIO_LINK_CLICK")
            put("trigger", "LINK_CLICK")
            put("src", "https://example.com/audio/test_recording.wav")
            put("currentTime", 0.0)
            put("duration", -1.0)
            put("title", "test_recording.wav")
            put("qualityBadge", "Lossless Audio")
            put("pageUrl", "https://example.com/soundboard")
        }

        feature.onMessage(json, null)

        assertNotNull(receivedPayload)
        assertTrue(receivedPayload!!.isSupported)
        assertEquals("DIRECT_AUDIO_LINK_CLICK", receivedPayload!!.reason)
        assertEquals("LINK_CLICK", receivedPayload!!.trigger)
        assertEquals("https://example.com/audio/test_recording.wav", receivedPayload!!.src)
        assertEquals("test_recording.wav", receivedPayload!!.title)
        assertEquals("Lossless Audio", receivedPayload!!.qualityBadge)
    }

    @Test
    fun `isPlayableMedia accurately classifies WAV and other audio and video formats`() {
        // Audio URLs & extensions
        assertTrue(ClarusTheaterActivity.isAudioUrl("https://example.com/song.wav"))
        assertTrue(ClarusTheaterActivity.isAudioUrl("https://example.com/song.wave?token=123"))
        assertTrue(ClarusTheaterActivity.isAudioUrl("https://example.com/song.mp3"))
        assertTrue(ClarusTheaterActivity.isAudioUrl("https://example.com/track.flac"))
        assertTrue(ClarusTheaterActivity.isAudioUrl("https://example.com/speech.opus"))

        // Video URLs
        assertTrue(ClarusTheaterActivity.isVideoUrl("https://example.com/video.mp4"))
        assertTrue(ClarusTheaterActivity.isVideoUrl("https://example.com/clip.webm#t=10"))
        assertTrue(ClarusTheaterActivity.isVideoUrl("https://example.com/movie.mkv"))

        // Full isPlayableMedia classifier
        assertTrue(ClarusTheaterActivity.isPlayableMedia("https://example.com/track.wav"))
        assertTrue(ClarusTheaterActivity.isPlayableMedia("https://example.com/stream", fileName = "sample.wav"))
        assertTrue(ClarusTheaterActivity.isPlayableMedia("https://example.com/download?id=99", mimeType = "audio/x-wav"))
        assertTrue(ClarusTheaterActivity.isPlayableMedia("https://example.com/download?id=99", mimeType = "audio/wav"))
        assertTrue(ClarusTheaterActivity.isPlayableMedia("https://example.com/download?id=99", mimeType = "audio/wave"))
        assertTrue(ClarusTheaterActivity.isPlayableMedia("https://example.com/stream", mimeType = "video/mp4"))
        assertTrue(ClarusTheaterActivity.isPlayableMedia("https://example.com/live", mimeType = "application/x-mpegurl"))

        // Non-media
        assertFalse(ClarusTheaterActivity.isPlayableMedia("https://example.com/document.pdf", fileName = "doc.pdf", mimeType = "application/pdf"))
        assertFalse(ClarusTheaterActivity.isPlayableMedia("https://example.com/archive.zip", mimeType = "application/zip"))
    }
}
