/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.theater

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.view.SurfaceView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.painterResource
import android.util.Log
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import mozilla.components.ui.icons.R as iconsR
import org.mozilla.fenix.R
import org.mozilla.fenix.ext.components
import org.mozilla.fenix.theme.glass.ClarusGlassTokens
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Aspect ratio resize modes for Clarus Theater fullscreen video playback.
 */
enum class VideoResizeMode(val label: String) {
    FIT("Fit"),
    FILL("Fill"),
    STRETCH("Stretch"),
}

/**
 * Native Fullscreen Theater Player UI Shell for Clarus Theater.
 *
 * Implements horizontal/landscape liquid-glass styling inspired by the Stitch
 * "Clarus - Fullscreen Video Player" specification.
 * Features:
 * - Low-latency ExoPlayer start with custom DefaultLoadControl and direct setMediaItem position
 * - Frame-exact scrubbing with SeekParameters.EXACT
 * - Top-left circular 40dp exit-fullscreen button
 * - Zero "LIVE" badge presence under all playback states
 * - Smooth auto-hiding controls with tap-to-reveal
 * - Real Fit / Fill / Stretch aspect ratio scaling
 * - Ambient animated visualizer for direct audio URLs
 * - Left/Right vertical gesture controls for Brightness and Volume
 */
