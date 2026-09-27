/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.theater

import android.content.Context
import android.util.Log
import androidx.annotation.VisibleForTesting
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import mozilla.components.browser.state.state.BrowserState
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.concept.engine.Engine
import mozilla.components.concept.engine.EngineSession
import mozilla.components.concept.engine.webextension.MessageHandler
import mozilla.components.concept.engine.webextension.Port
import mozilla.components.concept.engine.webextension.WebExtension
import mozilla.components.lib.state.ext.flowScoped
import mozilla.components.support.base.log.logger.Logger
import mozilla.components.support.ktx.kotlinx.coroutines.flow.filterChanged
import org.json.JSONObject

private const val TAG = "ClarusTheater"
private const val EXTENSION_ID = "theater@clarus.org"
private const val EXTENSION_URL = "resource://android/assets/extensions/clarus-theater/"
private const val EXTENSION_PORT = "ClarusTheater"

/**
 * Data representation of a video entering fullscreen on a webpage, extracted by the
 * Clarus Theater content script detection layer.
 */
data class ClarusVideoPayload(
    val isSupported: Boolean,
    val reason: String,
    val trigger: String,
    val src: String,
    val currentTime: Double,
    val duration: Double,
    val paused: Boolean,
    val muted: Boolean,
    val volume: Double,
    val videoWidth: Int,
    val videoHeight: Int,
    val title: String,
    val poster: String,
    val qualityBadge: String,
    val pageUrl: String,
    val isLive: Boolean = false,
    val referrer: String = "",
    val cookies: String = "",
)

/**
 * Manages the Clarus Theater built-in content script extension and coordinates
 * video detection, extraction, and Safari-style bidirectional handoff.
 */
