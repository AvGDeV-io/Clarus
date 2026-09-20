/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.components.toolbar

import android.graphics.Matrix
import android.graphics.SweepGradient
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams
import androidx.core.view.isVisible
import mozilla.components.browser.state.action.AwesomeBarAction
import mozilla.components.browser.state.state.CustomTabSessionState
import mozilla.components.browser.state.state.ExternalAppType
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.compose.base.utils.BackInvokedHandler
import mozilla.components.compose.browser.toolbar.BrowserToolbar
import mozilla.components.compose.browser.toolbar.store.BrowserToolbarAction.ToolbarGravityUpdated
import mozilla.components.compose.browser.toolbar.store.BrowserToolbarStore
import mozilla.components.compose.browser.toolbar.store.ToolbarGravity
import mozilla.components.compose.browser.toolbar.store.ToolbarGravity.Bottom
import mozilla.components.compose.browser.toolbar.store.ToolbarGravity.Top
import mozilla.components.concept.engine.EngineView
import mozilla.components.concept.toolbar.ScrollableToolbar
import mozilla.components.feature.toolbar.ToolbarBehaviorController
import mozilla.components.lib.state.ext.observeAsComposableState
import mozilla.components.support.ktx.android.view.findViewInHierarchy
import mozilla.components.support.utils.KeyboardState
import mozilla.components.support.utils.ext.isKeyboardVisible
import mozilla.components.support.utils.keyboardAsState
import mozilla.components.ui.widgets.behavior.DependencyGravity
import mozilla.components.ui.widgets.behavior.EngineViewScrollingBehavior
import mozilla.components.ui.widgets.behavior.EngineViewScrollingBehaviorFactory
import org.mozilla.fenix.browser.store.BrowserScreenStore
import org.mozilla.fenix.components.AppStore
import org.mozilla.fenix.components.appstate.AppAction.SearchAction.SearchEnded
import org.mozilla.fenix.components.toolbar.ToolbarPosition.BOTTOM
import org.mozilla.fenix.components.toolbar.ToolbarPosition.TOP
import org.mozilla.fenix.theme.FirefoxTheme
import org.mozilla.fenix.theme.Theme
import org.mozilla.fenix.theme.getThemeProvider
import org.mozilla.fenix.theme.glass.ClarusGlassSurface
import org.mozilla.fenix.theme.glass.ClarusGlassTokens
import org.mozilla.fenix.utils.Settings
import mozilla.components.browser.state.selector.selectedTab

/**
 * A wrapper over the [BrowserToolbar] composable that owns the toolbar [View] and its
 * scrolling behaviour.
 *
 * @param activity [AppCompatActivity] hosting the toolbar.
 * @param container [ViewGroup] which will serve as parent of this View.
 * @param toolbarStore [BrowserToolbarStore] containing the composable toolbar state.
 * @param browserScreenStore [BrowserScreenStore] used for integration with other browser screen functionalities.
 * @param appStore [AppStore] used for integration with other application features.
 * @param browserStore [BrowserStore] used for observing the browsing details.
 * @param settings [Settings] object to get the toolbar position and other settings.
 * @param customTabSession [CustomTabSessionState] if the toolbar is shown in a custom tab.
 * @param tabStripContent Composable content for the tab strip.
 * @param searchSuggestionsContent [Composable] as the search suggestions content to be displayed
 * together with this toolbar.
 * @param navigationBarContent [Composable] content for the navigation bar.
 */