@Composable
fun ClarusTheaterScreen(
    videoUrl: String,
    initialPositionMs: Long = 0L,
    initialPaused: Boolean = false,
    title: String? = null,
    formatBadges: List<String> = emptyList(),
    isLive: Boolean = false,
    isAudio: Boolean = false,
    isInPip: Boolean = false,
    referrer: String? = null,
    cookies: String? = null,
    customUserAgent: String? = null,
    trigger: String? = null,
    onVideoSizeChanged: ((width: Int, height: Int) -> Unit)? = null,
    onClose: (currentPositionMs: Long, isPaused: Boolean) -> Unit,
    onEnterPip: () -> Unit,
    onOpenInBrowser: ((url: String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    val viewConfig = LocalViewConfiguration.current
    val touchSlop = viewConfig.touchSlop

    // -------------------------------------------------------------
    // Low-Latency ExoPlayer Lifecycle & State Management
    // -------------------------------------------------------------
    val player = remember(videoUrl, referrer, cookies, customUserAgent) {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 15_000,
                /* maxBufferMs = */ 50_000,
                /* bufferForPlaybackMs = */ 300,
                /* bufferForPlaybackAfterRebufferMs = */ 1_000,
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val userAgent = customUserAgent?.ifBlank { null }
            ?: runCatching {
                context.components.core.engine.settings.userAgentString
            }.getOrNull()?.ifBlank { null }
            ?: "Mozilla/5.0 (Linux; Android 14; Mobile; rv:128.0) Gecko/128.0 Firefox/128.0"

        val requestProperties = mutableMapOf(
            "Accept" to "*/*",
            "Accept-Encoding" to "identity",
        )
        if (!referrer.isNullOrBlank()) {
            requestProperties["Referer"] = referrer
        }
        if (!cookies.isNullOrBlank()) {
            requestProperties["Cookie"] = cookies
        }

        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(userAgent)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(20_000)
            .setDefaultRequestProperties(requestProperties)

        val dataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

        ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .setSeekParameters(SeekParameters.EXACT)
            .build()
            .apply {
                // Prepare from position 0 first to allow container metadata (ftyp/moov) to parse cleanly without premature Range requests
                setMediaItem(MediaItem.fromUri(videoUrl))
                prepare()
                if (initialPositionMs <= 0L) {
                    playWhenReady = !initialPaused
                } else {
                    playWhenReady = false
                }
                videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT
            }
    }

    val coroutineScope = rememberCoroutineScope()
    var retryCount by remember { mutableIntStateOf(0) }
    var hasPerformedInitialSeek by remember(videoUrl) { mutableStateOf(initialPositionMs <= 0L) }
    var isPlaying by remember { mutableStateOf(!initialPaused) }
    var playbackState by remember { mutableIntStateOf(player.playbackState) }
    var playbackError by remember { mutableStateOf<String?>(null) }
    var isContainerUnsupported by remember { mutableStateOf(false) }
    var currentPositionMs by remember { mutableLongStateOf(initialPositionMs) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var bufferedPositionMs by remember { mutableLongStateOf(0L) }
    var videoAspectRatio by remember { mutableFloatStateOf(16f / 9f) }
    var resizeMode by remember { mutableStateOf(VideoResizeMode.FIT) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(state: Int) {
                playbackState = state
                if (state == Player.STATE_READY) {
                    retryCount = 0
                    durationMs = player.duration.coerceAtLeast(0L)
                    playbackError = null
                    isContainerUnsupported = false
                    if (!hasPerformedInitialSeek && initialPositionMs > 0L) {
                        hasPerformedInitialSeek = true
                        player.seekTo(initialPositionMs)
                        player.playWhenReady = !initialPaused
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e("ClarusTheater", "ExoPlayer playback error: ${error.errorCodeName}", error)
                if (isConnectionResetError(error) && retryCount < 3) {
                    retryCount++
                    val backoffMs = retryCount * 350L
                    Log.w("ClarusTheater", "Connection reset encountered. Retrying ($retryCount/3) after ${backoffMs}ms backoff...")
                    coroutineScope.launch {
                        delay(backoffMs)
                        playbackError = null
                        player.prepare()
                        if (hasPerformedInitialSeek || initialPositionMs <= 0L) {
                            if (!initialPaused) player.play()
                        }
                    }
                    return
                }
                if (error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED) {
                    isContainerUnsupported = true
                    playbackError = "This link does not appear to be a direct media stream."
                } else {
                    isContainerUnsupported = false
                    val detailedMsg = error.cause?.message ?: error.message ?: error.errorCodeName
                    playbackError = detailedMsg
                }
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    videoAspectRatio = videoSize.width.toFloat() / videoSize.height.toFloat()
                    onVideoSizeChanged?.invoke(videoSize.width, videoSize.height)
                }
            }
        }
        player.addListener(listener)

        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    // High-frequency position polling loop (every 150ms for responsive UI)
    LaunchedEffect(player) {
        while (true) {
            currentPositionMs = player.currentPosition.coerceAtLeast(0L)
            durationMs = player.duration.coerceAtLeast(0L)
            bufferedPositionMs = player.bufferedPosition.coerceAtLeast(0L)
            isPlaying = player.isPlaying
            delay(150)
        }
    }

    // Update player scaling mode when resizeMode changes
    LaunchedEffect(resizeMode) {
        player.videoScalingMode = when (resizeMode) {
            VideoResizeMode.FIT -> C.VIDEO_SCALING_MODE_SCALE_TO_FIT
            VideoResizeMode.FILL -> C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
            VideoResizeMode.STRETCH -> C.VIDEO_SCALING_MODE_SCALE_TO_FIT
        }
    }

    // -------------------------------------------------------------
    // Real System Volume & Window Brightness State
    // -------------------------------------------------------------
    var currentVolume by remember {
        mutableIntStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC))
    }

    var brightness by remember {
        val attrBrightness = activity?.window?.attributes?.screenBrightness ?: -1f
        mutableFloatStateOf(if (attrBrightness in 0.01f..1.0f) attrBrightness else 0.65f)
    }

    // Temporary active sliders for side gestures (decoupled from general HUD)
    var showBrightnessSlider by remember { mutableStateOf(false) }
    var showVolumeSlider by remember { mutableStateOf(false) }
    var brightnessFadeJob by remember { mutableStateOf<Job?>(null) }
    var volumeFadeJob by remember { mutableStateOf<Job?>(null) }

    // Center scrub gesture state
    var isCenterScrubbing by remember { mutableStateOf(false) }
    var centerScrubTargetMs by remember { mutableLongStateOf(0L) }
    var centerScrubDeltaSeconds by remember { mutableIntStateOf(0) }

    // Bottom scrubber dragging state
    var isScrubberDragging by remember { mutableStateOf(false) }
    var scrubDragPositionMs by remember { mutableLongStateOf(0L) }

    // -------------------------------------------------------------
    // Controls Visibility & 3.5s Inactivity Auto-Hide Timer
    // -------------------------------------------------------------
    var controlsVisible by remember { mutableStateOf(true) }
    var lastInteractionTimestamp by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(controlsVisible, lastInteractionTimestamp, isPlaying, isCenterScrubbing, isScrubberDragging) {
        if (controlsVisible && !isCenterScrubbing && !isScrubberDragging && isPlaying) {
            delay(3500)
            controlsVisible = false
        }
    }

    fun markInteraction() {
        lastInteractionTimestamp = System.currentTimeMillis()
        controlsVisible = true
    }

    // Resolved badges list (never contains LIVE)
    val resolvedBadges = remember(formatBadges, videoAspectRatio, durationMs, isAudio) {
        if (isAudio) {
            listOf("HQ Audio", "Lossless Stream")
        } else if (formatBadges.isNotEmpty()) {
            formatBadges.filter { !it.equals("LIVE", ignoreCase = true) }
        } else {
            val resBadge = when {
                videoAspectRatio >= 1.7f && durationMs > 0 -> "1080p FHD"
                else -> "Direct Stream"
            }
            listOf(resBadge)
        }
    }

    // -------------------------------------------------------------
    // Root Horizontal Layout with Fullscreen Touch & Gesture Detection
    // -------------------------------------------------------------
    val gestureModifier = if (isInPip) {
        Modifier
    } else {
        Modifier.pointerInput(touchSlop) {
            awaitEachGesture {
                val wasControlsVisibleAtDown = controlsVisible
                val down = awaitFirstDown(requireUnconsumed = false)
                val startX = down.position.x
                val startY = down.position.y
                val width = size.width
                val height = size.height

                var isDragging = false
                var resolvedZone = GestureZone.CENTER_SCRUB
                var initialDragPosMs = 0L
                var accumulatedVolDelta = 0f

                var prevY = startY
                var prevX = startX

                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break

                    if (!change.pressed) {
                        // Touch released
                        Log.d("ClarusTheater", "gestureModifier touch release: isDragging=$isDragging, wasVisibleAtDown=$wasControlsVisibleAtDown, isConsumed=${change.isConsumed}")
                        if (!isDragging) {
                            if (!wasControlsVisibleAtDown) {
                                // Controls were hidden when touch started: reveal them smoothly
                                Log.d("ClarusTheater", "Revealing controls on screen tap")
                                controlsVisible = true
                                lastInteractionTimestamp = System.currentTimeMillis()
                            } else {
                                // Controls were visible when touch started: hide them smoothly if not consumed by button
                                if (!change.isConsumed) {
                                    Log.d("ClarusTheater", "Hiding controls on unconsumed screen tap")
                                    controlsVisible = false
                                }
                            }
                        } else {
                            when (resolvedZone) {
                                GestureZone.CENTER_SCRUB -> {
                                    if (isCenterScrubbing) {
                                        player.seekTo(centerScrubTargetMs)
                                        isCenterScrubbing = false
                                    }
                                }
                                GestureZone.BRIGHTNESS -> {
                                    brightnessFadeJob?.cancel()
                                    brightnessFadeJob = coroutineScope.launch {
                                        delay(2000)
                                        showBrightnessSlider = false
                                    }
                                }
                                GestureZone.VOLUME -> {
                                    volumeFadeJob?.cancel()
                                    volumeFadeJob = coroutineScope.launch {
                                        delay(2000)
                                        showVolumeSlider = false
                                    }
                                }
                            }
                            markInteraction()
                        }
                        break
                    }

                    val curX = change.position.x
                    val curY = change.position.y
                    val totalDx = curX - startX
                    val totalDy = curY - startY

                    if (!isDragging) {
                        if (hypot(totalDx, totalDy) > touchSlop) {
                            isDragging = true
                            markInteraction()

                            val isVertical = abs(totalDy) > abs(totalDx)
                            resolvedZone = if (isVertical) {
                                if (startX < width * 0.5f) GestureZone.BRIGHTNESS else GestureZone.VOLUME
                            } else {
                                GestureZone.CENTER_SCRUB
                            }

                            if (resolvedZone == GestureZone.CENTER_SCRUB && !isAudio) {
                                isCenterScrubbing = true
                                initialDragPosMs = player.currentPosition
                                centerScrubTargetMs = initialDragPosMs
                                centerScrubDeltaSeconds = 0
                            } else if (resolvedZone == GestureZone.BRIGHTNESS) {
                                brightnessFadeJob?.cancel()
                                showBrightnessSlider = true
                                showVolumeSlider = false
                            } else if (resolvedZone == GestureZone.VOLUME) {
                                volumeFadeJob?.cancel()
                                showVolumeSlider = true
                                showBrightnessSlider = false
                            }
                        }
                    }

                    if (isDragging) {
                        change.consume()
                        markInteraction()
                        val dy = curY - prevY
                        val dx = curX - prevX

                        when (resolvedZone) {
                            GestureZone.BRIGHTNESS -> {
                                val delta = -dy / (height * 0.75f)
                                val newBrightness = (brightness + delta).coerceIn(0.01f, 1.0f)
                                brightness = newBrightness
                                activity?.let { act ->
                                    val lp = act.window.attributes
                                    lp.screenBrightness = newBrightness
                                    act.window.attributes = lp
                                }
                                brightnessFadeJob?.cancel()
                                showBrightnessSlider = true
                            }
                            GestureZone.VOLUME -> {
                                val volChange = -dy / (height * 0.75f) * maxVolume
                                accumulatedVolDelta += volChange
                                if (abs(accumulatedVolDelta) >= 0.75f) {
                                    val step = if (accumulatedVolDelta > 0) 1 else -1
                                    val newVol = (currentVolume + step).coerceIn(0, maxVolume)
                                    if (newVol != currentVolume) {
                                        currentVolume = newVol
                                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0)
                                    }
                                    accumulatedVolDelta = 0f
                                }
                                volumeFadeJob?.cancel()
                                showVolumeSlider = true
                            }
                            GestureZone.CENTER_SCRUB -> {
                                if (!isAudio) {
                                    val scrubFactor = (durationMs / 1000f).coerceIn(30f, 300f)
                                    val deltaSeconds = (totalDx / (width * 0.35f) * scrubFactor).toInt()
                                    centerScrubDeltaSeconds = deltaSeconds
                                    val target = (initialDragPosMs + deltaSeconds * 1000L).coerceIn(0L, durationMs)
                                    centerScrubTargetMs = target
                                }
                            }
                        }

                        prevX = curX
                        prevY = curY
                    }
                }
            }
        }
    }

    // Fullscreen video entrance spring animation
    val entranceScale = remember { Animatable(0.92f) }
    val entranceAlpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        launch {
            entranceScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow,
                ),
            )
        }
        launch {
            entranceAlpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 280),
            )
        }
    }

    if (isAudio) {
        if (isInPip) {
            ClarusAudioPipScreen(
                title = title ?: "Audio Stream",
                isPlaying = isPlaying,
                currentPosMs = currentPositionMs,
                durationMs = durationMs,
                onPlayPause = {
                    if (isPlaying) player.pause() else player.play()
                },
                modifier = modifier,
            )
            return
        }
        ClarusPortraitAudioScreen(
            title = title ?: "Audio Stream",
            badges = resolvedBadges,
            isPlaying = isPlaying,
            playbackState = playbackState,
            playbackError = playbackError,
            currentPosMs = if (isScrubberDragging) scrubDragPositionMs else currentPositionMs,
            durationMs = durationMs,
            bufferedPosMs = bufferedPositionMs,
            currentVolume = currentVolume,
            maxVolume = maxVolume,
            onVolumeChange = { newVol ->
                currentVolume = newVol
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0)
            },
            onPlayPause = {
                if (isPlaying) player.pause() else player.play()
            },
            onReplay10 = {
                val target = (player.currentPosition - 10_000L).coerceAtLeast(0L)
                player.seekTo(target)
            },
            onForward10 = {
                val target = (player.currentPosition + 10_000L).coerceAtMost(durationMs)
                player.seekTo(target)
            },
            onSeek = { targetMs ->
                player.seekTo(targetMs)
            },
            onScrubStart = {
                isScrubberDragging = true
                scrubDragPositionMs = currentPositionMs
            },
            onScrubProgress = { posMs ->
                scrubDragPositionMs = posMs
            },
            onScrubEnd = { posMs ->
                player.seekTo(posMs)
                isScrubberDragging = false
            },
            onRetry = {
                playbackError = null
                isContainerUnsupported = false
                player.prepare()
                player.play()
            },
            onClose = {
                onClose(player.currentPosition, !isPlaying)
            },
            onEnterPip = onEnterPip,
            isContainerUnsupported = isContainerUnsupported,
            onOpenInBrowser = if (onOpenInBrowser != null) { { onOpenInBrowser(videoUrl) } } else null,
            modifier = modifier,
        )
        return
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF08090D)) // Deep cinema obsidian
            .graphicsLayer {
                scaleX = entranceScale.value
                scaleY = entranceScale.value
                alpha = entranceAlpha.value
            }
            .then(gestureModifier),
    ) {
        // -------------------------------------------------------------
        // Background Fullscreen Video Surface
        // -------------------------------------------------------------
        BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .clipToBounds(),
                contentAlignment = Alignment.Center,
            ) {
                val screenWidth = maxWidth
                val screenHeight = maxHeight
                val screenRatio = if (screenHeight.value > 0) screenWidth.value / screenHeight.value else 16f / 9f

                when (resizeMode) {
                    VideoResizeMode.FIT -> {
                        // Letterbox / Pillarbox maintaining video ratio
                        AndroidView(
                            factory = { ctx ->
                                SurfaceView(ctx).apply {
                                    player.setVideoSurfaceView(this)
                                }
                            },
                            modifier = Modifier
                                .fillMaxSize()
                                .aspectRatio(videoAspectRatio, matchHeightConstraintsFirst = false),
                        )
                    }
                    VideoResizeMode.FILL -> {
                        // Zoom and crop excess edges to fill the entire landscape screen
                        val (targetWidth, targetHeight) = if (screenRatio > videoAspectRatio) {
                            screenWidth to (screenWidth / videoAspectRatio)
                        } else {
                            (screenHeight * videoAspectRatio) to screenHeight
                        }

                        AndroidView(
                            factory = { ctx ->
                                SurfaceView(ctx).apply {
                                    player.setVideoSurfaceView(this)
                                }
                            },
                            modifier = Modifier
                                .size(targetWidth, targetHeight)
                                .wrapContentSize(Alignment.Center),
                        )
                    }
                    VideoResizeMode.STRETCH -> {
                        // Directly stretch SurfaceView to exact window dimensions
                        AndroidView(
                            factory = { ctx ->
                                SurfaceView(ctx).apply {
                                    player.setVideoSurfaceView(this)
                                }
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }

        // -------------------------------------------------------------
        // Center Buffering Indicator
        // -------------------------------------------------------------
        if (playbackState == Player.STATE_BUFFERING && !isInPip) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(52.dp),
                    color = Color(0xFFD5A24A), // Muted Saffron / Amber glow
                    strokeWidth = 3.dp,
                )
            }
        }

        // -------------------------------------------------------------
        // Center Scrub Seek Popup (Center zone drag preview)
        // -------------------------------------------------------------
        if (isCenterScrubbing && !isAudio && !isInPip) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CenterScrubHud(
                    deltaSeconds = centerScrubDeltaSeconds,
                    targetMs = centerScrubTargetMs,
                    durationMs = durationMs,
                )
            }
        }

        // -------------------------------------------------------------
        // Side Sliders: Left Brightness Capsule Slider
        // -------------------------------------------------------------
        AnimatedVisibility(
            visible = showBrightnessSlider && !isInPip,
            enter = fadeIn(tween(250)),
            exit = fadeOut(tween(350)),
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 96.dp),
        ) {
            SideCapsuleSlider(
                iconRes = R.drawable.ic_theater_brightness,
                iconTint = Color(0xFFE08A44), // Warm Amber
                value = brightness,
                fillBrush = Brush.verticalGradient(
                    colors = listOf(Color(0xFFE08A44), Color(0xFFB8754B)),
                ),
                label = "${(brightness * 100).toInt()}%",
            )
        }

        // -------------------------------------------------------------
        // Side Sliders: Right Volume Capsule Slider
        // -------------------------------------------------------------
        AnimatedVisibility(
            visible = showVolumeSlider && !isInPip,
            enter = fadeIn(tween(250)),
            exit = fadeOut(tween(350)),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 96.dp),
        ) {
            val volRatio = currentVolume.toFloat() / maxVolume.toFloat()
            SideCapsuleSlider(
                iconRes = R.drawable.ic_theater_volume,
                iconTint = Color(0xFFAAA0D2), // Dusty Lavender
                value = volRatio,
                fillBrush = Brush.verticalGradient(
                    colors = listOf(Color(0xFFAAA0D2), Color(0xFF8B80F9)),
                ),
                label = "${(volRatio * 100).toInt()}%",
            )
        }

        // -------------------------------------------------------------
        // Main Unified HUD Overlays: Top Bar, Center Trio, Bottom Scrubber Dock
        // Auto-hides after inactivity, smoothly reveals on tap
        // -------------------------------------------------------------
        AnimatedVisibility(
            visible = controlsVisible && !isInPip,
            enter = fadeIn(tween(durationMillis = 250, easing = FastOutSlowInEasing)),
            exit = fadeOut(tween(durationMillis = 250, easing = FastOutSlowInEasing)),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        Log.d("ClarusTheater", "Overlay empty space clicked -> controlsVisible = false")
                        controlsVisible = false
                    },
            ) {
                // Top Bar with Circular Exit-Fullscreen Button in Top-Left
                TheaterTopBar(
                    badges = resolvedBadges,
                    title = title,
                    onEnterPip = onEnterPip,
                    onClose = {
                        onClose(player.currentPosition, !isPlaying)
                    },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 20.dp),
                )

                // Center Playback Control Trio (10s Back, Hero Play/Pause, 10s Forward)
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(28.dp),
                ) {
                    // Replay 10s Circular Button
                    IconButton(
                        onClick = {
                            val target = (player.currentPosition - 10_000L).coerceAtLeast(0L)
                            player.seekTo(target)
                            markInteraction()
                        },
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(Color(0x550F172A))
                            .border(0.75.dp, Color(0x2EFFFFFF), CircleShape),
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_theater_replay_10),
                            contentDescription = "Replay 10 seconds",
                            tint = Color(0xFFE2E8F0),
                            modifier = Modifier.size(24.dp),
                        )
                    }

                    // Hero Frosted Glass Play/Pause Button
                    FrostedHeroPlayButton(
                        isPlaying = isPlaying,
                        onClick = {
                            if (isPlaying) {
                                player.pause()
                            } else {
                                player.play()
                            }
                            markInteraction()
                        },
                    )

                    // Forward 10s Circular Button
                    IconButton(
                        onClick = {
                            val target = (player.currentPosition + 10_000L).coerceAtMost(durationMs)
                            player.seekTo(target)
                            markInteraction()
                        },
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(Color(0x550F172A))
                            .border(0.75.dp, Color(0x2EFFFFFF), CircleShape),
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_theater_forward_10),
                            contentDescription = "Forward 10 seconds",
                            tint = Color(0xFFE2E8F0),
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }

                // Bottom Floating Scrubber Dock Capsule
                TheaterBottomDock(
                    currentPosMs = if (isScrubberDragging) scrubDragPositionMs else currentPositionMs,
                    durationMs = durationMs,
                    bufferedPosMs = bufferedPositionMs,
                    resizeMode = resizeMode,
                    isAudio = isAudio,
                    onSeek = { targetMs ->
                        player.seekTo(targetMs)
                        markInteraction()
                    },
                    onScrubStart = {
                        isScrubberDragging = true
                        scrubDragPositionMs = currentPositionMs
                        markInteraction()
                    },
                    onScrubProgress = { posMs ->
                        scrubDragPositionMs = posMs
                        markInteraction()
                    },
                    onScrubEnd = { posMs ->
                        player.seekTo(posMs)
                        isScrubberDragging = false
                        markInteraction()
                    },
                    onToggleResizeMode = {
                        resizeMode = when (resizeMode) {
                            VideoResizeMode.FIT -> VideoResizeMode.FILL
                            VideoResizeMode.FILL -> VideoResizeMode.STRETCH
                            VideoResizeMode.STRETCH -> VideoResizeMode.FIT
                        }
                        markInteraction()
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 28.dp, vertical = 22.dp),
                )
            }
        }

        // -------------------------------------------------------------
        // Video Playback Error Overlay
        // -------------------------------------------------------------
        if (playbackError != null && !isInPip) {
            TheaterErrorOverlay(
                errorMessage = playbackError ?: "Playback error",
                onRetry = {
                    retryCount = 0
                    playbackError = null
                    isContainerUnsupported = false
                    player.prepare()
                    player.play()
                },
                onClose = {
                    onClose(player.currentPosition, !isPlaying)
                },
                isOpenInBrowserVisible = isContainerUnsupported && onOpenInBrowser != null,
                onOpenInBrowser = if (onOpenInBrowser != null) { { onOpenInBrowser(videoUrl) } } else null,
            )
        }
    }
}

