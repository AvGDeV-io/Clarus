/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.home.toolbar

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import mozilla.components.compose.browser.toolbar.NavigationBar
import mozilla.components.compose.browser.toolbar.store.BrowserToolbarStore
import mozilla.components.compose.browser.toolbar.store.ToolbarGravity.Bottom
import mozilla.components.compose.browser.toolbar.store.ToolbarGravity.Top
import mozilla.components.support.utils.KeyboardState
import mozilla.components.support.utils.keyboardAsState
import org.mozilla.fenix.browser.browsingmode.BrowsingModeManager
import org.mozilla.fenix.theme.FirefoxTheme
import org.mozilla.fenix.theme.glass.ClarusGlassSurface
import org.mozilla.fenix.theme.glass.ClarusGlassTokens
import org.mozilla.fenix.utils.Settings
import org.mozilla.fenix.wallpapers.WallpaperTheme

/**
 * A wrapper over the [NavigationBar] composable that provides enhanced customization and
 * lifecycle-aware integration for use within the [FenixHomeToolbar] framework.
 *
 * Renders inside [ClarusGlassSurface] so the home navigation dock matches the browser toolbar's
 * floating Clarus glass treatment.
 *
 * @param toolbarStore [BrowserToolbarStore] containing the navigation bar state.
 * @param browsingModeManager [BrowsingModeManager] used to determine the current browsing mode.
 * @param settings [Settings] object to get the toolbar position and other settings.
 * @param hideWhenKeyboardShown If true, navigation bar will be hidden when the keyboard is visible.
 */
class HomeNavigationBar(
    private val toolbarStore: BrowserToolbarStore,
    private val browsingModeManager: BrowsingModeManager,
    private val settings: Settings,
    private val hideWhenKeyboardShown: Boolean,
) : FenixHomeToolbar {

    @Composable
    private fun DefaultNavigationBarContent() {
        val uiState by toolbarStore.stateFlow.collectAsState()
        val toolbarGravity = remember(settings) {
            when (settings.shouldUseBottomToolbar) {
                true -> Bottom
                false -> Top
            }
        }
        val isKeyboardVisible = if (hideWhenKeyboardShown) {
            val keyboardState by keyboardAsState()
            keyboardState == KeyboardState.Opened
        } else {
            false
        }

        val isPrivateMode = browsingModeManager.mode.isPrivate

        if (uiState.displayState.navigationActions.isNotEmpty() && !isKeyboardVisible) {
            FirefoxTheme {
                val isDark = isSystemInDarkTheme() || isPrivateMode
                val useWallpaperTint =
                    settings.enableUniversalEdgeToEdgeWallpapers && !isPrivateMode
                val glassChromeColors = MaterialTheme.colorScheme.copy(
                    surface = Color.Transparent,
                    surfaceContainerHighest = Color.Transparent,
                    onSurface = if (useWallpaperTint) {
                        WallpaperTheme.onWallpaper
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )

                ClarusGlassSurface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 3.dp),
                    shape = ClarusGlassTokens.Shapes.Container,
                    elevation = ClarusGlassTokens.Elevation.Level2,
                    isDarkTheme = isDark,
                    isPrivate = isPrivateMode,
                ) {
                    MaterialTheme(colorScheme = glassChromeColors) {
                        NavigationBar(
                            actions = uiState.displayState.navigationActions,
                            toolbarGravity = toolbarGravity,
                            onInteraction = { toolbarStore.dispatch(it) },
                        )
                    }
                }
            }
        }
    }

    @Composable
    override fun Content() {
        DefaultNavigationBarContent()
    }

    override fun updateAddressBarVisibility(isVisible: Boolean) {
        // no-op
    }

    override fun build(middleSearchEnabled: Boolean) {
        // no-op
    }
}