@Suppress("LongParameterList")
class BrowserToolbarComposable(
    private val activity: AppCompatActivity,
    private val container: ViewGroup,
    private val toolbarStore: BrowserToolbarStore,
    private val browserScreenStore: BrowserScreenStore,
    private val appStore: AppStore,
    private val browserStore: BrowserStore,
    private val settings: Settings,
    private val customTabSession: CustomTabSessionState? = null,
    private val tabStripContent: @Composable () -> Unit,
    private val searchSuggestionsContent: @Composable (Modifier) -> Unit,
    private val navigationBarContent: (@Composable () -> Unit)?,
) : ScrollableToolbar {
    init {
        if (!settings.shouldUseMinimalBottomToolbarWhenEnteringText) {
            setupShowingToolbarsAfterKeyboardHidden()
        }

        // Reset the toolbar position whenever coming back to browsing
        // like after changing the toolbar position in settings.
        toolbarStore.dispatch(
            ToolbarGravityUpdated(
                buildToolbarGravityConfig(),
            ),
        )
    }

    val layout: View = ScrollableToolbarComposeView(activity, this) {
        val isSearching = toolbarStore.observeAsComposableState { it.isEditMode() }.value
        val shouldShowTabStrip: Boolean = remember { shouldShowTabStrip() }
        val customColors = browserScreenStore.observeAsComposableState { it.customTabColors }
        val shouldUseBottomToolbar = remember(settings) { settings.shouldUseBottomToolbar }

        val toolbarState by toolbarStore.stateFlow.collectAsState()
        val toolbarCFR = toolbarState.displayState.cfr

        DisposableEffect(activity) {
            val toolbarController = ToolbarBehaviorController(
                toolbar = this@BrowserToolbarComposable,
                store = browserStore,
                customTabId = customTabSession?.id,
            )
            toolbarController.start()
            onDispose { toolbarController.stop() }
        }

        BackInvokedHandler(isSearching) {
            appStore.dispatch(SearchEnded)
            browserStore.dispatch(AwesomeBarAction.EngagementFinished(abandoned = true))
        }

        FirefoxTheme {
            val theme = getThemeProvider().provideTheme()
            val isDarkTheme = theme == Theme.Dark || theme == Theme.Private

            val selectedTab = browserStore.observeAsComposableState { it.selectedTab }.value
            val isPrivate = theme == Theme.Private || selectedTab?.content?.private == true
            val isTabLoading = selectedTab?.content?.loading == true
            val progress = toolbarState.displayState.progressBarConfig?.progress ?: 0
            val isLoading = isTabLoading || (progress in 1..99)

            val clarusSurface = when {
                isPrivate -> ClarusGlassTokens.Colors.DarkDeep
                isDarkTheme -> ClarusGlassTokens.Colors.DarkBase
                else -> ClarusGlassTokens.Colors.LightBase
            }
            val clarusUrlBarBg = when {
                isPrivate -> Color(0xFF16141D).copy(alpha = 0.90f)
                isDarkTheme -> Color.White.copy(alpha = 0.08f)
                else -> Color(0xFF24201F).copy(alpha = 0.05f)
            }
            val clarusOnSurface = when {
                isPrivate -> Color(0xFFFEF3C7)
                isDarkTheme -> ClarusGlassTokens.Colors.DarkTextPrimary
                else -> ClarusGlassTokens.Colors.InkEspresso
            }
            val clarusOnSurfaceVariant = when {
                isPrivate -> Color(0xFFF59E0B)
                isDarkTheme -> ClarusGlassTokens.Colors.DarkTextSecondary
                else -> ClarusGlassTokens.Colors.InkMuted
            }

            val materialColors = MaterialTheme.colorScheme
            val colorScheme = remember(customColors.value, materialColors, isDarkTheme, isPrivate) {
                materialColors.copy(
                    // Toolbar background
                    surface = customColors.value?.toolbarColor?.let { Color(it) }
                        ?: Color.Transparent,
                    // Page origin background
                    surfaceContainerHighest = when (customTabSession) {
                        // show a different background only for normal tabs
                        null -> clarusUrlBarBg
                        else -> customColors.value?.toolbarColor?.let { Color(it) }
                            ?: clarusSurface
                    },
                    onSurface = customColors.value?.readableColor?.let { Color(it) }
                        ?: clarusOnSurface,
                    onSurfaceVariant =
                        customColors.value?.secondaryReadableColor?.let { Color(it) }
                            ?: clarusOnSurfaceVariant,
                    outline = if (isPrivate) Color(0x40F59E0B) else if (isDarkTheme) Color(0xFF302B2A) else Color(0xFFEAE1D4),
                    outlineVariant = if (isPrivate) Color(0xFFF59E0B) else if (isDarkTheme) Color(0xFF36335F) else Color(0xFF9CA8B7),
                )
            }

            val clarusShapes = MaterialTheme.shapes.copy(
                extraLarge = RoundedCornerShape(20.dp),
                large = RoundedCornerShape(18.dp),
                medium = RoundedCornerShape(16.dp),
                small = RoundedCornerShape(12.dp),
                extraSmall = RoundedCornerShape(8.dp),
            )

            MaterialTheme(colorScheme = colorScheme, shapes = clarusShapes) {
                when (shouldShowTabStrip) {
                    true -> Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight(),
                    ) {
                        tabStripContent()
                        ClarusToolbarContainer(
                            isLoading = isLoading,
                            isDarkTheme = isDarkTheme,
                            isPrivate = isPrivate,
                            customColor = customColors.value?.toolbarColor?.let { Color(it) },
                        ) {
                            BrowserToolbar(
                                store = toolbarStore,
                                cfr = toolbarCFR,
                                useMinimalBottomToolbarWhenEnteringText =
                                    settings.shouldUseMinimalBottomToolbarWhenEnteringText,
                            )
                        }
                        if (customTabSession == null) {
                            searchSuggestionsContent(Modifier.weight(1f))
                        }
                    }

                    false -> Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight(),
                    ) {
                        if (shouldUseBottomToolbar) {
                            if (customTabSession == null) {
                                searchSuggestionsContent(Modifier.weight(1f))
                            }
                            ClarusToolbarContainer(
                                isLoading = isLoading,
                                isDarkTheme = isDarkTheme,
                                isPrivate = isPrivate,
                                customColor = customColors.value?.toolbarColor?.let { Color(it) },
                            ) {
                                BrowserToolbar(
                                    store = toolbarStore,
                                    cfr = toolbarCFR,
                                    useMinimalBottomToolbarWhenEnteringText =
                                        settings.shouldUseMinimalBottomToolbarWhenEnteringText,
                                )
                            }
                            navigationBarContent?.invoke()
                        } else {
                            ClarusToolbarContainer(
                                isLoading = isLoading,
                                isDarkTheme = isDarkTheme,
                                isPrivate = isPrivate,
                                customColor = customColors.value?.toolbarColor?.let { Color(it) },
                            ) {
                                BrowserToolbar(
                                    store = toolbarStore,
                                    cfr = toolbarCFR,
                                    useMinimalBottomToolbarWhenEnteringText =
                                        settings.shouldUseMinimalBottomToolbarWhenEnteringText,
                                )
                            }
                            if (customTabSession == null) {
                                searchSuggestionsContent(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }.apply {
        if (!shouldShowTabStrip()) {
            val params = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)

            when (settings.toolbarPosition) {
                TOP -> params.gravity = Gravity.TOP
                BOTTOM -> params.gravity = Gravity.BOTTOM
            }

            layoutParams = params
        }
    }

    init {
        container.addView(layout)
        setToolbarBehavior(settings.toolbarPosition)
    }

    @VisibleForTesting
    internal val isPwaTabOrTwaTab: Boolean
        get() = customTabSession?.config?.externalAppType == ExternalAppType.PROGRESSIVE_WEB_APP ||
            customTabSession?.config?.externalAppType == ExternalAppType.TRUSTED_WEB_ACTIVITY

    override fun expand() {
        // expand only for normal tabs and custom tabs not for PWA or TWA
        if (isPwaTabOrTwaTab) {
            return
        }

        (layout.layoutParams as CoordinatorLayout.LayoutParams).apply {
            (behavior as? EngineViewScrollingBehavior)?.forceExpand()
        }
    }

    override fun collapse() {
        // collapse only for normal tabs and custom tabs not for PWA or TWA. Mirror expand()
        if (isPwaTabOrTwaTab) {
            return
        }

        (layout.layoutParams as CoordinatorLayout.LayoutParams).apply {
            (behavior as? EngineViewScrollingBehavior)?.forceCollapse()
        }
    }

    override fun enableScrolling() {
        if (!container.isKeyboardVisible()) {
            (layout.layoutParams as CoordinatorLayout.LayoutParams).apply {
                (behavior as? EngineViewScrollingBehavior)?.enableScrolling()
            }
        }
    }

    override fun disableScrolling() {
        (layout.layoutParams as CoordinatorLayout.LayoutParams).apply {
            (behavior as? EngineViewScrollingBehavior)?.disableScrolling()
        }
    }

    internal fun gone() {
        layout.isVisible = false
    }

    internal fun visible() {
        layout.isVisible = true
    }

    /**
     * Sets whether the toolbar will have a dynamic behavior (to be scrolled) or not.
     *
     * This will intrinsically check and disable the dynamic behavior if
     *  - this is disabled in app settings
     *  - toolbar is placed at the bottom and tab shows a PWA or TWA
     *
     *  Also if the user has not explicitly set a toolbar position and has a screen reader enabled
     *  the toolbar will be placed at the top and in a fixed position.
     *
     * @param toolbarPosition [ToolbarPosition] to set the toolbar to.
     * @param shouldDisableScroll force disable of the dynamic behavior irrespective of the intrinsic checks.
     */
    fun setToolbarBehavior(toolbarPosition: ToolbarPosition, shouldDisableScroll: Boolean = false) {
        when (toolbarPosition) {
            ToolbarPosition.BOTTOM -> {
                if (settings.isDynamicToolbarEnabled &&
                    !settings.shouldUseFixedTopToolbar
                ) {
                    setDynamicToolbarBehavior(true)
                } else {
                    expandToolbarAndMakeItFixed()
                }
            }
            ToolbarPosition.TOP -> {
                if (settings.shouldUseFixedTopToolbar ||
                    !settings.isDynamicToolbarEnabled ||
                    shouldDisableScroll
                ) {
                    expandToolbarAndMakeItFixed()
                } else {
                    setDynamicToolbarBehavior(false)
                }
            }
        }
    }

    @VisibleForTesting
    internal fun expandToolbarAndMakeItFixed() {
        expand()
        (layout.layoutParams as CoordinatorLayout.LayoutParams).apply {
            behavior = null
        }
    }

    @VisibleForTesting
    internal fun setDynamicToolbarBehavior(isToolbarAtBottom: Boolean) {
        (container.findViewInHierarchy { it is EngineView } as? EngineView)?.let { engineView ->
            (layout.layoutParams as CoordinatorLayout.LayoutParams).apply {
                behavior = EngineViewScrollingBehaviorFactory(
                    useScrollData = settings.useNewDynamicToolbarBehaviour,
                ).build(
                    engineView = engineView,
                    dependency = layout,
                    dependencyGravity = when (isToolbarAtBottom) {
                        true -> DependencyGravity.Bottom
                        false -> DependencyGravity.Top
                    },
                )
            }
        }
    }

    private fun shouldShowTabStrip() = customTabSession == null && settings.isTabStripEnabled

    private fun setupShowingToolbarsAfterKeyboardHidden() {
        container.addView(
            ComposeView(container.context).apply {
                setContent {
                    val keyboardState by keyboardAsState()
                    LaunchedEffect(keyboardState) {
                        if (keyboardState == KeyboardState.Closed) {
                            expand()
                        }
                    }
                }
            },
        )
    }

    private fun buildToolbarGravityConfig(): ToolbarGravity = when (settings.shouldUseBottomToolbar) {
        true -> Bottom
        false -> Top
    }
}

/**
 * Rotating sweep gradient shader brush used for the Clarus perimeter-tracing RGB loading strip.
 */
private class ClarusSweepGradientBrush(
    private val colors: List<Color>,
    private val rotationAngle: Float,
) : ShaderBrush() {
    override fun createShader(size: Size): Shader {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val colorInts = IntArray(colors.size) { colors[it].toArgb() }
        val shader = SweepGradient(cx, cy, colorInts, null)
        val matrix = Matrix()
        matrix.postRotate(rotationAngle, cx, cy)
        shader.setLocalMatrix(matrix)
        return shader
    }
}

/**
 * Clarus floating glassmorphic toolbar container with perimeter-tracing RGB loading strip
 * and ambient multi-color loading aura glowing behind and through the glass surface.
 */
@Composable
private fun ClarusToolbarContainer(
    isLoading: Boolean,
    isDarkTheme: Boolean,
    isPrivate: Boolean = false,
    customColor: Color? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val auraAlpha by animateFloatAsState(
        targetValue = if (isLoading) 1f else 0f,
        animationSpec = tween(durationMillis = 500, easing = LinearOutSlowInEasing),
        label = "ClarusAuraAlpha",
    )

    val infiniteTransition = rememberInfiniteTransition(label = "ClarusAuraTransition")
    val auraPulse by infiniteTransition.animateFloat(
        initialValue = 0.40f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "ClarusAuraPulse",
    )

    val sweepAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "ClarusRgbSweepAngle",
    )

    // Full-spectrum chromatic palette for normal browsing
    val normalRgbColors = remember {
        listOf(
            Color(0xFFFF3B30), // Red
            Color(0xFFFF9500), // Orange / Copper
            Color(0xFFFFCC00), // Amber Yellow
            Color(0xFF34C759), // Mint Green
            Color(0xFF00C7BE), // Cyan Teal
            Color(0xFF0A84FF), // Electric Soft Blue
            Color(0xFFAF52DE), // Velvet Violet
            Color(0xFFFF2D55), // Radiant Pink
            Color(0xFFFF3B30), // Loop closure
        )
    }

    // Obsidian Sanctuary chromatic palette for private browsing
    val privateRgbColors = remember {
        listOf(
            Color(0xFFF59E0B), // Radiant Amber
            Color(0xFFFBBF24), // Warm Honey Gold
            Color(0xFF8B5CF6), // Velvet Violet
            Color(0xFFA855F7), // Neon Orchid
            Color(0xFF6366F1), // Royal Indigo
            Color(0xFFEC4899), // Deep Rose
            Color(0xFFF59E0B), // Loop closure
        )
    }

    val activeColors = if (isPrivate) privateRgbColors else normalRgbColors
    val sweepBrush = remember(sweepAngle, isPrivate) {
        ClarusSweepGradientBrush(
            colors = activeColors,
            rotationAngle = sweepAngle,
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        // Layer 0: Ambient multi-color loading aura glowing THROUGH and BEHIND the glass surface
        if (auraAlpha > 0.005f) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        alpha = auraAlpha * auraPulse * 0.45f
                    }
                    .clip(ClarusGlassTokens.Shapes.Card)
                    .background(sweepBrush),
            )
        }

        // Layer 1: Clarus tactile frosted glass surface with perimeter-tracing RGB loading strip
        ClarusGlassSurface(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (auraAlpha > 0.005f) {
                        Modifier.drawWithContent {
                            drawContent()
                            // Perimeter-tracing RGB loading strip along the URL bar card boundary
                            val strokeWidthPx = 2.dp.toPx()
                            val halfStroke = strokeWidthPx / 2f
                            drawRoundRect(
                                brush = sweepBrush,
                                topLeft = Offset(halfStroke, halfStroke),
                                size = Size(size.width - strokeWidthPx, size.height - strokeWidthPx),
                                cornerRadius = CornerRadius(20.dp.toPx(), 20.dp.toPx()),
                                style = Stroke(width = strokeWidthPx),
                                alpha = auraAlpha,
                            )
                        }
                    } else {
                        Modifier
                    },
                ),
            shape = ClarusGlassTokens.Shapes.Card,
            elevation = ClarusGlassTokens.Elevation.Level2,
            isDarkTheme = isDarkTheme,
            isPrivate = isPrivate,
            isActive = isLoading,
        ) {
            if (customColor != null) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(customColor.copy(alpha = 0.85f)),
                )
            }
            content()
        }
    }
}