private enum class GestureZone {
    BRIGHTNESS,
    VOLUME,
    CENTER_SCRUB,
}

// -------------------------------------------------------------
// Top Bar: Circular Exit Button (Top-Left) + Title & Badges + PiP Button (Top-Right)
// Never renders any "LIVE" pill
// -------------------------------------------------------------
@Composable
private fun TheaterTopBar(
    badges: List<String>,
    title: String?,
    onEnterPip: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        // TOP-LEFT: Circular 40dp Frosted Glass Exit-Fullscreen Button
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(Color(0x660F172A))
                .border(0.75.dp, Color(0x33FFFFFF), CircleShape),
        ) {
            Icon(
                painter = painterResource(id = iconsR.drawable.mozac_ic_back_24),
                contentDescription = "Exit Theater",
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
        }

        // CENTER: Elegant Title and Format Badges (No LIVE Pill)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.weight(1f, fill = false).padding(horizontal = 16.dp),
        ) {
            if (!title.isNullOrBlank()) {
                Text(
                    text = title,
                    color = Color(0xFFF1F5F9),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.3.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }

        // TOP-RIGHT: PiP Glass Button Capsule
        IconButton(
            onClick = onEnterPip,
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(Color(0x660F172A))
                .border(0.75.dp, Color(0x33FFFFFF), CircleShape),
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_theater_pip),
                contentDescription = "Picture-in-Picture",
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

// -------------------------------------------------------------
// Side Glass Capsule Slider (Brightness / Volume)
// -------------------------------------------------------------
@Composable
private fun SideCapsuleSlider(
    iconRes: Int,
    iconTint: Color,
    value: Float,
    fillBrush: Brush,
    label: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(42.dp)
            .height(180.dp)
            .clip(RoundedCornerShape(21.dp))
            .background(Color(0x8C0F172A)) // Frosted dark obsidian glass
            .border(0.75.dp, Color(0x2EFFFFFF), RoundedCornerShape(21.dp))
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        // Icon at top
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(18.dp),
        )

        // Vertical fill bar track
        Box(
            modifier = Modifier
                .width(6.dp)
                .weight(1f)
                .padding(vertical = 8.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color(0x22FFFFFF)),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .fillMaxHeight(value.coerceIn(0f, 1f))
                    .clip(RoundedCornerShape(3.dp))
                    .background(fillBrush),
            )
        }

        // Percentage label at bottom
        Text(
            text = label,
            color = Color(0xFFF1F5F9),
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
        )
    }
}