class ClarusTheaterFeature(
    private val context: Context,
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main,
) : MessageHandler {

    private val logger = Logger(TAG)
    private var activePort: Port? = null

    private val _currentPayload = MutableStateFlow<ClarusVideoPayload?>(null)
    val currentPayload: StateFlow<ClarusVideoPayload?> = _currentPayload.asStateFlow()

    var onFullscreenRequestedListener: ((ClarusVideoPayload) -> Unit)? = null
    var onFullscreenExitedListener: (() -> Unit)? = null

    /**
     * Installs the Clarus Theater content-script WebExtension into the browser engine.
     */
    fun install(engine: Engine, store: BrowserStore) {
        engine.installBuiltInWebExtension(
            id = EXTENSION_ID,
            url = EXTENSION_URL,
            onSuccess = { extension ->
                Log.d(TAG, "Clarus Theater detection WebExtension installed successfully")
                store.flowScoped(dispatcher = mainDispatcher) { flow ->
                    subscribeToUpdates(flow, extension)
                }
            },
            onError = { throwable ->
                Log.e(TAG, "Failed to install Clarus Theater detection WebExtension", throwable)
            },
        )
    }

    private suspend fun subscribeToUpdates(
        flow: Flow<BrowserState>,
        extension: WebExtension,
    ) {
        flow.map { it.tabs }
            .filterChanged { it.engineState.engineSession }
            .collect { state ->
                val engineSession = state.engineState.engineSession ?: return@collect

                if (extension.hasContentMessageHandler(engineSession, EXTENSION_PORT)) {
                    return@collect
                }

                extension.registerContentMessageHandler(engineSession, EXTENSION_PORT, this)
            }
    }

    override fun onPortConnected(port: Port) {
        activePort = port
        Log.d(TAG, "Content script connected to native port: $EXTENSION_PORT")
    }

    override fun onPortDisconnected(port: Port) {
        if (activePort == port) {
            activePort = null
            Log.d(TAG, "Content script disconnected from native port: $EXTENSION_PORT")
        }
    }

    override fun onPortMessage(message: Any, port: Port) {
        handleIncomingMessage(message)
    }

    override fun onMessage(message: Any, source: EngineSession?): Any? {
        handleIncomingMessage(message)
        return ""
    }

    private fun handleIncomingMessage(message: Any) {
        if (message !is JSONObject) {
            return
        }

        when (message.optString("action")) {
            "FULLSCREEN_ENTER" -> {
                val payload = parsePayload(message)
                _currentPayload.value = payload

                Log.d(
                    TAG,
                    """
                    |>>> Clarus Theater: Video Fullscreen Detected <<<
                    |  - Supported: ${payload.isSupported} (Reason: ${payload.reason})
                    |  - Title: '${payload.title}'
                    |  - Stream URL: '${payload.src}'
                    |  - Position: ${payload.currentTime}s / ${payload.duration}s (Paused: ${payload.paused})
                    |  - Dimensions: ${payload.videoWidth}x${payload.videoHeight} (Badge: '${payload.qualityBadge}')
                    |  - Poster: '${payload.poster}'
                    |  - Page URL: '${payload.pageUrl}'
                    """.trimMargin(),
                )

                onFullscreenRequestedListener?.invoke(payload)
            }

            "FULLSCREEN_EXIT" -> {
                Log.d(TAG, ">>> Clarus Theater: Video Fullscreen Exited on Webpage <<<")
                _currentPayload.value = null
                onFullscreenExitedListener?.invoke()
            }
        }
    }

    private fun parsePayload(json: JSONObject): ClarusVideoPayload {
        return ClarusVideoPayload(
            isSupported = json.optBoolean("isSupported", false),
            reason = json.optString("reason", "UNKNOWN"),
            trigger = json.optString("trigger", "UNKNOWN"),
            src = json.optString("src", ""),
            currentTime = json.optDouble("currentTime", 0.0),
            duration = json.optDouble("duration", -1.0),
            paused = json.optBoolean("paused", false),
            muted = json.optBoolean("muted", false),
            volume = json.optDouble("volume", 1.0),
            videoWidth = json.optInt("videoWidth", 0),
            videoHeight = json.optInt("videoHeight", 0),
            title = json.optString("title", ""),
            poster = json.optString("poster", ""),
            qualityBadge = json.optString("qualityBadge", ""),
            pageUrl = json.optString("pageUrl", ""),
            isLive = json.optBoolean("isLive", false) ||
                json.optDouble("duration", -1.0) == Double.POSITIVE_INFINITY ||
                json.optString("reason") == "DIRECT_HLS_MANIFEST",
            referrer = json.optString("referrer", json.optString("pageUrl", "")),
            cookies = json.optString("cookies", ""),
        )
    }

    /**
     * Commands the in-page video to synchronize timestamp and playback state upon player close.
     */
    fun seekAndSync(currentTimeSec: Double, paused: Boolean) {
        try {
            val msg = JSONObject().apply {
                put("action", "SEEK_AND_SYNC")
                put("currentTime", currentTimeSec)
                put("paused", paused)
            }
            activePort?.postMessage(msg)
            Log.d(TAG, "Dispatched SEEK_AND_SYNC to webpage: time=$currentTimeSec, paused=$paused")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to dispatch SEEK_AND_SYNC to webpage", e)
        }
    }

    /**
     * Commands the webpage to exit its in-page fullscreen state cleanly.
     */
    fun exitWebFullscreen() {
        try {
            val msg = JSONObject().apply {
                put("action", "EXIT_FULLSCREEN")
            }
            activePort?.postMessage(msg)
            Log.d(TAG, "Dispatched EXIT_FULLSCREEN to webpage")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to dispatch EXIT_FULLSCREEN to webpage", e)
        }
    }

    /**
     * Commands the in-page video to pause while Clarus Theater is playing.
     */
    fun pauseWebVideo() {
        try {
            val msg = JSONObject().apply {
                put("action", "PAUSE_WEB_VIDEO")
            }
            activePort?.postMessage(msg)
            Log.d(TAG, "Dispatched PAUSE_WEB_VIDEO to webpage")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to dispatch PAUSE_WEB_VIDEO to webpage", e)
        }
    }
}
