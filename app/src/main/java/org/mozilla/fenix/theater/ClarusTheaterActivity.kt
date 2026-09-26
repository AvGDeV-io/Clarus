/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.theater

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Standalone Fullscreen Activity hosting Clarus Theater.
 *
 * Configured with hardware acceleration, immersive sticky system bar hiding,
 * notch/cutout edge-to-edge layout, forced landscape orientation, and native Android PiP support.
 */
class ClarusTheaterActivity : ComponentActivity() {

    private var currentPositionMs: Long = 0L
    private var isPaused: Boolean = false
    private var isInPipMode by mutableStateOf(false)
    private var currentVideoWidth: Int = 16
    private var currentVideoHeight: Int = 9

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= 34) {
            overrideActivityTransition(
                Activity.OVERRIDE_TRANSITION_OPEN,
                android.R.anim.fade_in,
                android.R.anim.fade_out,
            )
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }

        val videoUrl = intent.getStringExtra(EXTRA_VIDEO_URL)
            ?: DEFAULT_SAMPLE_URL
        val title = intent.getStringExtra(EXTRA_TITLE)
            ?: DEFAULT_SAMPLE_TITLE
        val initialPositionMs = intent.getLongExtra(EXTRA_INITIAL_POSITION_MS, 0L)
        val initialPaused = intent.getBooleanExtra(EXTRA_INITIAL_PAUSED, false)
        val badges = intent.getStringArrayListExtra(EXTRA_BADGES)
            ?: arrayListOf("4K HDR", "Dolby Atmos")
        val isLive = intent.getBooleanExtra(EXTRA_IS_LIVE, false)
        val isAudio = intent.getBooleanExtra(EXTRA_IS_AUDIO, false) || isAudioUrl(videoUrl)

        requestedOrientation = if (isAudio) {
            ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
        setupImmersiveFullscreen()

        this.currentPositionMs = initialPositionMs
        this.isPaused = initialPaused

        setContent {
            ClarusTheaterScreen(
                videoUrl = videoUrl,
                initialPositionMs = initialPositionMs,
                initialPaused = initialPaused,
                title = title,
                formatBadges = badges,
                isLive = isLive,
                isAudio = isAudio,
                isInPip = isInPipMode,
                onVideoSizeChanged = { w, h ->
                    currentVideoWidth = w
                    currentVideoHeight = h
                },
                onClose = { finalPos, finalPaused ->
                    returnPlaybackHandoff(finalPos, finalPaused)
                },
                onEnterPip = {
                    enterTheaterPip()
                },
            )
        }
    }

    private fun setupImmersiveFullscreen() {
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    fun enterTheaterPip() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val videoUrl = intent.getStringExtra(EXTRA_VIDEO_URL) ?: DEFAULT_SAMPLE_URL
                val isAudio = intent.getBooleanExtra(EXTRA_IS_AUDIO, false) || isAudioUrl(videoUrl)
                val pipRational = if (isAudio) {
                    Rational(16, 9)
                } else {
                    val safeWidth = currentVideoWidth.coerceAtLeast(1)
                    val safeHeight = currentVideoHeight.coerceAtLeast(1)
                    val ratioFloat = (safeWidth.toFloat() / safeHeight.toFloat()).coerceIn(0.42f, 2.38f)
                    Rational((ratioFloat * 1000).toInt(), 1000)
                }
                val params = PictureInPictureParams.Builder()
                    .setAspectRatio(pipRational)
                    .build()
                enterPictureInPictureMode(params)
            } catch (_: Throwable) {
                // Fallback if device does not support PiP
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Auto-enter PiP on home gesture if video is playing
        if (!isPaused) {
            enterTheaterPip()
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: android.content.res.Configuration,
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isInPipMode = isInPictureInPictureMode
        if (!isInPictureInPictureMode) {
            val videoUrl = intent.getStringExtra(EXTRA_VIDEO_URL) ?: DEFAULT_SAMPLE_URL
            val isAudio = intent.getBooleanExtra(EXTRA_IS_AUDIO, false) || isAudioUrl(videoUrl)
            requestedOrientation = if (isAudio) {
                ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT
            } else {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            }
            setupImmersiveFullscreen()
        } else {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    private fun returnPlaybackHandoff(finalPositionMs: Long, paused: Boolean) {
        val resultIntent = Intent().apply {
            putExtra(EXTRA_RESULT_POSITION_MS, finalPositionMs)
            putExtra(EXTRA_RESULT_IS_PAUSED, paused)
        }
        setResult(Activity.RESULT_OK, resultIntent)
        finish()
        if (Build.VERSION.SDK_INT >= 34) {
            overrideActivityTransition(
                Activity.OVERRIDE_TRANSITION_CLOSE,
                android.R.anim.fade_in,
                android.R.anim.fade_out,
            )
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
    }

    companion object {
        const val EXTRA_VIDEO_URL = "org.mozilla.fenix.theater.EXTRA_VIDEO_URL"
        const val EXTRA_TITLE = "org.mozilla.fenix.theater.EXTRA_TITLE"
        const val EXTRA_INITIAL_POSITION_MS = "org.mozilla.fenix.theater.EXTRA_INITIAL_POSITION_MS"
        const val EXTRA_INITIAL_PAUSED = "org.mozilla.fenix.theater.EXTRA_INITIAL_PAUSED"
        const val EXTRA_BADGES = "org.mozilla.fenix.theater.EXTRA_BADGES"
        const val EXTRA_IS_LIVE = "org.mozilla.fenix.theater.EXTRA_IS_LIVE"
        const val EXTRA_IS_AUDIO = "org.mozilla.fenix.theater.EXTRA_IS_AUDIO"

        const val EXTRA_RESULT_POSITION_MS = "org.mozilla.fenix.theater.EXTRA_RESULT_POSITION_MS"
        const val EXTRA_RESULT_IS_PAUSED = "org.mozilla.fenix.theater.EXTRA_RESULT_IS_PAUSED"

        const val DEFAULT_SAMPLE_URL =
            "https://interactive-examples.mdn.mozilla.net/media/cc0-videos/flower.mp4"
        const val DEFAULT_SAMPLE_TITLE = "MDN · Flower Bloom"
        const val DEFAULT_SAMPLE_AUDIO_URL =
            "https://interactive-examples.mdn.mozilla.net/media/cc0-audio/t-rex-roar.mp3"

        fun isAudioUrl(url: String): Boolean {
            val clean = url.substringBefore('?').substringBefore('#').lowercase()
            return clean.endsWith(".mp3") ||
                clean.endsWith(".m4a") ||
                clean.endsWith(".aac") ||
                clean.endsWith(".flac") ||
                clean.endsWith(".wav") ||
                clean.endsWith(".wave") ||
                clean.endsWith(".ogg") ||
                clean.endsWith(".oga") ||
                clean.endsWith(".opus") ||
                clean.endsWith(".weba")
        }

        fun isVideoUrl(url: String): Boolean {
            val clean = url.substringBefore('?').substringBefore('#').lowercase()
            return clean.endsWith(".mp4") ||
                clean.endsWith(".m4v") ||
                clean.endsWith(".mkv") ||
                clean.endsWith(".webm") ||
                clean.endsWith(".mov") ||
                clean.endsWith(".3gp") ||
                clean.endsWith(".ts") ||
                clean.endsWith(".m3u8")
        }

        fun isPlayableMedia(
            url: String,
            fileName: String? = null,
            mimeType: String? = null,
        ): Boolean {
            if (isAudioUrl(url) || isVideoUrl(url)) return true
            if (fileName != null && (isAudioUrl(fileName) || isVideoUrl(fileName))) return true
            if (mimeType != null) {
                val cleanMime = mimeType.substringBefore(';').trim().lowercase()
                if (cleanMime.startsWith("audio/") || cleanMime.startsWith("video/")) {
                    return true
                }
                if (cleanMime in listOf(
                        "application/ogg",
                        "application/x-mpegurl",
                        "application/vnd.apple.mpegurl",
                    )
                ) {
                    return true
                }
            }
            return false
        }

        fun createIntent(
            context: Context,
            videoUrl: String = DEFAULT_SAMPLE_URL,
            title: String = DEFAULT_SAMPLE_TITLE,
            initialPositionMs: Long = 0L,
            initialPaused: Boolean = false,
            badges: ArrayList<String> = arrayListOf("4K HDR", "Dolby Atmos"),
            isLive: Boolean = false,
            isAudio: Boolean = false,
        ): Intent {
            return Intent(context, ClarusTheaterActivity::class.java).apply {
                putExtra(EXTRA_VIDEO_URL, videoUrl)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_INITIAL_POSITION_MS, initialPositionMs)
                putExtra(EXTRA_INITIAL_PAUSED, initialPaused)
                putStringArrayListExtra(EXTRA_BADGES, badges)
                putExtra(EXTRA_IS_LIVE, isLive)
                putExtra(EXTRA_IS_AUDIO, isAudio || isAudioUrl(videoUrl))
            }
        }
    }
}