// -------------------------------------------------------------
// Hero Frosted Glass Play/Pause Button (68dp Stitch design)
// -------------------------------------------------------------
@Composable
private fun FrostedHeroPlayButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(68.dp)
            .clip(CircleShape)
            .background(Color(0x800F172A))
            .border(1.dp, Color(0x40FFFFFF), CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (isPlaying) {
            // Two crisp vertical pause bars
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .width(5.dp)
                        .height(24.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White),
                )
                Box(
                    modifier = Modifier
                        .width(5.dp)
                        .height(24.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White),
                )
            }
        } else {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = "Play",
                tint = Color.White,
                modifier = Modifier
                    .size(38.dp)
                    .padding(start = 3.dp),
            )
        }
    }
}

// -------------------------------------------------------------
// Center Scrub HUD (Appears when dragging horizontally in center zone)
// -------------------------------------------------------------
@Composable
private fun CenterScrubHud(
    deltaSeconds: Int,
    targetMs: Long,
    durationMs: Long,
    modifier: Modifier = Modifier,
) {
    val sign = if (deltaSeconds >= 0) "+$deltaSeconds" else "$deltaSeconds"
    val formattedTarget = formatTime(targetMs)
    val formattedDuration = formatTime(durationMs)

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xD90F172A))
            .border(1.dp, Color(0x33D5A24A), RoundedCornerShape(20.dp))
            .padding(horizontal = 24.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "[$sign s]",
            color = Color(0xFFD5A24A),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "$formattedTarget / $formattedDuration",
            color = Color(0xFFF8FAFC),
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
        )
    }
}

