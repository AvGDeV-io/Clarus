/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.browser.peek

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import mozilla.components.browser.state.selector.findTabOrCustomTabOrSelectedTab
import mozilla.components.browser.state.selector.selectedTab
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.concept.engine.Engine
import mozilla.components.concept.engine.EngineSession
import mozilla.components.concept.engine.EngineView
import mozilla.components.concept.engine.HitResult
import mozilla.components.feature.contextmenu.ContextMenuUseCases
import mozilla.components.lib.state.ext.flowScoped
import org.mozilla.fenix.components.usecases.FenixBrowserUseCases
import org.mozilla.fenix.theater.ClarusTheaterActivity
import org.mozilla.fenix.theme.ClarusHaptics
import org.mozilla.fenix.theme.FirefoxTheme
import org.mozilla.fenix.theme.Theme
import org.mozilla.fenix.theme.getThemeProvider

/**
 * Link peek: long-press on a link opens a large glass card with live preview
 * and pill actions (New tab / Copy / Share). Swipe left = private tab.
 * Gestures are handled at the raw touch level so release and drag work cleanly.
 */
class TabPeekController(
    private val browserStore: BrowserStore,
    private val fenixBrowserUseCases: FenixBrowserUseCases,
    private val contextMenuUseCases: ContextMenuUseCases,
    private val engine: Engine,
    private val lifecycleOwner: LifecycleOwner,
    private val container: ViewGroup,
    private val engineView: View,
    private val isPrivateSession: () -> Boolean = { false },
    private val onActionRequested: (actionId: String, currentUrl: String, hit: HitResult) -> Unit = { _, _, _ -> },
    private val dismissLegacyMenu: () -> Unit = { },
) {
    private var overlay: ComposeView? = null
    private var hitResultScope: CoroutineScope? = null
    private var previewSession: EngineSession? = null
    private var previewEngineView: EngineView? = null
    private var pendingHit: HitResult? = null
    private var previewTimeoutJob: Job? = null

    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var hasInitialTouch = false
    private var passedCommitThreshold = false
    private var passedPrivateThreshold = false
    private val actionBounds = mutableMapOf<String, Rect>()

    private var uiState by mutableStateOf<TabPeekUiState>(TabPeekUiState.Hidden)

    val isPeekVisible: Boolean
        get() = uiState is TabPeekUiState.Peeking

    fun shouldSuppressContextMenu(): Boolean =
        isPeekVisible || pendingHit != null || uiState is TabPeekUiState.Canceling

    private val previewObserver = object : EngineSession.Observer {
        override fun onFirstContentfulPaint() {
            markPreviewReady()
        }

        override fun onProgress(progress: Int) {
            if (progress >= 70) {
                markPreviewReady()
            }
        }

        override fun onLoadingStateChange(loading: Boolean) {
            if (!loading) {
                markPreviewReady()
            }
        }

        override fun onLocationChange(url: String, hasUserGesture: Boolean) {
            Log.e("PEEK_DEBUG", "preview onLocationChange: url='$url', hasUserGesture=$hasUserGesture")
            val current = uiState
            if (current is TabPeekUiState.Peeking && url.isNotBlank() && url != "about:blank") {
                uiState = current.copy(
                    content = current.content.copy(url = url),
                )
            }
        }

        override fun onTitleChange(title: String) {
            Log.e("PEEK_DEBUG", "preview onTitleChange: title='$title'")
            val current = uiState
            if (current is TabPeekUiState.Peeking && title.isNotBlank()) {
                uiState = current.copy(
                    content = current.content.copy(title = title),
                )
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private val touchListener = View.OnTouchListener { _, event ->
        handleTouchEvent(event)
    }

    private fun handleTouchEvent(event: MotionEvent): Boolean {
        if (!isPeekVisible) {
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                initialTouchX = event.rawX
                initialTouchY = event.rawY
                hasInitialTouch = true
                passedCommitThreshold = false
                passedPrivateThreshold = false
            }
            return false
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                initialTouchX = event.rawX
                initialTouchY = event.rawY
                hasInitialTouch = true
                passedCommitThreshold = false
                passedPrivateThreshold = false
            }

            MotionEvent.ACTION_MOVE -> {
                if (!hasInitialTouch) {
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    hasInitialTouch = true
                }
                val dragUp = (initialTouchY - event.rawY).coerceAtLeast(0f)
                val dragLeft = (initialTouchX - event.rawX).coerceAtLeast(0f)
                val density = container.resources.displayMetrics.density
                val commitThresholdPx = COMMIT_DRAG_THRESHOLD_DP * density
                val privateThresholdPx = PRIVATE_DRAG_THRESHOLD_DP * density

                val current = uiState
                if (current is TabPeekUiState.Peeking) {
                    uiState = current.copy(
                        dragUpPx = dragUp,
                        dragLeftPx = dragLeft,
                        commitThresholdPx = commitThresholdPx,
                        privateThresholdPx = privateThresholdPx,
                    )
                }

                if (dragUp >= commitThresholdPx && dragUp > dragLeft) {
                    if (!passedCommitThreshold) {
                        ClarusHaptics.performTick(engineView)
                        passedCommitThreshold = true
                    }
                } else {
                    passedCommitThreshold = false
                }

                if (dragLeft >= privateThresholdPx && dragLeft > dragUp) {
                    if (!passedPrivateThreshold) {
                        ClarusHaptics.performTick(engineView)
                        passedPrivateThreshold = true
                    }
                } else {
                    passedPrivateThreshold = false
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                val finalX = event.rawX
                val finalY = event.rawY
                val dragUp = if (hasInitialTouch) (initialTouchY - finalY).coerceAtLeast(0f) else 0f
                val dragLeft = if (hasInitialTouch) (initialTouchX - finalX).coerceAtLeast(0f) else 0f
                val density = container.resources.displayMetrics.density
                val commitThresholdPx = COMMIT_DRAG_THRESHOLD_DP * density
                val privateThresholdPx = PRIVATE_DRAG_THRESHOLD_DP * density

                val hitAction = findActionAt(finalX, finalY)
                when {
                    hitAction != null -> {
                        val hit = pendingHit
                        if (hit != null) {
                            ClarusHaptics.performClick(engineView)
                            val currentUrl = (uiState as? TabPeekUiState.Peeking)?.content?.url ?: hit.getUrl()
                            onActionRequested(hitAction, currentUrl, hit)
                            teardown()
                        } else {
                            cancelPeek()
                        }
                    }

                    dragUp >= commitThresholdPx && dragUp > dragLeft -> {
                        commit(private = false)
                    }

                    dragLeft >= privateThresholdPx && dragLeft > dragUp -> {
                        commit(private = true)
                    }

                    else -> {
                        cancelPeek()
                    }
                }
                hasInitialTouch = false
                passedCommitThreshold = false
                passedPrivateThreshold = false
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                cancelPeek()
                hasInitialTouch = false
                passedCommitThreshold = false
                passedPrivateThreshold = false
                return true
            }
        }
        return true
    }

    private fun findActionAt(rawX: Float, rawY: Float): String? {
        val loc = IntArray(2)
        container.getLocationInWindow(loc)
        val screenLoc = IntArray(2)
        container.getLocationOnScreen(screenLoc)
        val windowX = rawX - (screenLoc[0] - loc[0])
        val windowY = rawY - (screenLoc[1] - loc[1])

        val slopPx = 36f
        for ((id, bounds) in actionBounds) {
            if (windowX >= (bounds.left - slopPx) &&
                windowX <= (bounds.right + slopPx) &&
                windowY >= (bounds.top - slopPx) &&
                windowY <= (bounds.bottom + slopPx)
            ) {
                return id
            }
        }
        return null
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun attachTouchListeners(view: View) {
        view.setOnTouchListener(touchListener)
    }

    private fun detachTouchListeners(view: View) {
        view.setOnTouchListener(null)
    }

    fun start() {
        attachTouchListeners(engineView)
        hitResultScope?.cancel()
        hitResultScope = browserStore.flowScoped(dispatcher = Dispatchers.Main) { flow ->
            flow.map { state -> state.findTabOrCustomTabOrSelectedTab(null)?.content?.hitResult }
                .distinctUntilChanged()
                .collect { hit ->
                    if (hit != null && hit.isPeekableLink() && pendingHit != hit) {
                        openPeek(hit)
                    }
                }
        }
    }

    fun stop() {
        hitResultScope?.cancel()
        hitResultScope = null
        detachTouchListeners(engineView)
        teardown()
    }

    private fun openPeek(hit: HitResult) {
        dismissLegacyMenu()
        ClarusHaptics.performTick(engineView)

        pendingHit = hit
        val currentTabUrl = browserStore.state.selectedTab?.content?.url
        val rawHitUrl = hit.getUrl()
        val url = resolvePeekUrl(rawHitUrl, currentTabUrl)
        Log.e("PEEK_DEBUG", "=== PEEK TRIGGERED ===")
        Log.e("PEEK_DEBUG", "hit object: $hit")
        Log.e("PEEK_DEBUG", "hit class: ${hit::class.java.name}")
        Log.e("PEEK_DEBUG", "hit.src: '$rawHitUrl'")
        Log.e("PEEK_DEBUG", "currentTabUrl: '$currentTabUrl'")
        Log.e("PEEK_DEBUG", "resolved url: '$url'")
        val isDownloadable = isDownloadableUrl(url)
        val primaryAction = if (isDownloadable) {
            TabPeekAction(id = ACTION_DOWNLOAD, label = "Download", isPrimary = true)
        } else {
            TabPeekAction(id = ACTION_OPEN_TAB, label = "New tab", isPrimary = true)
        }
        val actions = listOf(
            TabPeekAction(id = ACTION_COPY, label = "Copy"),
            TabPeekAction(id = ACTION_SHARE, label = "Share"),
            primaryAction,
        )
        val density = container.resources.displayMetrics.density
        uiState = TabPeekUiState.Peeking(
            content = TabPeekContent(url = url, title = hit.linkLabel()),
            actions = actions,
            commitThresholdPx = COMMIT_DRAG_THRESHOLD_DP * density,
            privateThresholdPx = PRIVATE_DRAG_THRESHOLD_DP * density,
        )
        startPreview(url)
        ensureOverlay()
    }

    private fun startPreview(url: String) {
        discardPreview()
        try {
            val context = container.context
            val activity = context.findActivity() ?: (context as? Activity)
            val ev = engine.createView(context)
            ev.setActivityContext(activity)
            previewEngineView = ev

            val session = engine.createSession(private = isPrivateSession())
            // Clear requestInterceptor so the preview session does not intercept redirects
            // as AppLinks (e.g. YouTube app redirects which cause GeckoView to deny the load and show '302 Moved').
            session.settings.requestInterceptor = null
            val isDesktop = browserStore.state.selectedTab?.content?.desktopMode ?: false
            session.toggleDesktopMode(enable = isDesktop, reload = false)
            previewSession = session
            session.register(previewObserver)

            ev.render(session)
            session.loadUrl(url)

            previewTimeoutJob?.cancel()
            previewTimeoutJob = lifecycleOwner.lifecycleScope.launch {
                delay(4000)
                markPreviewReady()
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to start preview for url: $url", e)
        }
    }

    private fun bindPreviewHost(host: FrameLayout) {
        val ev = previewEngineView ?: return
        val session = previewSession ?: return
        val view = ev.asView()
        (view.parent as? ViewGroup)?.removeView(view)
        host.removeAllViews()
        host.addView(
            view,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
        view.post {
            try {
                ev.render(session)
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to re-render preview session", e)
            }
        }
    }

    private fun markPreviewReady() {
        val current = uiState
        if (current is TabPeekUiState.Peeking && !current.previewReady) {
            uiState = current.copy(previewReady = true)
        }
    }

    private fun discardPreview() {
        previewTimeoutJob?.cancel()
        previewTimeoutJob = null
        previewSession?.unregister(previewObserver)
        try {
            previewEngineView?.release()
        } catch (e: Throwable) {
            Log.w(TAG, "Error releasing previewEngineView", e)
        }
        try {
            previewSession?.close()
        } catch (e: Throwable) {
            Log.w(TAG, "Error closing previewSession", e)
        }
        previewEngineView = null
        previewSession = null
    }

    private fun commit(private: Boolean) {
        val state = uiState as? TabPeekUiState.Peeking ?: return
        Log.e("PEEK_DEBUG", "commit: state.content.url='${state.content.url}', private=$private")
        uiState = TabPeekUiState.Committing
        ClarusHaptics.performCommit(engineView)
        consumeHit()
        fenixBrowserUseCases.loadUrlOrSearch(
            searchTermOrURL = state.content.url,
            newTab = true,
            private = private,
        )
        teardown()
    }

    private fun cancelPeek() {
        val wasVisible = uiState is TabPeekUiState.Peeking
        uiState = TabPeekUiState.Canceling
        if (wasVisible) ClarusHaptics.performTick(engineView)
        consumeHit()
        lifecycleOwner.lifecycleScope.launch {
            delay(SNAP_SHUT_MS.toLong())
            teardown()
        }
    }

    private fun consumeHit() {
        browserStore.state.selectedTab?.id?.let { contextMenuUseCases.consumeHitResult(it) }
        pendingHit = null
    }

    private fun teardown() {
        discardPreview()
        removeOverlay()
        uiState = TabPeekUiState.Hidden
        pendingHit = null
        hasInitialTouch = false
        passedCommitThreshold = false
        passedPrivateThreshold = false
        actionBounds.clear()
    }

    private fun ensureOverlay() {
        if (overlay != null) return
        val cv = ComposeView(container.context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setOnTouchListener(touchListener)
            setContent {
                val theme: Theme = getThemeProvider().provideTheme()
                FirefoxTheme(theme = theme) {
                    TabPeekOverlay(
                        state = uiState,
                        theme = theme,
                        previewFactory = { host -> bindPreviewHost(host) },
                        onAction = { id ->
                            val hit = pendingHit
                            if (hit != null) {
                                ClarusHaptics.performClick(engineView)
                                val currentUrl = (uiState as? TabPeekUiState.Peeking)?.content?.url ?: hit.getUrl()
                                onActionRequested(id, currentUrl, hit)
                                teardown()
                            }
                        },
                        onDismiss = { cancelPeek() },
                        onCommit = { commit(private = false) },
                        onOpenPrivate = { commit(private = true) },
                        onDrag = { up, left ->
                            val current = uiState
                            if (current is TabPeekUiState.Peeking) {
                                uiState = current.copy(dragUpPx = up, dragLeftPx = left)
                            }
                        },
                        onActionBounds = { id, bounds ->
                            actionBounds[id] = bounds
                        },
                    )
                }
            }
        }
        overlay = cv
        val lp = if (container is CoordinatorLayout) {
            CoordinatorLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ).apply {
                behavior = object : CoordinatorLayout.Behavior<View>() {
                    override fun onInterceptTouchEvent(parent: CoordinatorLayout, child: View, ev: MotionEvent): Boolean {
                        if (isPeekVisible) {
                            handleTouchEvent(ev)
                            return true
                        }
                        return false
                    }

                    override fun onTouchEvent(parent: CoordinatorLayout, child: View, ev: MotionEvent): Boolean {
                        return handleTouchEvent(ev)
                    }
                }
            }
        } else {
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }
        container.addView(cv, lp)
    }

    private fun removeOverlay() {
        overlay?.setOnTouchListener(null)
        overlay?.let { container.removeView(it) }
        overlay = null
    }

    companion object {
        private const val TAG = "TabPeekController"
        const val ACTION_OPEN_TAB = "open_tab"
        const val ACTION_COPY = "copy_link"
        const val ACTION_SHARE = "share_link"
        const val ACTION_DOWNLOAD = "download_file"
    }
}

private val DOWNLOADABLE_EXTENSIONS = setOf(
    // Audio
    "mp3", "m4a", "aac", "flac", "wav", "wave", "ogg", "oga", "opus", "weba",
    // Video
    "mp4", "m4v", "mkv", "webm", "mov", "3gp", "ts", "m3u8",
    // Archives, Documents & Packages
    "pdf", "zip", "rar", "7z", "tar", "gz", "apk", "bin", "exe", "dmg", "iso",
)

fun isDownloadableUrl(url: String): Boolean {
    if (url.isBlank()) return false
    if (ClarusTheaterActivity.isAudioUrl(url) ||
        ClarusTheaterActivity.isVideoUrl(url) ||
        ClarusTheaterActivity.isPlayableMedia(url)
    ) {
        return true
    }
    val clean = url.substringBefore('?').substringBefore('#').lowercase()
    val ext = clean.substringAfterLast('.', "")
    return ext in DOWNLOADABLE_EXTENSIONS
}

private fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

fun cleanPeekUrl(rawUrl: String): String {
    Log.e("PEEK_DEBUG", "cleanPeekUrl: rawUrl='$rawUrl'")
    if (rawUrl.contains("google.com/url?") || rawUrl.contains("google.co/url?")) {
        runCatching {
            val uri = Uri.parse(rawUrl)
            val paramUrl = uri.getQueryParameter("url")
            val paramQ = uri.getQueryParameter("q")
            val target = paramUrl ?: paramQ
            Log.e("PEEK_DEBUG", "cleanPeekUrl: paramUrl='$paramUrl', paramQ='$paramQ', chosen target='$target'")
            if (!target.isNullOrBlank() && (target.startsWith("http://") || target.startsWith("https://"))) {
                Log.e("PEEK_DEBUG", "cleanPeekUrl: RETURNING target='$target'")
                return target
            }
        }.onFailure { Log.e("PEEK_DEBUG", "cleanPeekUrl parsing failed", it) }
    }
    Log.e("PEEK_DEBUG", "cleanPeekUrl: RETURNING UNMODIFIED rawUrl='$rawUrl'")
    return rawUrl
}

fun resolvePeekUrl(rawUrl: String, baseUrl: String?): String {
    val trimmed = rawUrl.trim()
    if (trimmed.isBlank()) return trimmed
    if (trimmed.startsWith("http://") || trimmed.startsWith("https://") ||
        trimmed.startsWith("about:") || trimmed.startsWith("javascript:") ||
        trimmed.startsWith("data:")
    ) {
        return cleanPeekUrl(trimmed)
    }
    if (baseUrl.isNullOrBlank()) return cleanPeekUrl(trimmed)
    return try {
        val baseUri = java.net.URI.create(baseUrl)
        val resolved = baseUri.resolve(trimmed).toString()
        cleanPeekUrl(resolved)
    } catch (e: Throwable) {
        cleanPeekUrl(trimmed)
    }
}

fun HitResult.isPeekableLink(): Boolean {
    val url = getUrl()
    return url.isNotBlank() &&
        !url.startsWith("about:") &&
        !url.startsWith("javascript:") &&
        (this is HitResult.UNKNOWN || this is HitResult.IMAGE_SRC || this is HitResult.IMAGE)
}

private fun HitResult.linkLabel(): String? = when (this) {
    is HitResult.UNKNOWN -> linkText
    is HitResult.IMAGE -> title
    else -> null
}
