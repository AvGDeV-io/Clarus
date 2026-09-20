/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.gestures.zen

import android.util.Log
import androidx.navigation.NavController
import mozilla.components.browser.state.selector.getNormalOrPrivateTabs
import mozilla.components.browser.state.selector.selectedTab
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.feature.session.SessionUseCases
import mozilla.components.feature.tabs.TabsUseCases
import org.mozilla.fenix.NavGraphDirections
import org.mozilla.fenix.components.usecases.FenixBrowserUseCases
import org.mozilla.fenix.ext.nav

import org.mozilla.fenix.tabstray.redux.state.Page

/**
 * Factory helper that creates a [ZenGestureActions] implementation bound to existing
 * Fenix / Mozilla Components navigation mechanisms.
 *
 * This connects detected Zen gestures to existing use cases and dispatchers with zero logic duplication.
 */
object ZenGestureDispatcherHelper {

    /**
     * Creates a [ZenGestureActions] instance backed by existing components.
     *
     * @param browserStore Store providing active tab and tab queue state.
     * @param sessionUseCases Use cases for back/forward navigation within the active session.
     * @param tabsUseCases Use cases for tab selection and switching.
     * @param fenixBrowserUseCases Use cases for creating new homepage tabs.
     * @param navController Controller for navigating to tab management tray and home destinations.
     * @param onFallbackBack Optional fallback back handler (e.g. Activity onBackPressedDispatcher).
     * @param isPrivateMode Optional query callback to determine if current session is private.
     */
    fun create(
        browserStore: BrowserStore,
        sessionUseCases: SessionUseCases,
        tabsUseCases: TabsUseCases,
        fenixBrowserUseCases: FenixBrowserUseCases,
        navController: NavController,
        onFallbackBack: (() -> Unit)? = null,
        isPrivateMode: (() -> Boolean)? = null,
    ): ZenGestureActions {
        return object : ZenGestureActions {
            override fun onNavigateBack() {
                val currentTab = browserStore.state.selectedTab
                Log.d(
                    "ZenGesture",
                    "3. [BEFORE DISPATCH] onNavigateBack called: tabId=${currentTab?.id}, url=${currentTab?.content?.url}, canGoBack=${currentTab?.content?.canGoBack}, hasFallback=${onFallbackBack != null}",
                )
                if (currentTab != null && currentTab.content.canGoBack) {
                    Log.d("ZenGesture", "Dispatching sessionUseCases.goBack(tabId=${currentTab.id})")
                    sessionUseCases.goBack(currentTab.id)
                } else if (onFallbackBack != null) {
                    Log.d("ZenGesture", "Dispatching onFallbackBack.invoke()")
                    onFallbackBack.invoke()
                } else {
                    Log.d("ZenGesture", "Dispatching default sessionUseCases.goBack()")
                    sessionUseCases.goBack()
                }
                Log.d("ZenGesture", "3. [AFTER DISPATCH] onNavigateBack dispatch call completed")
            }

            override fun onNavigateForward() {
                val currentTab = browserStore.state.selectedTab
                Log.d(
                    "ZenGesture",
                    "3. [BEFORE DISPATCH] onNavigateForward called: tabId=${currentTab?.id}, url=${currentTab?.content?.url}, canGoForward=${currentTab?.content?.canGoForward}",
                )
                if (currentTab != null && currentTab.content.canGoForward) {
                    Log.d("ZenGesture", "Dispatching sessionUseCases.goForward(tabId=${currentTab.id})")
                    sessionUseCases.goForward(currentTab.id)
                } else {
                    Log.d("ZenGesture", "Dispatching default sessionUseCases.goForward()")
                    sessionUseCases.goForward()
                }
                Log.d("ZenGesture", "3. [AFTER DISPATCH] onNavigateForward dispatch call completed")
            }

            override fun onOpenTabOverview() {
                val currentDestinationId = navController.currentDestination?.id
                val isPrivate = isPrivateMode?.invoke() ?: (browserStore.state.selectedTab?.content?.private == true)
                val targetPage = if (isPrivate) Page.PrivateTabs else Page.NormalTabs
                navController.nav(
                    currentDestinationId,
                    NavGraphDirections.actionGlobalTabManagementFragment(page = targetPage),
                )
            }

            override fun onOpenNewTab() {
                val isPrivate = browserStore.state.selectedTab?.content?.private ?: false
                fenixBrowserUseCases.addNewHomepageTab(private = isPrivate)
            }

            override fun onSwitchToPreviousTab() {
                switchTabOffset(-1)
            }

            override fun onSwitchToNextTab() {
                switchTabOffset(1)
            }

            private fun switchTabOffset(offset: Int) {
                val currentTab = browserStore.state.selectedTab ?: return
                val tabs = browserStore.state.getNormalOrPrivateTabs(currentTab.content.private)
                if (tabs.isEmpty()) return

                val currentIndex = tabs.indexOfFirst { it.id == currentTab.id }
                if (currentIndex == -1) return

                val targetIndex = (currentIndex + offset).coerceIn(0, tabs.lastIndex)
                if (targetIndex != currentIndex) {
                    val targetTab = tabs[targetIndex]
                    tabsUseCases.selectTab(targetTab.id)
                }
            }
        }
    }
}