// -------------------------------------------------------------
// Bottom Floating Scrubber Dock Capsule
// Contains timecodes, exact scrubber track, and aspect-ratio toggle
// -------------------------------------------------------------
@Composable
private fun TheaterBottomDock(
    currentPosMs: Long,
    durationMs: Long,
    bufferedPosMs: Long,
    resizeMode: VideoResizeMode,
    isAudio: Boolean = false,
    onSeek: (Long) -> Unit,
    onScrubStart: () -> Unit,
    onScrubProgress: (Long) -> Unit,
    onScrubEnd: (Long) -> Unit,
    onToggleResizeMode: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(58.dp)
            .clip(RoundedCornerShape(29.dp))
            .background(Color(0x8C0F172A)) // Frosted liquid glass
            .border(0.75.dp, Color(0x2EFFFFFF), RoundedCornerShape(29.dp))
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Elapsed Timecode
            Text(
                text = formatTime(currentPosMs),
                color = Color(0xFFE2E8F0),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.Monospace,
            )

            // Exact-Seek Interactive Scrubber Slider Track
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(34.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                InteractiveScrubberTrack(
                    currentPosMs = currentPosMs,
                    durationMs = durationMs,
                    bufferedPosMs = bufferedPosMs,
                    onSeek = onSeek,
                    onScrubStart = onScrubStart,
                    onScrubProgress = onScrubProgress,
                    onScrubEnd = onScrubEnd,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // Total Duration Timecode
            Text(
                text = formatTime(durationMs),
                color = Color(0xFF94A3B8),
                fontSize = 11.sp,
                fontWeight = FontWeight.Normal,
                fontFamily = FontFamily.Monospace,
            )

            // Aspect Ratio Toggle Pill (Fit / Fill / Stretch) - Video only
            if (!isAudio) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x33FFFFFF))
                        .border(0.5.dp, Color(0x33FFFFFF), RoundedCornerShape(12.dp))
                        .clickable(onClick = onToggleResizeMode)
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = resizeMode.label,
                        color = Color(0xFFF1F5F9),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------
// Interactive Scrubber Track with Exact Seek Callbacks
// Fixes drag-end seeking to land on the exact frame released on
// -------------------------------------------------------------
@Composable
private fun InteractiveScrubberTrack(
    currentPosMs: Long,
    durationMs: Long,
    bufferedPosMs: Long,
    onSeek: (Long) -> Unit,
    onScrubStart: () -> Unit,
    onScrubProgress: (Long) -> Unit,
    onScrubEnd: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val progressFraction = if (durationMs > 0) (currentPosMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val bufferFraction = if (durationMs > 0) (bufferedPosMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

    var activeDragTargetMs by remember { mutableLongStateOf(currentPosMs) }

    BoxWithConstraints(
        modifier = modifier
            .pointerInput(durationMs) {
                detectDragGestures(
                    onDragStart = { offset ->
                        onScrubStart()
                        if (durationMs > 0 && size.width > 0) {
                            val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                            val target = (fraction * durationMs).toLong()
                            activeDragTargetMs = target
                            onScrubProgress(target)
                        }
                    },
                    onDragEnd = {
                        // Land on the exact frame released on
                        onScrubEnd(activeDragTargetMs)
                    },
                    onDragCancel = {
                        onScrubEnd(currentPosMs)
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        if (durationMs > 0 && size.width > 0) {
                            val fraction = (change.position.x / size.width).coerceIn(0f, 1f)
                            val target = (fraction * durationMs).toLong()
                            activeDragTargetMs = target
                            onScrubProgress(target)
                        }
                    },
                )
            }
            .pointerInput(durationMs) {
                detectTapGestures { offset ->
                    if (durationMs > 0 && size.width > 0) {
                        val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                        val target = (fraction * durationMs).toLong()
                        onSeek(target)
                    }
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        val widthPx = constraints.maxWidth.toFloat()

        // Background track
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color(0x26FFFFFF)),
        )

        // Buffered track
        Box(
            modifier = Modifier
                .fillMaxWidth(bufferFraction)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color(0x40FFFFFF)),
        )

        // Played progress track (Liquid Amber / Copper Gradient)
        Box(
            modifier = Modifier
                .fillMaxWidth(progressFraction)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(Color(0xFFE08A44), Color(0xFFD5A24A), Color(0xFFAAA0D2)),
                    ),
                ),
        )

        // Glowing Thumb Indicator
        val density = LocalDensity.current
        val thumbRadiusPx = with(density) { 7.dp.toPx() }
        val thumbOffsetPx = ((widthPx * progressFraction) - thumbRadiusPx).coerceIn(0f, (widthPx - thumbRadiusPx * 2).coerceAtLeast(0f))

        Box(
            modifier = Modifier
                .padding(start = with(density) { thumbOffsetPx.toDp() })
                .size(14.dp)
                .clip(CircleShape)
                .background(Color.White)
                .border(1.5.dp, Color(0xFFD5A24A), CircleShape),
        )
    }
}

// -------------------------------------------------------------
// Clarus Serene Tactile Glass Portrait Audio Player Screen
// -------------------------------------------------------------
@Composable
private fun ClarusPortraitAudioScreen(
    title: String,
    badges: List<String>,
    isPlaying: Boolean,
    playbackState: Int,
    playbackError: String?,
    currentPosMs: Long,
    durationMs: Long,
    bufferedPosMs: Long,
    currentVolume: Int = 0,
    maxVolume: Int = 1,
    onVolumeChange: (Int) -> Unit = {},
    onPlayPause: () -> Unit,
    onReplay10: () -> Unit,
    onForward10: () -> Unit,
    onSeek: (Long) -> Unit,
    onScrubStart: () -> Unit,
    onScrubProgress: (Long) -> Unit,
    onScrubEnd: (Long) -> Unit,
    onRetry: () -> Unit,
    onClose: () -> Unit,
    onEnterPip: () -> Unit,
    isContainerUnsupported: Boolean = false,
    onOpenInBrowser: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition()

    // Breathing pulse scale
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.90f,
        targetValue = 1.10f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
    )

    // Aura rotation
    val auraRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 28000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
    )

    // Ambient glow alpha
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.40f,
        targetValue = 0.70f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
    )

    val activeScale = if (isPlaying) pulseScale else 0.92f
    val activeAlpha = if (isPlaying) glowAlpha else 0.35f

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF090A10)), // Serene deep midnight canvas
    ) {
        // Ambient background aura canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            val centerOffset = Offset(size.width * 0.5f, size.height * 0.38f)
            val baseRadius = size.width * 0.48f * activeScale

            // Outermost soft violet/lavender aura
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0x44AAA0D2).copy(alpha = activeAlpha * 0.6f),
                        Color(0x224C1D95).copy(alpha = activeAlpha * 0.35f),
                        Color.Transparent,
                    ),
                    center = centerOffset,
                    radius = baseRadius * 1.6f,
                ),
                radius = baseRadius * 1.6f,
                center = centerOffset,
            )

            // Mid warm copper/amber glowing harmonic ring
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0x55E08A44).copy(alpha = activeAlpha * 0.9f),
                        Color(0x26B8754B).copy(alpha = activeAlpha * 0.5f),
                        Color.Transparent,
                    ),
                    center = centerOffset,
                    radius = baseRadius * 1.15f,
                ),
                radius = baseRadius * 1.15f,
                center = centerOffset,
            )

            // Inner concentric sound ripples
            for (i in 1..3) {
                val rippleRadius = baseRadius * (0.35f + i * 0.22f)
                val rippleAlpha = (activeAlpha * (1f - i * 0.25f)).coerceIn(0f, 1f)
                drawCircle(
                    color = Color(0xFFD5A24A).copy(alpha = rippleAlpha * 0.25f),
                    radius = rippleRadius,
                    center = centerOffset,
                    style = Stroke(width = 1.5f),
                )
            }
        }

        // Main Portrait Audio Content Column
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .padding(top = 48.dp, bottom = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // TOP BAR: Frosted Exit Button, Category Pill, PiP Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color(0x660F172A))
                        .border(0.75.dp, Color(0x33FFFFFF), CircleShape),
                ) {
                    Icon(
                        painter = painterResource(id = iconsR.drawable.mozac_ic_back_24),
                        contentDescription = "Exit Theater",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                }

                // Centered subtle category pill
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0x401E293B))
                        .border(0.5.dp, Color(0x22FFFFFF), RoundedCornerShape(14.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFD5A24A)),
                    )
                    Text(
                        text = "CLARUS THEATER",
                        color = Color(0xFFCBD5E1),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.sp,
                    )
                }

                IconButton(
                    onClick = onEnterPip,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color(0x660F172A))
                        .border(0.75.dp, Color(0x33FFFFFF), CircleShape),
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_theater_pip),
                        contentDescription = "Picture-in-Picture",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.weight(0.7f))

            // HERO ARTWORK / SOUNDWAVE GLASS CARD (240dp x 240dp)
            Box(
                modifier = Modifier
                    .size(240.dp)
                    .clip(RoundedCornerShape(36.dp))
                    .background(Color(0x6611131F))
                    .border(
                        1.dp,
                        Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.28f),
                                Color.White.copy(alpha = 0.08f),
                                Color(0x22B8754B),
                            ),
                        ),
                        RoundedCornerShape(36.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                // Internal vinyl sound ring
                Box(
                    modifier = Modifier
                        .size(170.dp)
                        .clip(CircleShape)
                        .background(Color(0x550F172A))
                        .border(1.dp, Color(0x26D5A24A), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    // Harmonic soundwave bars
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val barHeights = listOf(20.dp, 36.dp, 56.dp, 32.dp, 48.dp, 24.dp)
                        barHeights.forEachIndexed { index, targetHeight ->
                            val barHeight = if (isPlaying) {
                                val factor = 0.55f + 0.45f * sin((auraRotation * 0.06f) + index.toFloat())
                                targetHeight * factor
                            } else {
                                targetHeight * 0.35f
                            }
                            Box(
                                modifier = Modifier
                                    .width(4.5.dp)
                                    .height(barHeight)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(Color(0xFFE08A44), Color(0xFFD5A24A), Color(0xFFAAA0D2)),
                                        ),
                                    ),
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // TRACK TITLE & BADGES
            Text(
                text = title,
                color = Color(0xFFF8FAFC),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.3.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Format Badges pill
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFD5A24A)),
                )
                Text(
                    text = if (badges.isNotEmpty()) badges.joinToString(" · ") else "Lossless Audio Stream",
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.5.sp,
                )
            }

            Spacer(modifier = Modifier.weight(0.5f))

            // SCRUBBER TRACK & TIMECODES
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
            ) {
                InteractiveScrubberTrack(
                    currentPosMs = currentPosMs,
                    durationMs = durationMs,
                    bufferedPosMs = bufferedPosMs,
                    onSeek = onSeek,
                    onScrubStart = onScrubStart,
                    onScrubProgress = onScrubProgress,
                    onScrubEnd = onScrubEnd,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = formatTime(currentPosMs),
                        color = Color(0xFFE2E8F0),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = FontFamily.Monospace,
                    )
                    Text(
                        text = formatTime(durationMs),
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // PLAYBACK CONTROLS TRIO (10s Back, Hero Play/Pause, 10s Forward)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(36.dp),
            ) {
                // Replay 10s
                IconButton(
                    onClick = onReplay10,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(Color(0x660F172A))
                        .border(0.75.dp, Color(0x33FFFFFF), CircleShape),
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_theater_replay_10),
                        contentDescription = "Replay 10 seconds",
                        tint = Color(0xFFE2E8F0),
                        modifier = Modifier.size(26.dp),
                    )
                }

                // Hero Frosted Glass Play/Pause Button
                FrostedHeroPlayButton(
                    isPlaying = isPlaying,
                    onClick = onPlayPause,
                )

                // Forward 10s
                IconButton(
                    onClick = onForward10,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(Color(0x660F172A))
                        .border(0.75.dp, Color(0x33FFFFFF), CircleShape),
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_theater_forward_10),
                        contentDescription = "Forward 10 seconds",
                        tint = Color(0xFFE2E8F0),
                        modifier = Modifier.size(26.dp),
                    )
                }
            }



            Spacer(modifier = Modifier.weight(0.4f))
        }

        // Center Buffering Indicator
        if (playbackState == Player.STATE_BUFFERING) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(52.dp),
                    color = Color(0xFFD5A24A),
                    strokeWidth = 3.dp,
                )
            }
        }

        // Playback Error Banner / Overlay
        if (playbackError != null) {
            TheaterErrorOverlay(
                errorMessage = playbackError,
                onRetry = onRetry,
                onClose = onClose,
                isOpenInBrowserVisible = isContainerUnsupported && onOpenInBrowser != null,
                onOpenInBrowser = onOpenInBrowser,
            )
        }
    }
}

// -------------------------------------------------------------
// Clarus Minimal Frosted Glass Audio PiP Screen
// -------------------------------------------------------------
@Composable
private fun ClarusAudioPipScreen(
    title: String,
    isPlaying: Boolean,
    currentPosMs: Long,
    durationMs: Long,
    onPlayPause: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress = if (durationMs > 0L) (currentPosMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f

    val infiniteTransition = rememberInfiniteTransition(label = "AudioPipWave")
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.28318f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "AudioPipWavePhase",
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF080B12))
            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        // Ambient soft copper/indigo glow
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0x33D5A24A),
                        Color(0x184C1D95),
                        Color.Transparent,
                    ),
                    center = Offset(size.width * 0.15f, size.height * 0.5f),
                    radius = size.width * 0.7f,
                ),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Left Art / Harmonic soundwave badge
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0x660F172A))
                    .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val barHeights = listOf(8.dp, 16.dp, 24.dp, 14.dp, 20.dp, 10.dp)
                    barHeights.forEachIndexed { index, targetHeight ->
                        val barHeight = if (isPlaying) {
                            val factor = 0.5f + 0.5f * sin(wavePhase + index.toFloat() * 1.1f)
                            targetHeight * factor
                        } else {
                            targetHeight * 0.35f
                        }
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(barHeight)
                                .clip(RoundedCornerShape(1.5.dp))
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color(0xFFE08A44), Color(0xFFD5A24A), Color(0xFFAAA0D2)),
                                    ),
                                ),
                        )
                    }
                }
            }

            // Track title, status pill, progress bar
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = title,
                    color = Color(0xFFF1F5F9),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(if (isPlaying) Color(0xFF10B981) else Color(0xFF94A3B8)),
                    )
                    Text(
                        text = if (isPlaying) "Playing" else "Paused",
                        color = if (isPlaying) Color(0xFF10B981) else Color(0xFF94A3B8),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    if (durationMs > 0L) {
                        Text(
                            text = "${formatTime(currentPosMs)} / ${formatTime(durationMs)}",
                            color = Color(0xFF64748B),
                            fontSize = 9.sp,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(3.dp))
                // Clean sleek mini progress line
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.5.dp)
                        .clip(RoundedCornerShape(1.5.dp))
                        .background(Color(0x33FFFFFF)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction = progress)
                            .fillMaxHeight()
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Color(0xFFD5A24A), Color(0xFFF59E0B)),
                                ),
                            ),
                    )
                }
            }

            // Play / Pause Mini Button
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color(0x800F172A))
                    .border(1.dp, Color(0x40FFFFFF), CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onPlayPause,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (isPlaying) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(13.dp)
                                .clip(RoundedCornerShape(1.dp))
                                .background(Color.White),
                        )
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(13.dp)
                                .clip(RoundedCornerShape(1.dp))
                                .background(Color.White),
                        )
                    }
                } else {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = "Play",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------
// Frosted Glass Error Overlay
// -------------------------------------------------------------
@Composable
private fun TheaterErrorOverlay(
    errorMessage: String,
    onRetry: () -> Unit,
    onClose: () -> Unit,
    isOpenInBrowserVisible: Boolean = false,
    onOpenInBrowser: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xB3000000)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .padding(32.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xD91E293B))
                .border(1.dp, Color(0x33EF4444), RoundedCornerShape(24.dp))
                .padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Playback Error",
                color = Color(0xFFF87171),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = errorMessage,
                color = Color(0xFFE2E8F0),
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x33FFFFFF))
                        .clickable(onClick = onClose)
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Close", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
                if (isOpenInBrowserVisible && onOpenInBrowser != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF3B82F6))
                            .clickable(onClick = onOpenInBrowser)
                            .padding(horizontal = 18.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("Open in Browser", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFD5A24A))
                        .clickable(onClick = onRetry)
                        .padding(horizontal = 22.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Retry", color = Color(0xFF0F172A), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// -------------------------------------------------------------
// Timecode Formatter Helper
// -------------------------------------------------------------
fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}

private fun isConnectionResetError(error: androidx.media3.common.PlaybackException): Boolean {
    var cause: Throwable? = error
    while (cause != null) {
        if (cause is java.net.SocketException) {
            return true
        }
        val msg = cause.message?.lowercase() ?: ""
        if (msg.contains("connection reset") || msg.contains("socketexception") || msg.contains("broken pipe")) {
            return true
        }
        cause = cause.cause
    }
    return false
}
